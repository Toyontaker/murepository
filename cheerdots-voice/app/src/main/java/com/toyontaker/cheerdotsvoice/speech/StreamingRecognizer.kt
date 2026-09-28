package com.toyontaker.cheerdotsvoice.speech

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.Executors

/**
 * Feeds externally captured 16-bit mono PCM into Android's [SpeechRecognizer]
 * through [RecognizerIntent.EXTRA_AUDIO_SOURCE] (Android 13+), so recognition
 * uses the Cheerdots microphone instead of the phone's.
 *
 * Must be created and driven from the main thread; [write] may be called from any thread.
 */
class StreamingRecognizer(
    private val context: Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onError(message: String)
        fun onLog(message: String) {}
    }

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var output: OutputStream? = null

    /**
     * Read end handed to the recognizer. startListening() is asynchronous (it
     * first binds to the recognition service), so this must stay open until the
     * session is over, or the service receives a closed audio source.
     */
    private var input: ParcelFileDescriptor? = null
    private val writer = Executors.newSingleThreadExecutor()

    private val transcript = TranscriptAssembler()
    private var sampleRate = 16000

    val isActive get() = recognizer != null

    fun start(language: String, sampleRate: Int, preferOffline: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            listener.onError("Android 13 以上が必要です")
            return
        }
        cancel()
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listener.onError("音声認識サービスが見つかりません")
            return
        }
        transcript.reset()
        this.sampleRate = sampleRate
        val pipe = ParcelFileDescriptor.createPipe()
        input = pipe[0]
        output = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])

        val onDevice = preferOffline && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        val sr = if (onDevice) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        recognizer = sr
        listener.onLog("recognizer: ${if (onDevice) "on-device" else "default (${defaultServiceName()})"}")
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onPartialResults(partialResults: Bundle) {
                if (recognizer !== sr) return
                val text = best(partialResults)
                if (!text.isNullOrEmpty()) {
                    transcript.onPartial(text)
                    listener.onPartial(transcript.text)
                }
            }

            override fun onSegmentResults(segmentResults: Bundle) {
                if (recognizer !== sr) return
                val text = best(segmentResults).orEmpty()
                listener.onLog("segment: \"$text\"")
                transcript.onSegment(text)
                listener.onPartial(transcript.text)
            }

            override fun onEndOfSegmentedSession() {
                if (recognizer !== sr) return
                listener.onLog("end of segmented session")
                deliverFinal(transcript.text)
            }

            override fun onResults(results: Bundle) {
                if (recognizer !== sr) return
                val text = best(results).orEmpty()
                listener.onLog("final: \"$text\" (collected: \"${transcript.text}\")")
                transcript.onFinal(text)
                deliverFinal(transcript.text)
            }

            override fun onError(error: Int) {
                if (recognizer !== sr) return
                val collected = transcript.text
                listener.onLog("recognizer error $error (collected: \"$collected\")")
                if (collected.isNotEmpty()) {
                    deliverFinal(collected)
                } else {
                    release()
                    listener.onError(describeError(error))
                }
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0])
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, sampleRate)
            // The key release decides when speech ends, not silence detection.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 10_000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 10_000L)
            // Report each finished segment via onSegmentResults(), where supported.
            putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
        }
        sr.startListening(intent)
        // Recognizers need some silence before speech to detect where it begins.
        writeSilence(PAD_MS)
    }

    private fun writeSilence(ms: Int) = write(ShortArray(sampleRate * ms / 1000))

    /** Appends little-endian 16-bit PCM. */
    fun write(pcm: ShortArray) {
        val out = output ?: return
        val bytes = ByteArray(pcm.size * 2)
        for (i in pcm.indices) {
            val s = pcm[i].toInt()
            bytes[i * 2] = s.toByte()
            bytes[i * 2 + 1] = (s shr 8).toByte()
        }
        writer.execute {
            try {
                out.write(bytes)
            } catch (_: IOException) {
                // Recognizer went away; results or an error will be reported separately.
            }
        }
    }

    /**
     * Ends speech input; the final result arrives via [Listener.onFinal].
     *
     * Uses stopListening() rather than closing the audio stream: closing it
     * makes recognition services treat the session as aborted and return an
     * empty result.
     */
    fun finish() {
        val sr = recognizer ?: return
        // Trailing silence lets the recognizer settle the last words before it stops.
        writeSilence(PAD_MS)
        main.postDelayed({
            if (recognizer !== sr) return@postDelayed
            // A segmented session ends at end-of-stream; others end on stopListening().
            closeOutput()
            sr.stopListening()
        }, PAD_MS.toLong())
        main.removeCallbacks(finalTimeout)
        main.postDelayed(finalTimeout, PAD_MS + FINAL_TIMEOUT_MS)
    }

    fun cancel() {
        recognizer?.cancel()
        release()
    }

    fun shutdown() {
        cancel()
        writer.shutdown()
    }

    private val finalTimeout = Runnable {
        if (recognizer == null) return@Runnable
        listener.onLog("no final result in time (collected: \"${transcript.text}\")")
        deliverFinal(transcript.text)
    }

    private fun deliverFinal(text: String) {
        release()
        listener.onFinal(text)
    }

    private fun closeOutput() {
        output?.let { out ->
            writer.execute {
                try {
                    out.close()
                } catch (_: IOException) {
                }
            }
        }
        output = null
    }

    private fun release() {
        main.removeCallbacks(finalTimeout)
        closeOutput()
        recognizer?.destroy()
        recognizer = null
        try {
            input?.close()
        } catch (_: IOException) {
        }
        input = null
    }

    private fun defaultServiceName(): String? =
        android.provider.Settings.Secure.getString(context.contentResolver, "voice_recognition_service")

    private fun best(bundle: Bundle): String? =
        bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private fun describeError(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH -> "聞き取れませんでした"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "音声がありませんでした"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ネットワークエラー"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "マイク権限がありません（アプリを開いて許可してください）"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "音声認識が使用中です"
        SpeechRecognizer.ERROR_AUDIO -> "音声入力エラー"
        SpeechRecognizer.ERROR_CLIENT -> "音声認識エラー (5: クライアント)"
        SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "音声認識サーバーエラー ($error)"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "この言語は未対応です"
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "言語データがありません"
        else -> "音声認識エラー ($error)"
    }

    private companion object {
        const val FINAL_TIMEOUT_MS = 4000L
        const val PAD_MS = 300
    }
}
