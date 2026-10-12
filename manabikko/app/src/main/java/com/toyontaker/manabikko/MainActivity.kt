package com.toyontaker.manabikko

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.toyontaker.manabikko.ui.CountingScreen
import com.toyontaker.manabikko.ui.HiraganaScreen
import com.toyontaker.manabikko.ui.HomeScreen
import com.toyontaker.manabikko.ui.ManabikkoTheme
import com.toyontaker.manabikko.ui.Palette
import com.toyontaker.manabikko.ui.QuizScreen
import com.toyontaker.manabikko.ui.TracingScreen

enum class Screen { Home, Hiragana, Counting, Tracing, Quiz }

class MainActivity : ComponentActivity() {
    private lateinit var speaker: Speaker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // あそんでいる あいだは 画面を つけたままにする
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        speaker = Speaker(this)

        setContent {
            ManabikkoTheme {
                CompositionLocalProvider(LocalSpeaker provides speaker) {
                    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
                    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }
                    val goHome = { screen = Screen.Home }

                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Palette.Background)
                            .safeDrawingPadding(),
                    ) {
                        AnimatedContent(
                            targetState = screen,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "screen",
                        ) { current ->
                            when (current) {
                                Screen.Home -> HomeScreen(onOpen = { screen = it })
                                Screen.Hiragana -> HiraganaScreen(onHome = goHome)
                                Screen.Counting -> CountingScreen(onHome = goHome)
                                Screen.Tracing -> TracingScreen(onHome = goHome)
                                Screen.Quiz -> QuizScreen(onHome = goHome)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        speaker.shutdown()
        super.onDestroy()
    }
}
