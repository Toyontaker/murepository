package com.toyontaker.manabikko.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import com.toyontaker.manabikko.trace.Glyph
import com.toyontaker.manabikko.trace.Strokes
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 書き順ガイドが どんな 大きさ・状態でも 落ちないこと。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class StrokeGuideTest {
    private val data = Strokes.parse(java.io.File("src/main/assets/strokes.tsv").readText())

    private fun drawAll(width: Int, height: Int) {
        val bitmap = Bitmap.createBitmap(maxOf(width, 1), maxOf(height, 1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        for ((_, strokes) in data) {
            val guide = StrokeGuide(Glyph(strokes, width.toFloat(), height.toFloat()), density = 3f)
            for (next in 0..strokes.size) guide.draw(canvas, next, null, 0f)
            for (demo in 0..strokes.size) guide.draw(canvas, 0, demo, 0.5f)
        }
    }

    /** 画面に でた 直後(まだ 大きさが きまっていない 1コマめ)。 */
    @Test fun zeroSizeDoesNotCrash() = drawAll(0, 0)

    @Test fun tinySizeDoesNotCrash() = drawAll(40, 30)

    @Test fun normalSizeDoesNotCrash() = drawAll(1000, 1300)
}
