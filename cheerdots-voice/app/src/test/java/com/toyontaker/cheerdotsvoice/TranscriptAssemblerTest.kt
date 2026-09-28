package com.toyontaker.cheerdotsvoice

import com.toyontaker.cheerdotsvoice.speech.TranscriptAssembler
import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptAssemblerTest {

    @Test
    fun keepsGrowingPartial() {
        val t = TranscriptAssembler()
        listOf("明日", "明日の", "明日の天気", "明日の天気を教えて").forEach(t::onPartial)
        assertEquals("明日の天気を教えて", t.text)
    }

    @Test
    fun revisionsReplaceRatherThanAppend() {
        val t = TranscriptAssembler()
        listOf("今日の", "今日の天気", "明日の天気", "明日の天気を教えて").forEach(t::onPartial)
        assertEquals("明日の天気を教えて", t.text)
    }

    @Test
    fun keepsEarlierSegmentWhenPartialsStartOver() {
        val t = TranscriptAssembler()
        // Observed on device: partials restart after a pause and only the tail survived.
        listOf("明日", "明日の", "天気", "天気を教えて").forEach(t::onPartial)
        assertEquals("明日の天気を教えて", t.text)
    }

    @Test
    fun textInsertedAtTheFrontIsARevision() {
        val t = TranscriptAssembler()
        listOf("天気", "明日の天気").forEach(t::onPartial)
        assertEquals("明日の天気", t.text)
    }

    @Test
    fun emptyFinalKeepsCollectedText() {
        val t = TranscriptAssembler()
        listOf("明日の", "天気を教えて").forEach(t::onPartial)
        t.onFinal("")
        assertEquals("明日の天気を教えて", t.text)
    }

    @Test
    fun finalReplacesCurrentSegment() {
        val t = TranscriptAssembler()
        listOf("明日の", "天気").forEach(t::onPartial)
        t.onFinal("天気を教えて")
        assertEquals("明日の天気を教えて", t.text)
    }

    @Test
    fun finalCoveringWholeUtteranceIsNotDuplicated() {
        val t = TranscriptAssembler()
        listOf("明日の", "天気").forEach(t::onPartial)
        t.onFinal("明日の天気を教えて")
        assertEquals("明日の天気を教えて", t.text)
    }

    @Test
    fun segmentedResults() {
        val t = TranscriptAssembler()
        t.onPartial("明日の")
        t.onSegment("明日の")
        t.onPartial("天気を")
        t.onSegment("天気を教えて")
        assertEquals("明日の天気を教えて", t.text)
    }
}
