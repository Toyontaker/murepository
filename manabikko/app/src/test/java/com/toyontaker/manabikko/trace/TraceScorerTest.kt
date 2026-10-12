package com.toyontaker.manabikko.trace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** 画像を つかわない、マス目だけの テスト。 */
class TraceScorerTest {
    private val size = 48
    private val cell = 10f // 480px 四方

    /** たて線(x = 20〜24 マス、y = 6〜42 マス)の おてほん。 */
    private val scorer = TraceScorer(
        BooleanArray(size * size) { i -> (i % size) in 20..24 && (i / size) in 6..42 },
        size, size, cell,
    )

    private fun line(x0: Float, y0: Float, x1: Float, y1: Float) = floatArrayOf(x0, y0, x1, y1)

    @Test fun tracingTheLineIsGreat() {
        val r = scorer.score(listOf(line(225f, 65f, 225f, 425f)))
        assertEquals(Grade.Great, r.grade)
    }

    @Test fun slightlyOffIsStillOk() {
        // 線の はばより すこし ずれていても OK
        val r = scorer.score(listOf(line(240f, 80f, 245f, 410f)))
        assertNotEquals(Grade.Miss, r.grade)
    }

    @Test fun onlyTopThirdIsMiss() {
        val r = scorer.score(listOf(line(225f, 65f, 225f, 180f)))
        assertEquals(Grade.Miss, r.grade)
    }

    @Test fun lineInWrongPlaceIsMiss() {
        val r = scorer.score(listOf(line(40f, 65f, 40f, 425f)))
        assertEquals(Grade.Miss, r.grade)
    }

    @Test fun scribblingEverywhereIsMiss() {
        // 画面じゅう ぐちゃぐちゃ:おてほんは うまるが はみだしが おおい
        val zigzag = (0..20).flatMap { i -> listOf(if (i % 2 == 0) 10f else 470f, 10f + i * 22f) }.toFloatArray()
        val r = scorer.score(listOf(zigzag))
        assertEquals(Grade.Miss, r.grade)
    }

    @Test fun tinyTapIsMiss() {
        val r = scorer.score(listOf(floatArrayOf(225f, 200f)))
        assertEquals(Grade.Miss, r.grade)
    }
}
