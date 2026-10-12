package com.toyontaker.manabikko

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

/**
 * 端末の音声読み上げ(TextToSpeech)を つかって、日本語で しゃべる。
 *
 * 読み上げエンジンは 文字の 書きかたから アクセントを きめるので、しゃべる 文は
 * 画面に だす ひらがな ではなく、漢字まじりで 空白なしの ふつうの 文で わたすこと
 * (例:「かめ」より「亀」、「なにで あそぶ?」より「何して遊ぶ?」)。
 */
class Speaker(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)

    /** 日本語の音声が つかえるとき true。 */
    var ready by mutableStateOf(false)
        private set

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val result = tts.setLanguage(Locale.JAPAN)
        ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        if (!ready) return
        bestJapaneseVoice()?.let { tts.voice = it }
        // ピッチを 上げすぎると 抑揚が 不自然に なるので ひかえめに
        tts.setSpeechRate(0.9f)
        tts.setPitch(1.05f)
    }

    /** 端末に 入っている 日本語の 声のうち、オフラインで いちばん 品質の よいもの。 */
    private fun bestJapaneseVoice(): Voice? = runCatching {
        tts.voices
            ?.filter { it.locale.language == "ja" }
            ?.filter { !it.isNetworkConnectionRequired }
            ?.filter { TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features }
            ?.maxByOrNull { it.quality }
    }.getOrNull()

    /** parts を じゅんばんに、あいだに すこし 間を あけて しゃべる。 */
    fun speak(vararg parts: String) {
        if (!ready || parts.isEmpty()) return
        parts.forEachIndexed { i, text ->
            if (i > 0) tts.playSilentUtterance(PAUSE_MILLIS, TextToSpeech.QUEUE_ADD, "pause$i")
            tts.speak(text, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "part$i")
        }
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }

    private companion object {
        const val PAUSE_MILLIS = 300L
    }
}

val LocalSpeaker = staticCompositionLocalOf<Speaker> { error("Speaker not provided") }
