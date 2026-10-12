package com.toyontaker.manabikko

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.toyontaker.manabikko.ui.CountingScreen
import com.toyontaker.manabikko.ui.KanaScreen
import com.toyontaker.manabikko.data.Hiragana
import com.toyontaker.manabikko.data.Katakana
import com.toyontaker.manabikko.ui.HomeScreen
import com.toyontaker.manabikko.ui.ManabikkoTheme
import com.toyontaker.manabikko.ui.Palette
import com.toyontaker.manabikko.ui.QuizScreen
import com.toyontaker.manabikko.ui.Question
import com.toyontaker.manabikko.data.Counter
import com.toyontaker.manabikko.data.Numbers
import com.toyontaker.manabikko.ui.TracingScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 各画面を JVM 上で描画して PNG に保存する(`./gradlew recordRoborazziDebug`)。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun shot(name: String, before: () -> Unit = {}, content: @Composable () -> Unit) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val speaker = Speaker(context)
        val sfx = Sfx(context)
        rule.setContent {
            ManabikkoTheme {
                CompositionLocalProvider(LocalSpeaker provides speaker, LocalSfx provides sfx) {
                    Box(Modifier.fillMaxSize().background(Palette.Background)) { content() }
                }
            }
        }
        before()
        rule.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Test fun home() = shot("home") { HomeScreen(onOpen = {}) }
    @Test fun hiragana() = shot("hiragana") { KanaScreen("ひらがな", Hiragana, Palette.Red, onHome = {}) }
    @Test fun katakana() = shot("katakana") { KanaScreen("カタカナ", Katakana, Palette.Orange, onHome = {}) }
    @Test fun counting() = shot("counting", before = {
        // ばなな 10ぽん のうち 4ほん かぞえた ところ
        listOf(0, 3, 6, 9).forEach { rule.onNodeWithTag("item_$it").performClick() }
        rule.mainClock.advanceTimeBy(1000)
    }) { CountingScreen(onHome = {}, initialTarget = 10, initialThing = 1) }
    @Test fun tracing() = shot("tracing") { TracingScreen(onHome = {}) }
    @Test fun quiz() = shot("quiz") {
        val fish = Numbers.things.first { it.counter == Counter.Hiki }
        QuizScreen(onHome = {}, firstQuestion = Question.HowMany(3, fish, listOf(1, 3, 6).map { fish.counter.label(it) }))
    }
}
