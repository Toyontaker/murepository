package com.toyontaker.manabikko

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

/** 端末の音声読み上げ(TextToSpeech)を つかって、日本語で しゃべる。 */
class Speaker(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)

    /** 日本語の音声が つかえるとき true。 */
    var ready by mutableStateOf(false)
        private set

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val result = tts.setLanguage(Locale.JAPAN)
        ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        // 小さい子にも ききとりやすいように、ゆっくり・すこし高めに
        tts.setSpeechRate(0.8f)
        tts.setPitch(1.15f)
    }

    fun speak(text: String) {
        if (ready) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "manabikko")
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}

val LocalSpeaker = staticCompositionLocalOf<Speaker> { error("Speaker not provided") }
