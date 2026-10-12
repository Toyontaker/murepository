package com.toyontaker.manabikko

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.toyontaker.manabikko.data.Counter
import com.toyontaker.manabikko.data.Hiragana
import com.toyontaker.manabikko.data.Katakana
import com.toyontaker.manabikko.data.Numbers
import com.toyontaker.manabikko.ui.AboutScreen
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

/**
 * いろいろな 画面サイズで ぜんぶの 画面を かいて、build/screen-sizes/ に 保存する(目で 確認する用)。
 * 落ちないこと も たしかめる。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class ScreenSizeTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private enum class S { Home, Hiragana, Katakana, Counting, Tracing, Quiz, About }

    private fun captureAll(device: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val speaker = Speaker(context)
        val sfx = Sfx(context)
        val current = mutableStateOf(S.Home)
        val book = Numbers.things.indexOfFirst { it.counter == Counter.Satsu }
        val shoes = Numbers.things.first { it.counter == Counter.Soku }
        rule.setContent {
            ManabikkoTheme {
                CompositionLocalProvider(LocalSpeaker provides speaker, LocalSfx provides sfx) {
                    Box(Modifier.fillMaxSize().background(Palette.Background)) {
                        when (current.value) {
                            S.Home -> HomeScreen(onOpen = {})
                            S.Hiragana -> KanaScreen("ひらがな", Hiragana, Palette.Red, onHome = {})
                            S.Katakana -> KanaScreen("カタカナ", Katakana, Palette.Orange, onHome = {})
                            S.Counting -> CountingScreen(onHome = {}, initialTarget = 10, initialThing = book)
                            S.Tracing -> TracingScreen(onHome = {}, autoDemo = false)
                            S.Quiz -> QuizScreen(
                                onHome = {},
                                firstQuestion = Question.HowMany(5, shoes, listOf(3, 5, 4).map { shoes.counter.label(it) }),
                            )
                            S.About -> AboutScreen(onHome = {}, speechReady = true)
                        }
                    }
                }
            }
        }
        for (s in S.entries) {
            current.value = s
            rule.mainClock.advanceTimeBy(500)
            if (s == S.Counting) {
                listOf(0, 4, 9).forEach { rule.onNodeWithTag("item_$it").performClick() }
                rule.mainClock.advanceTimeBy(500)
            }
            rule.onRoot().captureRoboImage("build/screen-sizes/${device}_${s.name.lowercase()}.png")
        }
    }

    @Config(qualifiers = "w320dp-h568dp-xhdpi")
    @Test fun smallPhone() = captureAll("1_small_320x568")

    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    @Test fun phoneLandscape() = captureAll("2_land_891x411")

    @Config(qualifiers = "w800dp-h1280dp-xhdpi")
    @Test fun tablet() = captureAll("3_tablet_800x1280")

    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    @Test fun tabletLandscape() = captureAll("4_tabland_1280x800")

    @Config(qualifiers = "w411dp-h891dp-xxhdpi", fontScale = 2.0f)
    @Test fun hugeFontScale() = captureAll("5_font2x_411x891")
}
