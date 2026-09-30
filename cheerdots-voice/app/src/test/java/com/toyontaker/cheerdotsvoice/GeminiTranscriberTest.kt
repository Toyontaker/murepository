package com.toyontaker.cheerdotsvoice

import com.toyontaker.cheerdotsvoice.speech.GeminiApi
import com.toyontaker.cheerdotsvoice.speech.GeminiTranscriber
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GeminiTranscriberTest {

    private val audio = JSONObject().put("type", "audio").put("data", "AAAA").put("mime_type", "audio/wav")

    @Test
    fun buildsSmartRequest() {
        val req = GeminiTranscriber.buildRequest(audio, "ja-JP", smart = true)
        assertEquals("gemini-3.5-transcribe", req.getString("model"))
        assertEquals("audio", req.getJSONArray("input").getJSONObject(0).getString("type"))
        val config = req.getJSONObject("generation_config").getJSONObject("transcription_config")
        assertEquals("ja-JP", config.getJSONArray("language_codes").getString(0))
        assertEquals("smart", config.getString("mode"))
    }

    @Test
    fun buildsVerbatimRequest() {
        val req = GeminiTranscriber.buildRequest(audio, "ja-JP", smart = false)
        val config = req.getJSONObject("generation_config").getJSONObject("transcription_config")
        assertEquals("verbatim", config.getJSONObject("mode").getString("type"))
    }

    @Test
    fun parsesOutputText() {
        val response = JSONObject("""{"id":"interactions/abc","status":"completed","output_text":"明日の天気を教えて"}""")
        assertEquals("明日の天気を教えて", GeminiApi.outputText(response))
    }

    @Test
    fun parsesStepsWhenOutputTextIsMissing() {
        val response = JSONObject(
            """{"status":"completed","steps":[
                {"type":"user_input","content":[{"type":"audio"}]},
                {"type":"model_output","content":[{"type":"text","text":"明日の"},{"type":"text","text":"天気"}]}
            ]}"""
        )
        assertEquals("明日の天気", GeminiApi.outputText(response))
    }

    @Test
    fun noTextWhileInProgress() {
        assertNull(GeminiApi.outputText(JSONObject("""{"id":"interactions/abc","status":"in_progress"}""")))
    }

    @Test
    fun wrapsPcmInWav() {
        val pcm = byteArrayOf(1, 2, 3, 4)
        val wav = GeminiTranscriber.wav(pcm, 16000)
        assertEquals(48, wav.size)
        val b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals(40, b.getInt(4))
        assertEquals("WAVE", String(wav, 8, 4))
        assertEquals(1.toShort(), b.getShort(20)) // PCM
        assertEquals(1.toShort(), b.getShort(22)) // mono
        assertEquals(16000, b.getInt(24))
        assertEquals(32000, b.getInt(28))
        assertEquals(16.toShort(), b.getShort(34))
        assertEquals("data", String(wav, 36, 4))
        assertEquals(4, b.getInt(40))
        assertArrayEquals(pcm, wav.copyOfRange(44, 48))
    }
}
