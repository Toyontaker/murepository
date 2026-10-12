package com.toyontaker.manabikko

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.pow

/** 効果音(res/raw の wav)を ならす。 */
class Sfx(context: Context) {
    enum class Sound { Pop, Ding, Correct, Wrong, Fanfare, Swish }

    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val ids = mapOf(
        Sound.Pop to pool.load(context, R.raw.pop, 1),
        Sound.Ding to pool.load(context, R.raw.ding, 1),
        Sound.Correct to pool.load(context, R.raw.correct, 1),
        Sound.Wrong to pool.load(context, R.raw.wrong, 1),
        Sound.Fanfare to pool.load(context, R.raw.fanfare, 1),
        Sound.Swish to pool.load(context, R.raw.swish, 1),
    )

    fun play(sound: Sound, rate: Float = 1f, volume: Float = 0.7f) {
        ids[sound]?.let { pool.play(it, volume, volume, 1, 0, rate.coerceIn(0.5f, 2f)) }
    }

    /** かぞえるときの音。1,2,3… と ドレミファソラシド… のように 高くなる。 */
    fun count(n: Int) {
        val major = intArrayOf(0, 2, 4, 5, 7, 9, 11, 12, 14, 16)
        val semitone = major[(n - 1).coerceIn(0, major.lastIndex)]
        play(Sound.Ding, rate = 2.0.pow(semitone / 12.0).toFloat())
    }

    fun release() = pool.release()
}

val LocalSfx = staticCompositionLocalOf<Sfx> { error("Sfx not provided") }
