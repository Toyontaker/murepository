package com.toyontaker.cheerdotsvoice.speech

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Transcribes a recorded clip with Gemini 3.5 Transcribe (Gemini API, Interactions endpoint).
 *
 * Blocking; call from a background thread. Audio is sent inline as base64 WAV.
 * Inline audio is not documented for this model, so if the API rejects it the
 * clip is uploaded through the Files API and referenced by URI instead (the
 * documented form), and later calls go straight to the upload path.
 */
class GeminiTranscriber {

    @Volatile
    private var inlineRejected = false

    fun transcribe(pcm: ByteArray, sampleRate: Int, language: String, smart: Boolean, apiKey: String): String {
        val api = GeminiApi(apiKey)
        val wav = wav(pcm, sampleRate)

        if (!inlineRejected) {
            val inline = JSONObject()
                .put("type", "audio")
                .put("data", Base64.encodeToString(wav, Base64.NO_WRAP))
                .put("mime_type", WAV_MIME)
            val (response, interaction) = api.tryInteract(buildRequest(inline, language, smart))
            if (interaction != null) return GeminiApi.outputText(interaction).orEmpty()
            if (response.code != 400) throw api.error(response)
            // Inline audio rejected: fall back to the Files API.
            inlineRejected = true
        }

        val file = api.upload(wav, WAV_MIME, "cheerdots-voice")
        try {
            val ref = JSONObject()
                .put("type", "audio")
                .put("uri", file.getString("uri"))
                .put("mime_type", WAV_MIME)
            return GeminiApi.outputText(api.interact(buildRequest(ref, language, smart))).orEmpty()
        } finally {
            api.deleteFile(file.getString("name"))
        }
    }

    companion object {
        const val MODEL = "gemini-3.5-transcribe"
        private const val WAV_MIME = "audio/wav"

        fun buildRequest(audio: JSONObject, language: String, smart: Boolean): JSONObject {
            val transcription = JSONObject()
                .put("language_codes", JSONArray().put(language))
                .put("mode", if (smart) "smart" else JSONObject().put("type", "verbatim"))
            return JSONObject()
                .put("model", MODEL)
                .put("input", JSONArray().put(audio))
                .put("generation_config", JSONObject().put("transcription_config", transcription))
        }

        /** Wraps 16-bit mono little-endian PCM in a WAV container. */
        fun wav(pcm: ByteArray, sampleRate: Int): ByteArray {
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()); putInt(36 + pcm.size); put("WAVE".toByteArray())
                put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
                putInt(sampleRate); putInt(sampleRate * 2); putShort(2); putShort(16)
                put("data".toByteArray()); putInt(pcm.size)
            }
            return header.array() + pcm
        }
    }
}
