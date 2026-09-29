package com.toyontaker.cheerdotsvoice.speech

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
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

    class TranscribeException(message: String) : Exception(message)

    @Volatile
    private var inlineRejected = false

    fun transcribe(pcm: ByteArray, sampleRate: Int, language: String, smart: Boolean, apiKey: String): String {
        if (apiKey.isBlank()) throw TranscribeException("Gemini APIキーが未設定です")
        val wav = wav(pcm, sampleRate)

        if (!inlineRejected) {
            val inline = JSONObject()
                .put("type", "audio")
                .put("data", Base64.encodeToString(wav, Base64.NO_WRAP))
                .put("mime_type", WAV_MIME)
            val response = post("$API/interactions", apiKey, buildRequest(inline, language, smart))
            if (response.code in 200..299) return finish(JSONObject(response.body), apiKey)
            if (response.code != 400) throw apiError(response)
            // Inline audio rejected: fall back to the Files API.
            inlineRejected = true
        }

        val file = upload(wav, apiKey)
        try {
            val ref = JSONObject()
                .put("type", "audio")
                .put("uri", file.getString("uri"))
                .put("mime_type", WAV_MIME)
            val response = post("$API/interactions", apiKey, buildRequest(ref, language, smart))
            if (response.code !in 200..299) throw apiError(response)
            return finish(JSONObject(response.body), apiKey)
        } finally {
            runCatching { request("DELETE", "$API/${file.getString("name")}", apiKey, null, emptyMap()) }
        }
    }

    /** Returns the transcript, polling while the interaction is still running. */
    private fun finish(first: JSONObject, apiKey: String): String {
        var interaction = first
        val deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS
        while (true) {
            parseTranscript(interaction)?.let { return it }
            when (val status = interaction.optString("status")) {
                "", "completed" -> return ""
                "failed", "cancelled", "incomplete" -> throw TranscribeException("文字起こしに失敗しました ($status)")
            }
            if (System.currentTimeMillis() > deadline) throw TranscribeException("文字起こしがタイムアウトしました")
            Thread.sleep(POLL_INTERVAL_MS)
            val id = interaction.optString("id")
            val path = if (id.startsWith("interactions/")) id else "interactions/$id"
            val response = request("GET", "$API/$path", apiKey, null, emptyMap())
            if (response.code !in 200..299) throw apiError(response)
            interaction = JSONObject(response.body)
        }
    }

    /** Resumable upload to the Files API; returns the file resource (name, uri). */
    private fun upload(bytes: ByteArray, apiKey: String): JSONObject {
        val start = request(
            "POST", "$UPLOAD_API/files", apiKey,
            JSONObject().put("file", JSONObject().put("display_name", "cheerdots-voice")).toString().toByteArray(),
            mapOf(
                "X-Goog-Upload-Protocol" to "resumable",
                "X-Goog-Upload-Command" to "start",
                "X-Goog-Upload-Header-Content-Length" to bytes.size.toString(),
                "X-Goog-Upload-Header-Content-Type" to WAV_MIME,
                "Content-Type" to "application/json",
            ),
        )
        if (start.code !in 200..299) throw apiError(start)
        val uploadUrl = start.headers["x-goog-upload-url"] ?: throw TranscribeException("アップロードURLを取得できません")
        val done = request(
            "POST", uploadUrl, apiKey, bytes,
            mapOf("X-Goog-Upload-Offset" to "0", "X-Goog-Upload-Command" to "upload, finalize"),
        )
        if (done.code !in 200..299) throw apiError(done)
        return JSONObject(done.body).getJSONObject("file")
    }

    private class Response(val code: Int, val body: String, val headers: Map<String, String>)

    private fun post(url: String, apiKey: String, json: JSONObject) =
        request("POST", url, apiKey, json.toString().toByteArray(), mapOf("Content-Type" to "application/json"))

    private fun request(method: String, url: String, apiKey: String, body: ByteArray?, headers: Map<String, String>): Response {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.setRequestProperty("x-goog-api-key", apiKey)
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.setFixedLengthStreamingMode(body.size)
                conn.outputStream.use { it.write(body) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            val responseHeaders = conn.headerFields.entries
                .filter { it.key != null }
                .associate { it.key.lowercase() to it.value.joinToString(",") }
            return Response(code, text, responseHeaders)
        } catch (e: IOException) {
            throw TranscribeException("通信エラー: ${e.message}")
        } finally {
            conn.disconnect()
        }
    }

    private fun apiError(response: Response): TranscribeException {
        val message = runCatching { JSONObject(response.body).getJSONObject("error").getString("message") }
            .getOrDefault(response.body.take(200))
        return TranscribeException("Gemini API エラー ${response.code}: $message")
    }

    companion object {
        private const val API = "https://generativelanguage.googleapis.com/v1beta"
        private const val UPLOAD_API = "https://generativelanguage.googleapis.com/upload/v1beta"
        const val MODEL = "gemini-3.5-transcribe"
        private const val WAV_MIME = "audio/wav"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val POLL_INTERVAL_MS = 500L
        private const val POLL_TIMEOUT_MS = 30_000L

        fun buildRequest(audio: JSONObject, language: String, smart: Boolean): JSONObject {
            val transcription = JSONObject()
                .put("language_codes", JSONArray().put(language))
                .put("mode", if (smart) "smart" else JSONObject().put("type", "verbatim"))
            return JSONObject()
                .put("model", MODEL)
                .put("input", JSONArray().put(audio))
                .put("generation_config", JSONObject().put("transcription_config", transcription))
        }

        /** The transcript, or null while the interaction has produced no text yet. */
        fun parseTranscript(interaction: JSONObject): String? {
            interaction.optString("output_text").takeIf { it.isNotEmpty() }?.let { return it }
            val steps = interaction.optJSONArray("steps") ?: return null
            val text = StringBuilder()
            for (i in 0 until steps.length()) {
                val step = steps.optJSONObject(i) ?: continue
                if (step.optString("type") != "model_output") continue
                val content = step.optJSONArray("content") ?: continue
                for (j in 0 until content.length()) {
                    val part = content.optJSONObject(j) ?: continue
                    if (part.optString("type") == "text") text.append(part.optString("text"))
                }
            }
            return text.toString().takeIf { it.isNotEmpty() }
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
