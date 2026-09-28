package com.toyontaker.cheerdotsvoice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.toyontaker.cheerdotsvoice.audio.SbcDecoder
import com.toyontaker.cheerdotsvoice.ble.CheerdotsClient
import com.toyontaker.cheerdotsvoice.protocol.CheerdotsProtocol
import com.toyontaker.cheerdotsvoice.speech.StreamingRecognizer
import java.io.ByteArrayOutputStream

/**
 * Turns Cheerdots voice-key presses into speech sessions.
 *
 * A session starts on the voice-key-down event or on the first audio packet,
 * and ends on the key-up event or when audio stops arriving. Depending on
 * [mode], the decoded audio goes to the speech recognizer or into a PCM buffer
 * (for the recording test). Everything runs on the main thread.
 */
class VoiceController(private val context: Context, private val listener: Listener) {

    interface Listener {
        fun onConnectionChanged(state: CheerdotsClient.State) {}
        fun onSessionStarted() {}
        fun onPartial(text: String) {}
        fun onFinal(text: String) {}
        fun onRecorded(pcm: ByteArray, sampleRate: Int) {}
        fun onError(message: String) {}
        fun onLog(message: String) {}
    }

    enum class Mode { RECOGNIZE, RECORD }

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
    private val recording = ByteArrayOutputStream()

    private val recognizer = StreamingRecognizer(context, object : StreamingRecognizer.Listener {
        override fun onPartial(text: String) = listener.onPartial(text)
        override fun onFinal(text: String) = listener.onFinal(text)
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
    }

    private fun startSession() {
        if (sessionActive) return
        sessionActive = true
        packetCount = 0
        decodeErrors = 0
        sessionStartedAt = SystemClock.elapsedRealtime()
        firstPacketAt = 0L
        decoder.reset()
        recording.reset()
        if (mode == Mode.RECOGNIZE) {
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
        when (mode) {
            Mode.RECOGNIZE -> recognizer.finish()
            Mode.RECORD -> listener.onRecorded(recording.toByteArray(), CheerdotsProtocol.AUDIO_SAMPLE_RATE)
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
        when (mode) {
            Mode.RECOGNIZE -> recognizer.write(pcm)
            Mode.RECORD -> for (s in pcm) {
                recording.write(s.toInt())
                recording.write(s.toInt() shr 8)
            }
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
    }
}

fun ByteArray.toHex(): String = joinToString(" ") { "%02x".format(it) }
