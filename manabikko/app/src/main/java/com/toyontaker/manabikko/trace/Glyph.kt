package com.toyontaker.manabikko.trace

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PointF
import androidx.core.graphics.PathParser
import androidx.core.graphics.createBitmap
import kotlin.math.hypot

/**
 * なぞりがきの おてほん文字。書き順データの 線を、画面の 大きさに あわせて おく。
 * 画面に かくときも 採点用の マス目を つくるときも 同じ 線を つかう。
 */
class Glyph(strokeData: List<String>, val width: Float, val height: Float) {
    private val size = minOf(width, height)

    /** おてほんの 線の 太さ(px)。 */
    val strokeWidth = size * 0.085f

    /** 書く じゅんばんに ならんだ 線(画面の 座標)。 */
    val strokes: List<Path> = run {
        val scale = size * 0.95f / Strokes.BOX
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate((width - Strokes.BOX * scale) / 2, (height - Strokes.BOX * scale) / 2)
        }
        strokeData.map { d -> PathParser.createPathFromPathData(d).apply { transform(matrix) } }
    }

    private val measures = strokes.map { PathMeasure(it, false) }

    /** 線の ながさ(px)。 */
    fun length(i: Int) = measures[i].length

    /** 線の とちゅう(distance px の ところ)の 位置と むき(単位ベクトル)。 */
    fun posTan(i: Int, distance: Float): Pair<PointF, PointF> {
        val pos = FloatArray(2)
        val tan = FloatArray(2)
        measures[i].getPosTan(distance.coerceIn(0f, length(i)), pos, tan)
        val n = hypot(tan[0], tan[1]).takeIf { it > 0f } ?: 1f
        return PointF(pos[0], pos[1]) to PointF(tan[0] / n, tan[1] / n)
    }

    /** 線の はじめから progress(0〜1)ぶん だけの 線。 */
    fun partial(i: Int, progress: Float): Path =
        Path().also { measures[i].getSegment(0f, length(i) * progress.coerceIn(0f, 1f), it, true) }

    fun strokePaint(color: Int, width: Float = strokeWidth) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    fun draw(canvas: Canvas, paint: Paint) = strokes.forEach { canvas.drawPath(it, paint) }

    /** 採点用の スコアラー(線を 小さな ビットマップに かいて マス目に する)。 */
    fun scorer(): TraceScorer {
        val cell = size / TraceScorer.GRID
        val cols = (width / cell).toInt().coerceAtLeast(1)
        val rows = (height / cell).toInt().coerceAtLeast(1)
        val bitmap = createBitmap(cols, rows)
        val canvas = Canvas(bitmap)
        canvas.scale(1f / cell, 1f / cell)
        draw(canvas, strokePaint(android.graphics.Color.BLACK))
        val pixels = IntArray(cols * rows)
        bitmap.getPixels(pixels, 0, cols, 0, 0, cols, rows)
        bitmap.recycle()
        val mask = BooleanArray(pixels.size) { (pixels[it] ushr 24) > 100 }
        return TraceScorer(mask, cols, rows, cell)
    }
}
