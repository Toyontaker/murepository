package com.toyontaker.manabikko.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CounterTest {
    @Test fun labels() {
        assertEquals("3こ", Counter.Ko.label(3))
        assertEquals("1ぽん", Counter.Hon.label(1))
        assertEquals("2ほん", Counter.Hon.label(2))
        assertEquals("3ぼん", Counter.Hon.label(3))
        assertEquals("6ぴき", Counter.Hiki.label(6))
        assertEquals("3びき", Counter.Hiki.label(3))
        assertEquals("10だい", Counter.Dai.label(10))
        assertEquals("4わ", Counter.Wa.label(4))
        assertEquals("1さつ", Counter.Satsu.label(1))
        assertEquals("3ぞく", Counter.Soku.label(3))
        assertEquals("8そく", Counter.Soku.label(8))
        assertEquals("2たま", Counter.Tama.label(2))
        assertEquals("5まい", Counter.Mai.label(5))
        assertEquals("1とう", Counter.Tou.label(1))
    }

    @Test fun speech() {
        assertEquals("3本", Counter.Hon.say(3))
        assertEquals("2冊", Counter.Satsu.say(2))
        // 「3玉」は エンジンが 読みまちがえやすいので かなで 読ませる
        assertEquals("みたま", Counter.Tama.say(3))
    }

    @Test fun questions() {
        assertEquals("いくつ", Counter.Ko.question)
        assertEquals("なんぼん", Counter.Hon.question)
        assertEquals("なんびき", Counter.Hiki.question)
        assertEquals("なんだい", Counter.Dai.question)
        assertEquals("なんわ", Counter.Wa.question)
        assertEquals("なんさつ", Counter.Satsu.question)
        assertEquals("なんぞく", Counter.Soku.question)
        assertEquals("何足", Counter.Soku.sayQuestion)
    }

    @Test fun everyCounterHasTenReadings() {
        Counter.entries.forEach { assertEquals(it.name, 10, it.readings.size) }
    }
}
