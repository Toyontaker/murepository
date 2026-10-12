package com.toyontaker.manabikko.trace

import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max

/** なぞりがき の けっか。 */
enum class Grade { Great, Good, Miss }

data class TraceResult(
    /** かいた線のうち、おてほんの 上(ちかく)に ある わりあい。はみだしが 多いと ひくい。 */
    val precision: Float,
    /** おてほんのうち、なぞられた わりあい。かきのこしが 多いと ひくい。 */
    val coverage: Float,
    val grade: Grade,
)

/**
 * おてほんの文字 と こどもが かいた線 を、こまかい マス目の上で くらべて 採点する。
 *
 * 3さい向けに はんていは ゆるめ:線の太さぶん ずれても OK にして、
 * 「はみだしすぎ」か「ぜんぜん なぞっていない」ときだけ [Grade.Miss] にする。
 *
 * @param mask おてほんの文字が ある マスは true(cols × rows、行ごとに ならぶ)
 * @param cell 1マスの 大きさ(px)
 */
class TraceScorer(
    val mask: BooleanArray,
    val cols: Int,
    val rows: Int,
    val cell: Float,
    private val precisionTolerance: Int = PRECISION_TOLERANCE,
) {
    init {
        require(mask.size == cols * rows)
    }

    private val glyphCount = mask.count { it }

    // おてほんの まわり precisionTolerance マス までは「上を なぞっている」と みなす
    private val nearGlyph = dilate(mask, precisionTolerance)

    /** strokes:線ごとの 点の ならび(x, y を じゅんばんに ならべた FloatArray)。 */
    fun score(strokes: List<FloatArray>): TraceResult {
        val drawn = rasterize(strokes)
        val drawnCount = drawn.count { it }
        if (glyphCount == 0 || drawnCount < MIN_DRAWN_CELLS) return TraceResult(0f, 0f, Grade.Miss)

        var onGlyph = 0
        for (i in drawn.indices) if (drawn[i] && nearGlyph[i]) onGlyph++
        val precision = onGlyph.toFloat() / drawnCount

        val nearDrawn = dilate(drawn, COVERAGE_TOLERANCE)
        var covered = 0
        for (i in mask.indices) if (mask[i] && nearDrawn[i]) covered++
        val coverage = covered.toFloat() / glyphCount

        val grade = when {
            precision >= GREAT_PRECISION && coverage >= GREAT_COVERAGE -> Grade.Great
            precision >= GOOD_PRECISION && coverage >= GOOD_COVERAGE -> Grade.Good
            else -> Grade.Miss
        }
        return TraceResult(precision, coverage, grade)
    }

    /** 線を マス目に おとす(点と点の あいだも 半マスごとに うめる)。 */
    private fun rasterize(strokes: List<FloatArray>): BooleanArray {
        val out = BooleanArray(cols * rows)
        fun mark(x: Float, y: Float) {
            val c = (x / cell).toInt()
            val r = (y / cell).toInt()
            if (c in 0 until cols && r in 0 until rows) out[r * cols + c] = true
        }
        for (pts in strokes) {
            if (pts.size < 2) continue
            mark(pts[0], pts[1])
            var i = 2
            while (i + 1 < pts.size) {
                val x0 = pts[i - 2]
                val y0 = pts[i - 1]
                val x1 = pts[i]
                val y1 = pts[i + 1]
                val steps = max(1, ceil(hypot(x1 - x0, y1 - y0) / (cell * 0.5f)).toInt())
                for (s in 1..steps) {
                    val t = s.toFloat() / steps
                    mark(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t)
                }
                i += 2
            }
        }
        return out
    }

    /** まわり radius マス(円形)まで ひろげる。 */
    private fun dilate(src: BooleanArray, radius: Int): BooleanArray {
        val out = BooleanArray(src.size)
        val r2 = radius * radius
        for (r in 0 until rows) for (c in 0 until cols) {
            if (!src[r * cols + c]) continue
            for (dr in -radius..radius) for (dc in -radius..radius) {
                if (dr * dr + dc * dc > r2) continue
                val rr = r + dr
                val cc = c + dc
                if (rr in 0 until rows && cc in 0 until cols) out[rr * cols + cc] = true
            }
        }
        return out
    }

    companion object {
        /** 画面の みじかい辺を 何マスに わけるか。 */
        const val GRID = 48

        const val PRECISION_TOLERANCE = 3
        const val COVERAGE_TOLERANCE = 3
        const val MIN_DRAWN_CELLS = 6

        const val GOOD_PRECISION = 0.7f
        const val GOOD_COVERAGE = 0.65f
        const val GREAT_PRECISION = 0.85f
        const val GREAT_COVERAGE = 0.75f
    }
}
