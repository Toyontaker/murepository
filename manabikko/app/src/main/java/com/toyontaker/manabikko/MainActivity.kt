package com.toyontaker.manabikko

import android.app.ActivityManager
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.toyontaker.manabikko.data.Hiragana
import com.toyontaker.manabikko.data.Katakana
import com.toyontaker.manabikko.ui.AboutScreen
import com.toyontaker.manabikko.ui.CountingScreen
import com.toyontaker.manabikko.ui.HomeScreen
import com.toyontaker.manabikko.ui.KanaScreen
import com.toyontaker.manabikko.ui.ManabikkoTheme
import com.toyontaker.manabikko.ui.Palette
import com.toyontaker.manabikko.ui.QuizScreen
import com.toyontaker.manabikko.ui.TracingScreen

enum class Screen { Home, Hiragana, Katakana, Counting, Tracing, Quiz, About }

class MainActivity : ComponentActivity() {
    private lateinit var speaker: Speaker
    private lateinit var sfx: Sfx
    private var locked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // あそんでいる あいだは 画面を つけたままにする
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        speaker = Speaker(this)
        sfx = Sfx(this)

        setContent {
            ManabikkoTheme {
                CompositionLocalProvider(LocalSpeaker provides speaker, LocalSfx provides sfx) {
                    var screen by rememberSaveable { mutableStateOf(Screen.Home) }
                    // ホームでは もどるボタンを むしして、うっかり アプリが とじないようにする
                    BackHandler {
                        if (screen != Screen.Home) screen = Screen.Home
                    }
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
                                Screen.Home -> HomeScreen(
                                    onOpen = { screen = it },
                                    locked = locked,
                                    onToggleLock = ::toggleLock,
                                    onAbout = { screen = Screen.About },
                                )
                                Screen.About -> AboutScreen(onHome = goHome, speechReady = speaker.ready)
                                Screen.Hiragana -> KanaScreen("ひらがな", Hiragana, Palette.Red, onHome = goHome)
                                Screen.Katakana -> KanaScreen("カタカナ", Katakana, Palette.Orange, onHome = goHome)
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

    /** ステータスバーと ナビゲーションバーを かくして 全画面にする。 */
    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onResume() {
        super.onResume()
        refreshLockState()
    }

    override fun onStop() {
        // ほかの アプリに うつったら しゃべるのを やめる
        speaker.stop()
        super.onStop()
    }

    private fun refreshLockState() {
        val am = getSystemService(ActivityManager::class.java)
        locked = am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    /**
     * 「画面の固定」で このアプリから でられないようにする / 解除する。
     * 端末の 設定 で「アプリの固定」を オンに しておく必要がある。
     */
    private fun toggleLock() {
        refreshLockState()
        if (locked) {
            stopLockTask()
            locked = false
        } else {
            try {
                startLockTask()
                locked = true
            } catch (_: Exception) {
                locked = false
            }
            // 端末の 設定で「アプリ固定」が オフ などで 固定されなかった ときに 🔒 を けす
            window.decorView.postDelayed({ refreshLockState() }, 1500)
        }
    }

    override fun onDestroy() {
        speaker.shutdown()
        sfx.release()
        super.onDestroy()
    }
}
