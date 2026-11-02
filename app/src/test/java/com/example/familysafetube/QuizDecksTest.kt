package com.example.familysafetube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizDecksTest {
    @Test fun deckSizes() {
        assertEquals(28, QuizDecks.deck(DeckId.AR_LETTERS).size)
        assertEquals(26, QuizDecks.deck(DeckId.EN_LETTERS).size)
        assertEquals(10, QuizDecks.deck(DeckId.NUMBERS).size)
        assertEquals(5, QuizDecks.deck(DeckId.COUNTING).size)
        for (d in DeckId.values()) assertEquals(QuizDecks.size(d), QuizDecks.deck(d).size)
    }

    @Test fun idsAndLabels_areUniqueAcrossAllDecks() {
        val all = DeckId.values().flatMap { QuizDecks.deck(it) }
        assertEquals("duplicate ids", all.size, all.map { it.id }.toSet().size)
        // labelEn is what the engine de-duplicates on: a clash would silently drop a card
        assertEquals("duplicate labels", all.size, all.map { it.labelEn.lowercase() }.toSet().size)
    }

    @Test fun everyCardHasSpokenNamesInBothLanguages() {
        for (c in DeckId.values().flatMap { QuizDecks.deck(it) }) {
            assertTrue("${c.id} ar", c.nameAr.isNotBlank()); assertTrue("${c.id} en", c.nameEn.isNotBlank())
            assertFalse(c.needsArabicName); assertTrue(c.enabled)
        }
    }

    @Test fun idCategoryIsTheDeck() {
        for (d in DeckId.values()) assertTrue(QuizDecks.deck(d).all { it.category == d.key })
    }

    @Test fun artMatchesTheDeck() {
        assertTrue(QuizDecks.deck(DeckId.AR_LETTERS).all { it.art is CardArt.Glyph })
        assertTrue(QuizDecks.deck(DeckId.NUMBERS).all { it.art is CardArt.Glyph })
        val dots = QuizDecks.deck(DeckId.COUNTING).map { (it.art as CardArt.Dots).count }
        assertEquals(listOf(1, 2, 3, 4, 5), dots)
    }

    @Test fun glyphsAreWhatTheyShould() {
        val ar = QuizDecks.deck(DeckId.AR_LETTERS)
        assertEquals("أ", (ar.first().art as CardArt.Glyph).text); assertEquals("ألف", ar.first().nameAr)
        assertEquals("ي", (ar.last().art as CardArt.Glyph).text)
        val en = QuizDecks.deck(DeckId.EN_LETTERS)
        assertEquals("A", (en.first().art as CardArt.Glyph).text); assertEquals("Z", (en.last().art as CardArt.Glyph).text)
        val nums = QuizDecks.deck(DeckId.NUMBERS)
        assertEquals("3", (nums[2].art as CardArt.Glyph).text); assertEquals("ثلاثة", nums[2].nameAr); assertEquals("Three", nums[2].nameEn)
    }

    @Test fun colorsStayInRange() {
        for (c in DeckId.values().flatMap { QuizDecks.deck(it) }) {
            val i = when (val a = c.art) { is CardArt.Glyph -> a.colorIndex; is CardArt.Dots -> a.colorIndex; else -> 0 }
            assertTrue(i in 0 until QuizDecks.COLOR_COUNT)
        }
    }

    @Test fun parseAndFormat() {
        assertEquals(setOf(DeckId.NUMBERS, DeckId.AR_LETTERS), QuizDecks.parse("numbers, arletters,bogus"))
        assertTrue(QuizDecks.parse(null).isEmpty()); assertTrue(QuizDecks.parse("").isEmpty())
        assertEquals("arletters,numbers", QuizDecks.format(setOf(DeckId.NUMBERS, DeckId.AR_LETTERS)))
        assertEquals(setOf(DeckId.COUNTING), QuizDecks.parse(QuizDecks.format(setOf(DeckId.COUNTING))))
    }

    @Test fun onlyEnabledDecksProduceCards_andOverridesApply() {
        assertTrue(QuizDecks.cards(emptySet(), emptyMap(), 0L).isEmpty())
        val cards = QuizDecks.cards(
            setOf(DeckId.NUMBERS), mapOf("numbers_2" to CardOverride(enabled = false, voiceArFile = "v.snd", focusSinceDay = 100L)), today = 102L
        )
        assertEquals(10, cards.size)
        val two = cards.first { it.id == "numbers_2" }
        assertFalse(two.enabled); assertEquals("v.snd", two.voiceArFile); assertTrue(two.focus)
        assertFalse(cards.first { it.id == "numbers_1" }.focus)
    }

    @Test fun decksAreQuizPlayableOnTheirOwn() {
        assertTrue(QuizEngine.isPlayable(QuizDecks.deck(DeckId.COUNTING)))
        val q = QuizEngine.pick(QuizDecks.deck(DeckId.NUMBERS), emptyMap(), emptyList(), 3)!!
        assertEquals(3, q.options.size); assertEquals(1, q.options.map { it.category }.distinct().size)
    }
}
