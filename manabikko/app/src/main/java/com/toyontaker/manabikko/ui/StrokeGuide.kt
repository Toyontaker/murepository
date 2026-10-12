package com.toyontaker.manabikko.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.toyontaker.manabikko.trace.Glyph
import kotlin.math.cos
import kotlin.math.sin

/**
 * 書き順ガイドを かく。
 * - おてほん(うすい色)
 * - つぎに かく 線を オレンジで めだたせて、むきを しめす 矢印
 * - 線の かきはじめに 番号
 * - 「かきじゅん」アニメーション(demoStroke ばんめの 線を demoProgress まで)
 */
class StrokeGuide(private val glyph: Glyph, density: Float) {
    private val template = glyph.strokePaint(Palette.Faint.toArgb())
    private val nextStroke = glyph.strokePaint(Color(0xFFFFCC80).toArgb())
    private val demoStroke = glyph.strokePaint(Color(0xFFFF7043).toArgb())
    private val arrow = glyph.strokePaint(Color(0xFFE65100).toArgb(), width = glyph.strokeWidth * 0.18f)

    private val badgeRadius = maxOf(glyph.strokeWidth * 0.42f, 13f * density)
    private val badgeFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val badgeText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = badgeRadius * 1.3f
    }
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color(0xFFE65100).toArgb() }

    /**
     * @param next つぎに かく 線の 番号(0から)。ぜんぶ かいたら 線の数 いじょう。
     * @param demoStroke アニメーション中の 線(null なら アニメーション していない)
     */
    fun draw(canvas: Canvas, next: Int, demoStroke: Int?, demoProgress: Float) {
        // 大きさが まだ きまっていない(画面に でた 直後の 1コマめ)ときは なにも かかない
        if (glyph.width <= 0f || glyph.height <= 0f) return
        glyph.draw(canvas, template)
        val count = glyph.strokes.size

        if (demoStroke != null) {
            // かきじゅん アニメーション:かきおわった 線 + いま かいている 線 + ペン先
            for (i in 0 until demoStroke) canvas.drawPath(glyph.strokes[i], this.demoStroke)
            if (demoStroke < count) {
                canvas.drawPath(glyph.partial(demoStroke, demoProgress), this.demoStroke)
                val (tip, _) = glyph.posTan(demoStroke, glyph.length(demoStroke) * demoProgress)
                canvas.drawCircle(tip.x, tip.y, glyph.strokeWidth * 0.32f, pen)
            }
        } else if (next < count) {
            // つぎに かく 線と、その むき
            canvas.drawPath(glyph.strokes[next], nextStroke)
            drawArrow(canvas, next)
        }

        // 番号(かきはじめの 少し てまえ)。つぎの線は 赤、かいた線は 灰色
        for (i in 0 until count) {
            val (start, tan) = glyph.posTan(i, 0f)
            val back = glyph.strokeWidth * 0.75f
            val x = clamp(start.x - tan.x * back, badgeRadius, glyph.width - badgeRadius)
            val y = clamp(start.y - tan.y * back, badgeRadius, glyph.height - badgeRadius)
            val active = if (demoStroke != null) i == demoStroke else i == next
            badgeFill.color = when {
                active -> Color(0xFFE53935).toArgb()
                demoStroke == null && i < next -> Color(0xFFBDBDBD).toArgb()
                else -> Color(0xFF8D6E63).toArgb()
            }
            val r = if (active) badgeRadius * 1.15f else badgeRadius
            canvas.drawCircle(x, y, r, badgeFill)
            val fm = badgeText.fontMetrics
            canvas.drawText((i + 1).toString(), x, y - (fm.ascent + fm.descent) / 2, badgeText)
        }
    }

    /** min〜max に おさめる。画面が 小さすぎて min > max の ときは まんなかに する(coerceIn は 例外に なる)。 */
    private fun clamp(v: Float, min: Float, max: Float) = if (min > max) (min + max) / 2 else v.coerceIn(min, max)

    /** 線の とちゅうに、すすむ むきの 「>」を かく。 */
    private fun drawArrow(canvas: Canvas, i: Int) {
        val len = glyph.length(i)
        val at = minOf(len * 0.45f, glyph.strokeWidth * 2.2f)
        val (p, t) = glyph.posTan(i, at)
        val wing = glyph.strokeWidth * 0.55f
        val angle = Math.toRadians(150.0)
        fun wingPoint(sign: Int): Pair<Float, Float> {
            val a = angle * sign
            val dx = (t.x * cos(a) - t.y * sin(a)).toFloat()
            val dy = (t.x * sin(a) + t.y * cos(a)).toFloat()
            return p.x + dx * wing to p.y + dy * wing
        }
        val (x1, y1) = wingPoint(1)
        val (x2, y2) = wingPoint(-1)
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(p.x, p.y)
            lineTo(x2, y2)
        }
        canvas.drawPath(path, arrow)
    }
}
