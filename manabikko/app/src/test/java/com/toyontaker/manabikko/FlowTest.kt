package com.toyontaker.manabikko

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import com.toyontaker.manabikko.data.Hiragana
import com.toyontaker.manabikko.trace.Glyph
import com.toyontaker.manabikko.trace.Strokes
import com.toyontaker.manabikko.ui.CountingScreen
import com.toyontaker.manabikko.ui.ManabikkoTheme
import com.toyontaker.manabikko.ui.Question
import com.toyontaker.manabikko.ui.QuizScreen
import com.toyontaker.manabikko.ui.TracingScreen
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 画面ごとの あそびかたを 最初から 最後まで とおす テスト。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class FlowTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun show(content: @Composable () -> Unit) {
        val speaker = Speaker(context)
        val sfx = Sfx(context)
        rule.setContent {
            ManabikkoTheme {
                CompositionLocalProvider(LocalSpeaker provides speaker, LocalSfx provides sfx, content = content)
            }
        }
    }

    private fun setPref(key: String, value: String) =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit(commit = true) { putString(key, value) }

    private fun settle(millis: Long) = rule.mainClock.advanceTimeBy(millis)

    private fun exists(text: String, substring: Boolean = false) =
        rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

    // ---------------- クイズ ----------------

    private val kanaQuestion = { Question.FindKana(answer = "あ", choices = listOf("い", "あ", "う")) }

    @Test fun quizAllCorrectGivesFiveStars() {
        show { QuizScreen(onHome = {}, questions = kanaQuestion) }
        repeat(5) {
            rule.onNodeWithText("あ").performClick()
            settle(2500)
        }
        rule.onNodeWithText("ぜんぶ おわったよ!").assertExists()
        rule.onNodeWithText("⭐ × 5").assertExists()
    }

    @Test fun quizWrongFirstTryLosesThatStar() {
        show { QuizScreen(onHome = {}, questions = kanaQuestion) }
        rule.onNodeWithText("い").performClick() // まちがい
        settle(500)
        rule.onNodeWithText("あ").performClick()
        settle(2500)
        repeat(4) {
            rule.onNodeWithText("あ").performClick()
            settle(2500)
        }
        rule.onNodeWithText("⭐ × 4").assertExists()
        // もういちど で 0 から
        rule.onNodeWithText("もういちど").performClick()
        settle(500)
        rule.onNodeWithText("「あ」は どれかな?").assertExists()
    }

    // ---------------- かず ----------------

    private fun itemCount() = (0 until 10).count { rule.onAllNodesWithTag("item_$it").fetchSemanticsNodes().isNotEmpty() }

    @Test fun countingInOrderGoesToNextNumber() {
        setPref("count_order", "seq")
        show { CountingScreen(onHome = {}, initialTarget = 2, initialThing = 0) }
        assert(itemCount() == 2)
        rule.onNodeWithTag("item_0").performClick()
        // おなじ ものを 2かい さわっても 1つと かぞえる
        rule.onNodeWithTag("item_0").performClick()
        settle(300)
        rule.onNodeWithText("いっこ").assertExists()
        rule.onNodeWithTag("item_1").performClick()
        settle(1000)
        rule.onNodeWithText("2こ!", substring = true).assertExists()
        settle(4000)
        assert(itemCount() == 3) { "itemCount=${itemCount()}" }
    }

    @Test fun countingRandomGoesToDifferentNumber() {
        setPref("count_order", "random")
        show { CountingScreen(onHome = {}, initialTarget = 2, initialThing = 0) }
        rule.onNodeWithTag("item_0").performClick()
        rule.onNodeWithTag("item_1").performClick()
        settle(1000)
        rule.onNodeWithText("2こ!", substring = true).assertExists()
        settle(4000)
        assertNotEquals(2, itemCount())
    }

    // ---------------- なぞりがき ----------------

    private fun word() = Hiragana.all.first { exists("${it.emoji} ${it.word}") }.char

    /** 書き順データの 線の 上を ゆびで なぞる。dx だけ ずらせる。 */
    private fun traceLetter(char: String, dx: Float = 0f) {
        val node = rule.onNodeWithTag("trace_canvas").fetchSemanticsNode()
        val glyph = Glyph(Strokes.get(context, char), node.size.width.toFloat(), node.size.height.toFloat())
        for (i in glyph.strokes.indices) {
            val points = (0..30).map { k ->
                val (p, _) = glyph.posTan(i, glyph.length(i) * k / 30f)
                Offset(p.x + dx, p.y)
            }
            rule.onNodeWithTag("trace_canvas").performTouchInput {
                down(points.first())
                points.drop(1).forEach { moveTo(it) }
                up()
            }
        }
    }

    @Test fun tracingHanamaruMovesToNextLetter() {
        setPref("trace_order", "seq")
        setPref("trace_script", "hira")
        show { TracingScreen(onHome = {}, autoDemo = false) }
        settle(500)
        assert(word() == "あ")
        traceLetter("あ")
        rule.onNodeWithText("できた").performClick()
        settle(500)
        rule.onNodeWithText("はなまる", substring = true).assertExists()
        settle(3000)
        // じゅんばん なので つぎは「い」
        assert(word() == "い") { "now ${word()}" }
    }

    @Test fun tracingMissStaysOnSameLetter() {
        setPref("trace_order", "seq")
        setPref("trace_script", "hira")
        show { TracingScreen(onHome = {}, autoDemo = false) }
        settle(500)
        // 文字から 大きく はずれた ところを なぞる
        traceLetter("あ", dx = 600f)
        rule.onNodeWithText("できた").performClick()
        settle(500)
        rule.onNodeWithText("おしい", substring = true).assertExists()
        settle(3000)
        assert(word() == "あ")
        // ◀ ▶ でも うごける
        rule.onNodeWithText("▶").performClick()
        settle(500)
        assert(word() == "い")
        rule.onNodeWithText("◀").performClick()
        settle(500)
        assert(word() == "あ")
    }

    @Test fun tracingEmptyDoneDoesNothing() {
        show { TracingScreen(onHome = {}, autoDemo = false) }
        settle(500)
        rule.onNodeWithText("できた").performClick()
        settle(500)
        rule.onAllNodesWithText("はなまる", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("おしい", substring = true).assertCountEquals(0)
    }

    @Test fun tracingRowRandomOnlyShowsThatRow() {
        setPref("trace_order", "row:1") // か行
        setPref("trace_script", "hira")
        show { TracingScreen(onHome = {}, autoDemo = false) }
        settle(500)
        val seen = mutableSetOf<String>()
        repeat(12) {
            seen += word()
            rule.onNodeWithText("▶").performClick()
            settle(300)
        }
        assert(seen == setOf("か", "き", "く", "け", "こ")) { "$seen" }
    }
}
