package com.toyontaker.manabikko.trace

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface

/**
 * なぞりがきの おてほん文字の かきかた。
 * 画面に かくときも 採点用の マス目を つくるときも 同じ レイアウトを つかう。
 */
class Glyph(val char: String, val width: Float, val height: Float) {
    private val textSize = minOf(width, height) * 0.78f

    fun paint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = this@Glyph.textSize
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    fun draw(canvas: Canvas, paint: Paint) {
        val fm = paint.fontMetrics
        val baseline = height / 2 - (fm.ascent + fm.descent) / 2
        canvas.drawText(char, width / 2, baseline, paint)
    }

    /** 採点用の スコアラー(文字を 小さな ビットマップに かいて マス目に する)。 */
    fun scorer(): TraceScorer {
        val cell = minOf(width, height) / TraceScorer.GRID
        val cols = (width / cell).toInt().coerceAtLeast(1)
        val rows = (height / cell).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(1f / cell, 1f / cell)
        draw(canvas, paint(android.graphics.Color.BLACK))
        val pixels = IntArray(cols * rows)
        bitmap.getPixels(pixels, 0, cols, 0, 0, cols, rows)
        bitmap.recycle()
        val mask = BooleanArray(pixels.size) { (pixels[it] ushr 24) > 100 }
        return TraceScorer(mask, cols, rows, cell)
    }
}
