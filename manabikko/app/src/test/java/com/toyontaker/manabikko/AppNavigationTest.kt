package com.toyontaker.manabikko

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** ほんものの MainActivity で ホーム ⇔ 各画面 を いききする。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class AppNavigationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun settle() = rule.mainClock.advanceTimeBy(1000)

    private fun atHome() = rule.onAllNodesWithText("まなびっこ").fetchSemanticsNodes().isNotEmpty()

    private val menus = listOf(
        "ひらがな" to "まえの もじ",
        "カタカナ" to "まえの もじ",
        "かず" to "タップしてね",
        "なぞりがき" to "できた",
        "クイズ" to "もういちど きく",
    )

    @Test fun everyScreenOpensAndHomeButtonReturns() {
        for ((menu, marker) in menus) {
            rule.onNodeWithText(menu).performClick()
            settle()
            assert(!atHome()) { "$menu did not open" }
            assert(
                rule.onAllNodesWithText(marker).fetchSemanticsNodes().isNotEmpty() ||
                    runCatching { rule.onNodeWithContentDescription(marker).assertExists() }.isSuccess,
            ) { "$menu: $marker not found" }
            rule.onNodeWithContentDescription("おうちに もどる").performClick()
            settle()
            assert(atHome()) { "home button from $menu" }
        }
    }

    @Test fun backButtonReturnsHomeAndDoesNotCloseApp() {
        for ((menu, _) in menus) {
            rule.onNodeWithText(menu).performClick()
            settle()
            rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            settle()
            assert(atHome()) { "back from $menu" }
        }
        // ホームで もどる を おしても アプリは とじない
        rule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
            assertFalse(it.isFinishing)
        }
        settle()
        assert(atHome())
    }

    @Test fun aboutNeedsLongPress() {
        rule.onNodeWithContentDescription("このアプリについて(ながおし)").performClick()
        settle()
        assert(atHome())
        rule.onNodeWithContentDescription("このアプリについて(ながおし)").performTouchInput { longClick(durationMillis = 1800) }
        settle()
        rule.onNodeWithText("アプリについて").assertExists()
        rule.onNodeWithText("クレジット・ライセンス").assertExists()
    }

    @Test fun rotationKeepsCurrentScreen() {
        rule.onNodeWithText("かず").performClick()
        settle()
        rule.activityRule.scenario.recreate()
        settle()
        assert(!atHome())
    }
}
