package com.toyontaker.cheerdotsvoice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.toyontaker.cheerdotsvoice.audio.SbcDecoder
import com.toyontaker.cheerdotsvoice.ble.CheerdotsClient
import com.toyontaker.cheerdotsvoice.protocol.CheerdotsProtocol
import com.toyontaker.cheerdotsvoice.speech.GeminiTranscriber
import com.toyontaker.cheerdotsvoice.speech.StreamingRecognizer
import com.toyontaker.cheerdotsvoice.speech.TextRefiner
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

/**
 * Turns Cheerdots voice-key presses into speech sessions.
 *
 * A session starts on the voice-key-down event or on the first audio packet,
 * and ends on the key-up event or when audio stops arriving. Depending on
 * [mode] and the selected engine, the decoded audio is streamed to Android's
 * speech recognizer, or buffered and sent to Gemini when the key is released,
 * or just buffered (for the recording test). Final transcripts are optionally
 * cleaned up by an LLM ([TextRefiner]). Callbacks run on the main thread.
 */
class VoiceController(private val context: Context, private val listener: Listener) {

    interface Listener {
        fun onConnectionChanged(state: CheerdotsClient.State) {}
        fun onSessionStarted() {}

        /** Waiting for a remote step: transcription (Gemini) or LLM clean-up. */
        fun onProcessing(stage: Stage) {}
        fun onPartial(text: String) {}
        fun onFinal(text: String) {}
        fun onRecorded(pcm: ByteArray, sampleRate: Int) {}
        fun onError(message: String) {}
        fun onLog(message: String) {}
    }

    enum class Mode { RECOGNIZE, RECORD }

    enum class Stage { TRANSCRIBING, REFINING }

    /** Supplies where the text will be inserted (the keyboard's input field), for LLM context. */
    var inputContextProvider: (() -> TextRefiner.InputContext?)? = null

    var mode = Mode.RECOGNIZE

    private val main = Handler(Looper.getMainLooper())
    private val decoder = SbcDecoder()
    private val settings = Settings(context)
    private var sessionActive = false
    private var packetCount = 0
    private var decodeErrors = 0
    private var sessionStartedAt = 0L
    private var firstPacketAt = 0L
    private var lastPacketAt = 0L
    private var peak = 0
    private var sumSquares = 0.0
    private var sampleCount = 0L
    private val recording = ByteArrayOutputStream()
    private var sessionEngine = Settings.Engine.ANDROID
    private val gemini = GeminiTranscriber()
    private val refiner = TextRefiner()
    private val worker = Executors.newSingleThreadExecutor()

    /** Incremented per remote request; results of superseded requests are dropped. */
    private var requestSeq = 0

    private val recognizer = StreamingRecognizer(context, object : StreamingRecognizer.Listener {
        override fun onPartial(text: String) = listener.onPartial(text)
        override fun onFinal(text: String) = deliverFinal(text)
        override fun onError(message: String) = listener.onError(message)
        override fun onLog(message: String) = listener.onLog(message)
    })

    private val client = CheerdotsClient(context, object : CheerdotsClient.Listener {
        override fun onStateChanged(state: CheerdotsClient.State) {
            if (state != CheerdotsClient.State.READY) endSession()
            listener.onConnectionChanged(state)
        }

        override fun onEvent(event: CheerdotsProtocol.Event) {
            when (event) {
                is CheerdotsProtocol.Event.KeyEvent -> {
                    listener.onLog("key 0x%02x".format(event.code))
                    when (event.code) {
                        CheerdotsProtocol.Key.VOICE_INPUT_DOWN -> startSession()
                        CheerdotsProtocol.Key.VOICE_INPUT_UP -> endSession()
                    }
                }
                is CheerdotsProtocol.Event.Battery -> listener.onLog("battery ${event.percent}%")
                is CheerdotsProtocol.Event.Status -> listener.onLog("status ${event.raw.toHex()}")
                is CheerdotsProtocol.Event.Unknown -> listener.onLog("event ${event.raw.toHex()}")
            }
        }

        override fun onAudioPacket(packet: ByteArray) {
            main.post { handleAudio(packet) }
        }

        override fun onLog(message: String) = listener.onLog(message)
    })

    val connectionState get() = client.state

    fun connect(): Boolean {
        val address = settings.deviceAddress ?: return false
        client.connect(address)
        return true
    }

    fun disconnect() {
        endSession()
        client.close()
    }

    fun release() {
        disconnect()
        recognizer.shutdown()
        requestSeq++
        worker.shutdown()
    }

    /** Buffer the session's PCM instead of streaming it. */
    private val buffering get() = mode == Mode.RECORD || sessionEngine == Settings.Engine.GEMINI

    private fun startSession() {
        if (sessionActive) return
        sessionActive = true
        packetCount = 0
        decodeErrors = 0
        sessionStartedAt = SystemClock.elapsedRealtime()
        firstPacketAt = 0L
        peak = 0
        sumSquares = 0.0
        sampleCount = 0L
        decoder.reset()
        recording.reset()
        sessionEngine = settings.engine
        if (mode == Mode.RECOGNIZE && sessionEngine == Settings.Engine.ANDROID) {
            recognizer.start(settings.language, CheerdotsProtocol.AUDIO_SAMPLE_RATE, settings.preferOffline)
        }
        listener.onSessionStarted()
        armSilenceTimeout()
    }

    private fun endSession() {
        main.removeCallbacks(silenceTimeout)
        if (!sessionActive) return
        sessionActive = false
        // One packet carries 5 ms of audio; fewer packets than the elapsed time means drops.
        val expected = if (firstPacketAt == 0L) 0 else (lastPacketAt - firstPacketAt) / PACKET_MS + 1
        val startDelay = if (firstPacketAt == 0L) -1 else firstPacketAt - sessionStartedAt
        listener.onLog(
            "session: $packetCount/$expected packets, first audio after ${startDelay}ms, $decodeErrors decode errors"
        )
        if (sampleCount > 0) {
            val rms = Math.sqrt(sumSquares / sampleCount)
            listener.onLog("level: peak %.0f dBFS, rms %.0f dBFS".format(dbfs(peak.toDouble()), dbfs(rms)))
        }
        when {
            mode == Mode.RECORD -> listener.onRecorded(recording.toByteArray(), CheerdotsProtocol.AUDIO_SAMPLE_RATE)
            sessionEngine == Settings.Engine.GEMINI -> transcribeWithGemini(recording.toByteArray())
            else -> recognizer.finish()
        }
    }

    private fun transcribeWithGemini(pcm: ByteArray) {
        val rate = CheerdotsProtocol.AUDIO_SAMPLE_RATE
        if (pcm.size < rate * 2 * MIN_GEMINI_MS / 1000) {
            listener.onLog("gemini: too short, skipped")
            listener.onFinal("")
            return
        }
        val request = ++requestSeq
        val language = settings.language
        val smart = settings.geminiSmart
        val apiKey = settings.geminiApiKey
        listener.onProcessing(Stage.TRANSCRIBING)
        val startedAt = SystemClock.elapsedRealtime()
        worker.execute {
            val result = runCatching { gemini.transcribe(pcm, rate, language, smart, apiKey) }
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            main.post {
                if (request != requestSeq) return@post
                result.fold(
                    onSuccess = {
                        listener.onLog("gemini: ${elapsed}ms \"$it\"")
                        deliverFinal(it)
                    },
                    onFailure = {
                        listener.onLog("gemini error: ${it.message}")
                        listener.onError(it.message ?: "Gemini エラー")
                    },
                )
            }
        }
    }

    /** Fixes typos in existing text with the LLM; [onResult] runs on the main thread. */
    fun proofread(text: String, onResult: (Result<String>) -> Unit) {
        val prompt = settings.proofreadPrompt
        val model = settings.refineModel
        val notes = settings.userNotes
        val apiKey = settings.geminiApiKey
        val startedAt = SystemClock.elapsedRealtime()
        worker.execute {
            val result = runCatching { refiner.proofread(text, prompt, model, notes, apiKey) }
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            main.post {
                listener.onLog("proofread: ${elapsed}ms ${result.exceptionOrNull()?.message ?: "ok"}")
                onResult(result)
            }
        }
    }

    /** Hands a final transcript to the listener, after LLM clean-up when enabled. */
    private fun deliverFinal(raw: String) {
        if (!settings.refineEnabled || raw.isBlank()) {
            listener.onFinal(raw)
            return
        }
        val field = runCatching { inputContextProvider?.invoke() }.getOrNull()
        val isPrivate = field?.private == true
        val refineRequest = TextRefiner.Request(
            transcript = raw,
            systemPrompt = settings.refinePrompt,
            model = settings.refineModel,
            context = field.takeIf { settings.refineUseFieldContext && !isPrivate },
            history = settings.history(),
            userNotes = settings.userNotes,
        )
        val apiKey = settings.geminiApiKey
        val request = ++requestSeq
        listener.onProcessing(Stage.REFINING)
        val startedAt = SystemClock.elapsedRealtime()
        worker.execute {
            val result = runCatching { refiner.refine(refineRequest, apiKey) }
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            main.post {
                if (request != requestSeq) return@post
                result.fold(
                    onSuccess = {
                        listener.onLog("refine: ${elapsed}ms \"$raw\" -> \"$it\"")
                        if (!isPrivate) settings.addHistory(it)
                        listener.onFinal(it)
                    },
                    onFailure = {
                        // Fall back to the unrefined transcript rather than losing the dictation.
                        listener.onLog("refine error (raw text used): ${it.message}")
                        listener.onFinal(raw)
                    },
                )
            }
        }
    }

    private fun handleAudio(packet: ByteArray) {
        if (!sessionActive) startSession()
        armSilenceTimeout()
        packetCount++
        lastPacketAt = SystemClock.elapsedRealtime()
        if (firstPacketAt == 0L) firstPacketAt = lastPacketAt
        val pcm = try {
            val frame = CheerdotsProtocol.audioPacketToSbcFrame(packet)
            decoder.decode(frame, blocksOverride = CheerdotsProtocol.AUDIO_SBC_BLOCKS).pcm
        } catch (e: SbcDecoder.DecodeException) {
            decodeErrors++
            if (decodeErrors <= 3) listener.onLog("decode error: ${e.message} ${packet.toHex()}")
            return
        }
        for (s in pcm) {
            val v = s.toInt()
            if (Math.abs(v) > peak) peak = Math.abs(v)
            sumSquares += v.toDouble() * v
        }
        sampleCount += pcm.size
        if (buffering) {
            for (s in pcm) {
                recording.write(s.toInt())
                recording.write(s.toInt() shr 8)
            }
        } else {
            recognizer.write(pcm)
        }
    }

    private val silenceTimeout = Runnable {
        listener.onLog("audio stopped")
        endSession()
    }

    private fun armSilenceTimeout() {
        main.removeCallbacks(silenceTimeout)
        main.postDelayed(silenceTimeout, SILENCE_TIMEOUT_MS)
    }

    companion object {
        /** Audio arrives every 5 ms while the key is held. */
        private const val SILENCE_TIMEOUT_MS = 600L
        private const val PACKET_MS = 5L
        private const val MIN_GEMINI_MS = 300
    }
}

private fun dbfs(amplitude: Double): Double = 20 * Math.log10(maxOf(amplitude, 1.0) / 32768.0)

fun ByteArray.toHex(): String = joinToString(" ") { "%02x".format(it) }
