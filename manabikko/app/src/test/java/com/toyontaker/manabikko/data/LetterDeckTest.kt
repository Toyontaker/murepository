package com.toyontaker.manabikko.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LetterDeckTest {
    @Test fun sequentialGoesInOrderAndWraps() {
        val deck = LetterDeck(Hiragana, LetterOrder.Sequential)
        assertEquals("あ", deck.next(null).char)
        assertEquals("い", deck.next(Hiragana.all[0]).char)
        assertEquals("あ", deck.next(Hiragana.all.last()).char)
        assertEquals("ん", deck.previous(Hiragana.all[0]).char)
    }

    @Test fun allRandomShowsEveryLetterBeforeRepeating() {
        val deck = LetterDeck(Hiragana, LetterOrder.AllRandom, Random(1))
        var cur: Kana? = null
        val seen = (1..Hiragana.all.size).map { deck.next(cur).also { cur = it }.char }
        assertEquals(Hiragana.all.map { it.char }.toSet(), seen.toSet())
    }

    @Test fun neverSameLetterTwiceInARow() {
        for (order in listOf(LetterOrder.AllRandom, LetterOrder.RowRandom(1), LetterOrder.RowRandom(7))) {
            val deck = LetterDeck(Katakana, order, Random(42))
            var cur = deck.next(null)
            repeat(300) {
                val n = deck.next(cur)
                assertNotEquals("$order", cur, n)
                cur = n
            }
        }
    }

    @Test fun rowRandomStaysInRow() {
        val deck = LetterDeck(Hiragana, LetterOrder.RowRandom(1), Random(7))
        var cur: Kana? = null
        val seen = (1..50).map { deck.next(cur).also { cur = it }.char }.toSet()
        assertEquals(setOf("か", "き", "く", "け", "こ"), seen)
    }

    @Test fun rowWithGapsSkipsEmptyCells() {
        // や行(や・ゆ・よ)
        val deck = LetterDeck(Hiragana, LetterOrder.RowRandom(7), Random(3))
        var cur: Kana? = null
        val seen = (1..30).map { deck.next(cur).also { cur = it }.char }.toSet()
        assertEquals(setOf("や", "ゆ", "よ"), seen)
    }

    @Test fun encodeDecodeRoundTrip() {
        for (o in listOf(LetterOrder.Sequential, LetterOrder.AllRandom, LetterOrder.RowRandom(4))) {
            assertEquals(o, LetterOrder.decode(o.encode()))
        }
        assertTrue(LetterOrder.decode(null) == LetterOrder.Sequential)
        assertTrue(LetterOrder.decode("row:x") == LetterOrder.Sequential)
    }
}
