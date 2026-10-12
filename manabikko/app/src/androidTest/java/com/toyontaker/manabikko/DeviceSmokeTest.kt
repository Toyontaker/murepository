package com.toyontaker.manabikko

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.toyontaker.manabikko.trace.Glyph
import com.toyontaker.manabikko.trace.Strokes
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ほんものの 端末(エミュレータ)で うごかす 確認。
 * 読み上げ・効果音・フォント・タッチを 実際の Android で とおす。
 */
@RunWith(AndroidJUnit4::class)
class DeviceSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Before fun resetSettings() {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit(commit = true) {
                putString("trace_order", "seq")
                putString("trace_script", "hira")
                putString("count_order", "seq")
            }
    }

    private fun atHome() = rule.onAllNodesWithText("まなびっこ").fetchSemanticsNodes().isNotEmpty()

    private fun home() {
        rule.onNodeWithContentDescription("おうちに もどる").performClick()
        rule.waitUntil(5_000) { atHome() }
    }

    private fun open(menu: String) {
        rule.onNodeWithText(menu).performClick()
        rule.waitUntil(5_000) { !atHome() }
    }

    @Test fun everyScreenOpens() {
        for (menu in listOf("ひらがな", "カタカナ", "かず", "なぞりがき", "クイズ")) {
            open(menu)
            rule.waitForIdle()
            home()
        }
        rule.onNodeWithContentDescription("このアプリについて(ながおし)").performTouchInput { longClick(durationMillis = 1800) }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("クレジット・ライセンス").fetchSemanticsNodes().isNotEmpty() }
        home()
    }

    @Test fun kanaCardsAllTappable() {
        open("ひらがな")
        // カードに 出ている 文字と かぶらないよう、毎回 ちがう 文字を おす
        for (ch in listOf("か", "さ", "た", "な")) {
            rule.onNodeWithText(ch).performClick()
            rule.waitForIdle()
        }
        rule.onNodeWithContentDescription("つぎの もじ").performClick()
        home()
    }

    @Test fun countingToTheEnd() {
        open("かず")
        // 1こ を かぞえる(初期は じゅんばん で 1から)
        rule.onNodeWithTag("item_0").performClick()
        rule.waitUntil(6_000) { rule.onAllNodesWithTag("item_1").fetchSemanticsNodes().isNotEmpty() }
        home()
    }

    @Test fun tracingHanamaruOnDevice() {
        open("なぞりがき")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val node = rule.onNodeWithTag("trace_canvas").fetchSemanticsNode()
        val glyph = Glyph(Strokes.get(context, "あ"), node.size.width.toFloat(), node.size.height.toFloat())
        for (i in glyph.strokes.indices) {
            val points = (0..30).map { k ->
                val (p, _) = glyph.posTan(i, glyph.length(i) * k / 30f)
                Offset(p.x, p.y)
            }
            rule.onNodeWithTag("trace_canvas").performTouchInput {
                down(points.first())
                points.drop(1).forEach { moveTo(it) }
                up()
            }
        }
        rule.onNodeWithText("できた").performClick()
        rule.waitUntil(3_000) { rule.onAllNodesWithText("はなまる", substring = true).fetchSemanticsNodes().isNotEmpty() }
        // つぎの 文字「い」へ
        rule.waitUntil(8_000) { rule.onAllNodesWithText("🐶 いぬ").fetchSemanticsNodes().isNotEmpty() }
        home()
    }

    @Test fun quizAnswersWork() {
        open("クイズ")
        rule.onNodeWithContentDescription("もういちど きく").performClick()
        repeat(6) {
            // いちばん 下(さいご)の こたえボタンを おす(せいかいでも まちがいでも 落ちないこと)
            val buttons = rule.onAllNodes(hasClickAction())
            buttons[buttons.fetchSemanticsNodes().size - 1].performClick()
            Thread.sleep(2_500)
            rule.waitForIdle()
        }
        home()
    }
}

