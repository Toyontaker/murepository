package com.toyontaker.cheerdotsvoice.speech

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
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
    }

    private var recognizer: SpeechRecognizer? = null
    private var output: OutputStream? = null
    private val writer = Executors.newSingleThreadExecutor()

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
        val pipe = ParcelFileDescriptor.createPipe()
        val out = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
        output = out

        val sr = if (preferOffline && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        recognizer = sr
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onPartialResults(partialResults: Bundle) {
                best(partialResults)?.let { listener.onPartial(it) }
            }

            override fun onResults(results: Bundle) {
                val text = best(results).orEmpty()
                release()
                listener.onFinal(text)
            }

            override fun onError(error: Int) {
                release()
                listener.onError(describeError(error))
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
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0])
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, sampleRate)
            // The key release decides when speech ends, not silence detection.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 10_000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 10_000L)
        }
        sr.startListening(intent)
        // The recognizer holds its own copy of the read end.
        pipe[0].close()
    }

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

    /** Ends the audio stream; the final result arrives via [Listener.onFinal]. */
    fun finish() {
        val out = output ?: return
        output = null
        writer.execute {
            try {
                out.close()
            } catch (_: IOException) {
            }
        }
    }

    fun cancel() {
        finish()
        recognizer?.let {
            it.cancel()
            it.destroy()
        }
        recognizer = null
    }

    fun shutdown() {
        cancel()
        writer.shutdown()
    }

    private fun release() {
        finish()
        recognizer?.destroy()
        recognizer = null
    }

    private fun best(bundle: Bundle): String? =
        bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private fun describeError(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH -> "聞き取れませんでした"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "音声がありませんでした"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ネットワークエラー"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "マイク権限がありません（アプリを開いて許可してください）"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "音声認識が使用中です"
        SpeechRecognizer.ERROR_AUDIO -> "音声入力エラー"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "この言語は未対応です"
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "言語データがありません"
        else -> "音声認識エラー ($error)"
    }
}
