package com.toyontaker.cheerdotsvoice.speech

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Minimal Gemini API client (Interactions and Files endpoints). Blocking; use from a background thread. */
class GeminiApi(private val apiKey: String) {

    class ApiException(message: String, val code: Int = 0) : Exception(message)

    class Response(val code: Int, val body: String, val headers: Map<String, String>) {
        val ok get() = code in 200..299
    }

    init {
        if (apiKey.isBlank()) throw ApiException("Gemini APIキーが未設定です")
    }

    /** Creates an interaction and waits until it is no longer running. */
    fun interact(request: JSONObject): JSONObject {
        val response = post("$API/interactions", request)
        if (!response.ok) throw error(response)
        return waitForCompletion(JSONObject(response.body))
    }

    /** Like [interact], but returns the raw response when it is not successful. */
    fun tryInteract(request: JSONObject): Pair<Response, JSONObject?> {
        val response = post("$API/interactions", request)
        return response to if (response.ok) waitForCompletion(JSONObject(response.body)) else null
    }

    private fun waitForCompletion(first: JSONObject): JSONObject {
        var interaction = first
        val deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS
        while (outputText(interaction) == null && interaction.optString("status") !in DONE + FAILED) {
            if (System.currentTimeMillis() > deadline) throw ApiException("Gemini の応答がタイムアウトしました")
            Thread.sleep(POLL_INTERVAL_MS)
            val id = interaction.optString("id")
            val path = if (id.startsWith("interactions/")) id else "interactions/$id"
            val response = request("GET", "$API/$path", null, emptyMap())
            if (!response.ok) throw error(response)
            interaction = JSONObject(response.body)
        }
        val status = interaction.optString("status")
        if (status in FAILED) throw ApiException("Gemini の処理に失敗しました ($status)")
        return interaction
    }

    /** Resumable upload to the Files API; returns the file resource (name, uri). */
    fun upload(bytes: ByteArray, mimeType: String, displayName: String): JSONObject {
        val start = request(
            "POST", "$UPLOAD_API/files",
            JSONObject().put("file", JSONObject().put("display_name", displayName)).toString().toByteArray(),
            mapOf(
                "X-Goog-Upload-Protocol" to "resumable",
                "X-Goog-Upload-Command" to "start",
                "X-Goog-Upload-Header-Content-Length" to bytes.size.toString(),
                "X-Goog-Upload-Header-Content-Type" to mimeType,
                "Content-Type" to "application/json",
            ),
        )
        if (!start.ok) throw error(start)
        val uploadUrl = start.headers["x-goog-upload-url"] ?: throw ApiException("アップロードURLを取得できません")
        val done = request(
            "POST", uploadUrl, bytes,
            mapOf("X-Goog-Upload-Offset" to "0", "X-Goog-Upload-Command" to "upload, finalize"),
        )
        if (!done.ok) throw error(done)
        return JSONObject(done.body).getJSONObject("file")
    }

    fun deleteFile(name: String) {
        runCatching { request("DELETE", "$API/$name", null, emptyMap()) }
    }

    fun error(response: Response): ApiException {
        val message = runCatching { JSONObject(response.body).getJSONObject("error").getString("message") }
            .getOrDefault(response.body.take(200))
        return ApiException("Gemini API エラー ${response.code}: $message", response.code)
    }

    private fun post(url: String, json: JSONObject) =
        request("POST", url, json.toString().toByteArray(), mapOf("Content-Type" to "application/json"))

    private fun request(method: String, url: String, body: ByteArray?, headers: Map<String, String>): Response {
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
            throw ApiException("通信エラー: ${e.message}")
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val API = "https://generativelanguage.googleapis.com/v1beta"
        private const val UPLOAD_API = "https://generativelanguage.googleapis.com/upload/v1beta"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val POLL_INTERVAL_MS = 500L
        private const val POLL_TIMEOUT_MS = 30_000L
        private val DONE = setOf("", "completed")
        private val FAILED = setOf("failed", "cancelled", "incomplete")

        /** The text output of an interaction, or null when it has none. */
        fun outputText(interaction: JSONObject): String? {
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
    }
}
