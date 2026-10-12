package com.toyontaker.manabikko.trace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** ほんものの 文字(フォント)を つかって 採点の かげんを たしかめる。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class GlyphScoringTest {
    private val width = 1000f
    private val height = 1300f

    /** おてほんの マスを よこに なぞる 線(行ごとに 1本)。dx, dy だけ ずらせる。 */
    private fun traceOver(s: TraceScorer, dx: Float = 0f, dy: Float = 0f, rowStep: Int = 1): List<FloatArray> {
        val strokes = mutableListOf<FloatArray>()
        for (r in 0 until s.rows step rowStep) {
            var c = 0
            while (c < s.cols) {
                if (!s.mask[r * s.cols + c]) { c++; continue }
                val start = c
                while (c < s.cols && s.mask[r * s.cols + c]) c++
                val y = (r + 0.5f) * s.cell + dy
                strokes += floatArrayOf((start + 0.5f) * s.cell + dx, y, (c - 0.5f) * s.cell + dx, y)
            }
        }
        return strokes
    }

    private fun scorer(char: String) = Glyph(char, width, height).scorer()

    @Test fun glyphIsRendered() {
        val s = scorer("あ")
        val filled = s.mask.count { it }.toFloat() / s.mask.size
        assertTrue("filled=$filled", filled in 0.03f..0.4f)
    }

    @Test fun exactTraceIsGreat() {
        for (ch in listOf("あ", "ア", "の", "ん")) {
            val s = scorer(ch)
            assertEquals(ch, Grade.Great, s.score(traceOver(s)).grade)
        }
    }

    @Test fun sloppyButCloseTraceIsOk() {
        // 2マスおきの 行だけ、しかも 文字の 6% くらい ずれて なぞっても OK
        for (ch in listOf("あ", "い", "し", "の", "ア", "ン")) {
            val s = scorer(ch)
            val r = s.score(traceOver(s, dx = width * 0.06f, dy = width * 0.03f, rowStep = 2))
            assertTrue("$ch $r", r.grade != Grade.Miss)
        }
    }

    @Test fun farOffTraceIsMiss() {
        // 文字の 大きさの 3わり くらい ずれていたら だめ
        for (ch in listOf("あ", "い", "し", "の", "ア", "ン", "ぬ")) {
            val s = scorer(ch)
            val r = s.score(traceOver(s, dx = width * 0.3f, dy = width * 0.15f))
            assertEquals("$ch $r", Grade.Miss, r.grade)
        }
    }

    @Test fun halfTracedIsMiss() {
        // 上 半分しか なぞっていない
        val s = scorer("い")
        val r = s.score(traceOver(s).filter { it[1] < height * 0.45f })
        assertEquals("$r", Grade.Miss, r.grade)
    }

    @Test fun singleCrossLineIsMiss() {
        val s = scorer("あ")
        val r = s.score(listOf(floatArrayOf(0f, height / 2, width, height / 2)))
        assertEquals("$r", Grade.Miss, r.grade)
    }
}
