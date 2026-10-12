package com.toyontaker.manabikko

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.toyontaker.manabikko.ui.CountingScreen
import com.toyontaker.manabikko.ui.ManabikkoTheme
import com.toyontaker.manabikko.ui.TracingScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** ちょっと さわった だけでは かわらず、ながおし で はじめて かわること。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class LongPressTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(content: @Composable () -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val speaker = Speaker(context)
        val sfx = Sfx(context)
        rule.setContent {
            ManabikkoTheme {
                CompositionLocalProvider(LocalSpeaker provides speaker, LocalSfx provides sfx, content = content)
            }
        }
    }


    @Test fun numberButtonNeedsLongPress() {
        show { CountingScreen(onHome = {}, initialTarget = 3, initialThing = 0) }
        rule.onAllNodesWithTag("item_2").assertCountEquals(1)
        rule.onAllNodesWithTag("item_3").assertCountEquals(0)

        // ちょっと さわった だけ → かわらない
        rule.onNodeWithText("5").performClick()
        rule.mainClock.advanceTimeBy(500)
        rule.onAllNodesWithTag("item_3").assertCountEquals(0)

        // 0.5びょう だけ おした → かわらない
        rule.onNodeWithText("5").performTouchInput { longClick(durationMillis = 500) }
        rule.mainClock.advanceTimeBy(500)
        rule.onAllNodesWithTag("item_3").assertCountEquals(0)

        // ながおし → 5こ に かわる
        rule.onNodeWithText("5").performTouchInput { longClick(durationMillis = 1300) }
        rule.mainClock.advanceTimeBy(500)
        rule.onAllNodesWithTag("item_4").assertCountEquals(1)
        rule.onAllNodesWithTag("item_5").assertCountEquals(0)
    }

    @Test fun traceSettingsNeedLongPress() {
        show { TracingScreen(onHome = {}, autoDemo = false) }
        rule.onNodeWithText("あ→").performClick()
        rule.mainClock.advanceTimeBy(500)
        rule.onAllNodesWithText("OK").assertCountEquals(0)

        rule.onNodeWithText("あ→").performTouchInput { longClick(durationMillis = 1000) }
        rule.mainClock.advanceTimeBy(500)
        rule.onAllNodesWithText("OK").assertCountEquals(0)

        rule.onNodeWithText("あ→").performTouchInput { longClick(durationMillis = 1800) }
        rule.mainClock.advanceTimeBy(500)
        rule.onAllNodesWithText("OK").assertCountEquals(1)
    }

    @Test fun countOrderToggleNeedsLongPress() {
        show { CountingScreen(onHome = {}, initialTarget = 3, initialThing = 0) }
        rule.onNodeWithText("🎲 ランダム").performClick()
        rule.mainClock.advanceTimeBy(500)
        rule.onAllNodesWithText("🎲 ランダム").assertCountEquals(1)

        rule.onNodeWithText("🎲 ランダム").performTouchInput { longClick(durationMillis = 1800) }
        rule.mainClock.advanceTimeBy(500)
        rule.onAllNodesWithText("1→2→3").assertCountEquals(1)
    }
}
