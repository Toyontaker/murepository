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
    }

    @Test fun questions() {
        assertEquals("いくつ", Counter.Ko.question)
        assertEquals("なんぼん", Counter.Hon.question)
        assertEquals("なんびき", Counter.Hiki.question)
        assertEquals("なんだい", Counter.Dai.question)
        assertEquals("なんわ", Counter.Wa.question)
    }

    @Test fun everyCounterHasTenReadings() {
        Counter.entries.forEach { assertEquals(it.name, 10, it.readings.size) }
    }
}
