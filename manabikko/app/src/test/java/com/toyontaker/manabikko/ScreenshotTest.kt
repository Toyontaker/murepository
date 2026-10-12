package com.toyontaker.manabikko

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.toyontaker.manabikko.ui.CountingScreen
import com.toyontaker.manabikko.ui.HiraganaScreen
import com.toyontaker.manabikko.ui.HomeScreen
import com.toyontaker.manabikko.ui.ManabikkoTheme
import com.toyontaker.manabikko.ui.Palette
import com.toyontaker.manabikko.ui.QuizScreen
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

    private fun shot(name: String, content: @Composable () -> Unit) {
        val speaker = Speaker(ApplicationProvider.getApplicationContext())
        rule.setContent {
            ManabikkoTheme {
                CompositionLocalProvider(LocalSpeaker provides speaker) {
                    Box(Modifier.fillMaxSize().background(Palette.Background)) { content() }
                }
            }
        }
        rule.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Test fun home() = shot("home") { HomeScreen(onOpen = {}) }
    @Test fun hiragana() = shot("hiragana") { HiraganaScreen(onHome = {}) }
    @Test fun counting() = shot("counting") { CountingScreen(onHome = {}) }
    @Test fun tracing() = shot("tracing") { TracingScreen(onHome = {}) }
    @Test fun quiz() = shot("quiz") { QuizScreen(onHome = {}) }
}
