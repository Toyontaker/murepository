package com.toyontaker.manabikko.trace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Random

/** ほんものの 書き順データを つかって 採点の かげんを たしかめる。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class GlyphScoringTest {
    private val width = 1000f
    private val height = 1300f
    private val data = Strokes.parse(java.io.File("src/main/assets/strokes.tsv").readText())

    private fun glyph(char: String) = Glyph(data.getValue(char), width, height)

    /** 書き順の 線の 上を ゆびで なぞった つもりの 線(dx, dy ずらす、wobble で 手ぶれ)。 */
    private fun traceStrokes(g: Glyph, dx: Float = 0f, dy: Float = 0f, wobble: Float = 0f): List<FloatArray> {
        val rnd = Random(3)
        return g.strokes.indices.map { i ->
            val n = 30
            FloatArray(n * 2) { k ->
                val (p, _) = g.posTan(i, g.length(i) * (k / 2) / (n - 1))
                val w = (rnd.nextFloat() * 2 - 1) * wobble
                if (k % 2 == 0) p.x + dx + w else p.y + dy + w
            }
        }
    }

    @Test fun allKanaHaveStrokes() {
        assertEquals(46 + 45, data.size)
        assertEquals(3, data.getValue("あ").size)
        assertEquals(4, data.getValue("き").size)
        assertEquals(1, data.getValue("の").size)
    }

    @Test fun templateIsRendered() {
        val s = glyph("あ").scorer()
        val filled = s.mask.count { it }.toFloat() / s.mask.size
        assertTrue("filled=$filled", filled in 0.03f..0.4f)
    }

    @Test fun exactTraceIsGreatForEveryKana() {
        for (ch in data.keys) {
            val g = glyph(ch)
            assertEquals(ch, Grade.Great, g.scorer().score(traceStrokes(g)).grade)
        }
    }

    @Test fun shakyTraceIsGreat() {
        for (ch in listOf("あ", "き", "ぬ", "ネ")) {
            val g = glyph(ch)
            assertEquals(ch, Grade.Great, g.scorer().score(traceStrokes(g, wobble = width * 0.04f)).grade)
        }
    }

    @Test fun slightlyOffTraceIsOk() {
        // 文字の 3% ずれ は ぜんぶ OK
        for (ch in data.keys) {
            val g = glyph(ch)
            val r = g.scorer().score(traceStrokes(g, dx = width * 0.03f, dy = width * 0.015f))
            assertNotEquals("$ch $r", Grade.Miss, r.grade)
        }
    }

    @Test fun farOffTraceIsMiss() {
        // 文字の 3わり ずれ は ぜんぶ NG
        for (ch in data.keys) {
            val g = glyph(ch)
            val r = g.scorer().score(traceStrokes(g, dx = width * 0.3f, dy = width * 0.15f))
            assertEquals("$ch $r", Grade.Miss, r.grade)
        }
    }

    @Test fun clearlyDifferentLetterIsMiss() {
        val pairs = listOf("い" to "ア", "し" to "の", "あ" to "い", "ン" to "ぬ", "く" to "ろ", "こ" to "め", "つ" to "は", "へ" to "ふ")
        for ((target, written) in pairs) {
            val r = glyph(target).scorer().score(traceStrokes(glyph(written)))
            assertEquals("$target<-$written $r", Grade.Miss, r.grade)
        }
    }

    @Test fun halfTracedIsMiss() {
        // 3画の 「あ」の 1画目だけ
        val g = glyph("あ")
        val r = g.scorer().score(traceStrokes(g).take(1))
        assertEquals("$r", Grade.Miss, r.grade)
    }

    @Test fun singleCrossLineIsMiss() {
        val r = glyph("あ").scorer().score(listOf(floatArrayOf(0f, height / 2, width, height / 2)))
        assertEquals("$r", Grade.Miss, r.grade)
    }
}
