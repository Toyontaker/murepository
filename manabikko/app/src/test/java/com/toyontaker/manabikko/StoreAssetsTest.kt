package com.toyontaker.manabikko

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.toyontaker.manabikko.data.Counter
import com.toyontaker.manabikko.data.Hiragana
import com.toyontaker.manabikko.data.Numbers
import com.toyontaker.manabikko.trace.Glyph
import com.toyontaker.manabikko.trace.Strokes
import com.toyontaker.manabikko.ui.CountingScreen
import com.toyontaker.manabikko.ui.HomeScreen
import com.toyontaker.manabikko.ui.KanaScreen
import com.toyontaker.manabikko.ui.ManabikkoTheme
import com.toyontaker.manabikko.ui.Palette
import com.toyontaker.manabikko.ui.Question
import com.toyontaker.manabikko.ui.QuizScreen
import com.toyontaker.manabikko.ui.TracingScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Google Play の ストア掲載用 画像を つくる(manabikko/store/ に 出力)。
 *   ./gradlew recordRoborazziDebug --tests '*StoreAssetsTest*'
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class StoreAssetsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    // setContent は 1かい だけ なので、中身を きりかえる(key で 毎回 まっさらに する)
    private val current = mutableStateOf<@Composable () -> Unit>({})
    private var step = mutableStateOf(0)
    private var started = false

    private fun show(content: @Composable () -> Unit) {
        current.value = content
        step.value++
        if (!started) {
            started = true
            val speaker = Speaker(context)
            val sfx = Sfx(context)
            rule.setContent {
                ManabikkoTheme {
                    CompositionLocalProvider(LocalSpeaker provides speaker, LocalSfx provides sfx) {
                        Box(Modifier.fillMaxSize().background(Palette.Background)) {
                            key(step.value) { current.value() }
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(600)
    }

    private fun save(path: String) = rule.onRoot().captureRoboImage("../store/$path")

    // ---------- アイコン 512×512 ----------

    @Config(qualifiers = "w512dp-h512dp-mdpi")
    @Test fun icon() {
        val size = 512
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFFFFB74D.toInt())
        // アダプティブアイコンの 見える範囲(108dp のうち まんなか 72dp)を 512px に
        val fg = ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)!!
        val full = size * 108 / 72
        val offset = (full - size) / 2
        fg.setBounds(-offset, -offset, full - offset, full - offset)
        fg.draw(canvas)
        val out = File("../store/icon-512.png")
        out.parentFile!!.mkdirs()
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // ---------- フィーチャーグラフィック 1024×500 ----------

    @Config(qualifiers = "w1024dp-h500dp-mdpi")
    @Test fun featureGraphic() {
        show {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(Color(0xFFFFE0B2), Color(0xFFFFF8E7), Color(0xFFE1F5FE)), Offset.Zero, Offset(1024f, 500f))),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🐣", fontSize = 96.sp)
                        Text("まなびっこ", fontSize = 104.sp, fontWeight = FontWeight.Black, color = Palette.Ink, modifier = Modifier.padding(horizontal = 24.dp))
                        Text("🌈", fontSize = 96.sp)
                    }
                    Text("ひらがな ・ カタカナ ・ かず", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Palette.Ink)
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        for ((t, c) in listOf("あ" to Palette.Red, "ア" to Palette.Orange, "🍎" to Palette.Green, "✏️" to Palette.Blue, "⭐" to Palette.Purple)) {
                            Box(Modifier.clip(RoundedCornerShape(28.dp)).background(c).padding(horizontal = 26.dp, vertical = 6.dp)) {
                                Text(t, fontSize = 52.sp, fontWeight = FontWeight.Black, color = Palette.Ink)
                            }
                        }
                    }
                }
            }
        }
        save("feature-graphic-1024x500.png")
    }

    // ---------- スクリーンショット ----------

    private fun traceHanamaru() {
        val node = rule.onNodeWithTag("trace_canvas").fetchSemanticsNode()
        val glyph = Glyph(Strokes.get(context, "あ"), node.size.width.toFloat(), node.size.height.toFloat())
        for (i in glyph.strokes.indices) {
            val pts = (0..30).map { k -> glyph.posTan(i, glyph.length(i) * k / 30f).first.let { Offset(it.x, it.y) } }
            rule.onNodeWithTag("trace_canvas").performTouchInput {
                down(pts.first()); pts.drop(1).forEach { moveTo(it) }; up()
            }
        }
    }

    private fun screenshots(dir: String) {
        show { HomeScreen(onOpen = {}) }
        save("$dir/01_home.png")

        show { KanaScreen("ひらがな", Hiragana, Palette.Red, onHome = {}) }
        rule.onNodeWithText("く").performClick()
        rule.mainClock.advanceTimeBy(800)
        save("$dir/02_hiragana.png")

        val apple = Numbers.things.indexOfFirst { it.counter == Counter.Ko }
        show { CountingScreen(onHome = {}, initialTarget = 5, initialThing = apple) }
        listOf(0, 1, 2).forEach { rule.onNodeWithTag("item_$it").performClick() }
        rule.mainClock.advanceTimeBy(600)
        save("$dir/03_counting.png")

        show { TracingScreen(onHome = {}) }
        rule.mainClock.advanceTimeBy(2300)
        save("$dir/04_stroke_order.png")

        show { TracingScreen(onHome = {}, autoDemo = false) }
        traceHanamaru()
        rule.onNodeWithText("できた").performClick()
        rule.mainClock.advanceTimeBy(900)
        save("$dir/05_hanamaru.png")

        val fish = Numbers.things.first { it.counter == Counter.Hiki }
        show { QuizScreen(onHome = {}, firstQuestion = Question.HowMany(3, fish, listOf(2, 3, 4).map { fish.counter.label(it) })) }
        save("$dir/06_quiz.png")
    }

    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    @Test fun phoneScreenshots() = screenshots("phone")

    @Config(qualifiers = "w600dp-h960dp-xhdpi")
    @Test fun tablet7Screenshots() = screenshots("tablet-7in")

    @Config(qualifiers = "w800dp-h1280dp-xhdpi")
    @Test fun tablet10Screenshots() = screenshots("tablet-10in")
}
