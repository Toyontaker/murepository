package com.toyontaker.cheerdotsvoice

import com.toyontaker.cheerdotsvoice.speech.TextRefiner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextRefinerTest {

    private fun request(
        context: TextRefiner.InputContext? = null,
        history: List<String> = emptyList(),
        notes: String = "",
        prompt: String = "",
        model: String = "",
    ) = TextRefiner.Request(
        transcript = "えーと明日の、いや明後日の天気を教えて",
        systemPrompt = prompt,
        model = model,
        context = context,
        history = history,
        userNotes = notes,
    )

    @Test
    fun usesDefaultsWhenBlank() {
        val json = TextRefiner.buildInteraction(request())
        assertEquals(TextRefiner.DEFAULT_MODEL, json.getString("model"))
        assertEquals(TextRefiner.DEFAULT_SYSTEM_PROMPT, json.getString("system_instruction"))
        assertEquals(0.2, json.getJSONObject("generation_config").getDouble("temperature"), 1e-9)
    }

    @Test
    fun usesCustomPromptAndModel() {
        val json = TextRefiner.buildInteraction(request(prompt = "カスタム", model = "gemini-3.8-flash"))
        assertEquals("gemini-3.8-flash", json.getString("model"))
        assertEquals("カスタム", json.getString("system_instruction"))
    }

    @Test
    fun transcriptOnlyWhenNothingElse() {
        assertEquals(
            "<transcript>\nえーと明日の、いや明後日の天気を教えて\n</transcript>",
            TextRefiner.buildInput(request()),
        )
    }

    @Test
    fun includesContextHistoryAndNotes() {
        val input = TextRefiner.buildInput(
            request(
                context = TextRefiner.InputContext("com.example.chat", "メッセージ", "昨日の件ですが、"),
                history = listOf("了解です。", "1行目\n2行目"),
                notes = "語尾はです・ます",
            )
        )
        assertTrue(input.startsWith("<context>\n入力先アプリ: com.example.chat\n入力欄のヒント: メッセージ\n"))
        assertTrue("昨日の件ですが、" in input)
        assertTrue("<user_notes>\n語尾はです・ます\n</user_notes>" in input)
        // Multi-line examples are kept on one line.
        assertTrue("<style_examples>\n- 了解です。\n- 1行目 / 2行目\n</style_examples>" in input)
        assertTrue(input.endsWith("<transcript>\nえーと明日の、いや明後日の天気を教えて\n</transcript>"))
    }

    @Test
    fun emptyContextIsOmitted() {
        val input = TextRefiner.buildInput(request(context = TextRefiner.InputContext(textBeforeCursor = "  ")))
        assertFalse("<context>" in input)
    }

    @Test
    fun stripsWrappingTheModelMayAdd() {
        assertEquals("明後日の天気を教えて", TextRefiner.stripWrapping("「明後日の天気を教えて」"))
        assertEquals("明後日の天気を教えて", TextRefiner.stripWrapping("<transcript>\n明後日の天気を教えて\n</transcript>"))
        // Quotes that are part of the text stay.
        assertEquals("「A」と「B」", TextRefiner.stripWrapping("「A」と「B」"))
    }
}
