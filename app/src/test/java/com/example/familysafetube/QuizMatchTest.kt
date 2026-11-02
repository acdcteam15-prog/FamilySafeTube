package com.example.familysafetube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuizMatchTest {
    private fun obj(file: String, nameAr: String, nameEn: String, sound: String? = null) =
        QuizCard("animals_$file", "/p/$file.png", nameEn, nameAr, nameEn, true, false, sound, null, null, null)

    private val objects = listOf(
        obj("banana", "موزة", "Banana"), obj("lion", "أسد", "Lion"), obj("cat", "قطة", "Cat", "cat"),
        obj("dog", "كلب", "Dog", "dog"), obj("cow", "بقرة", "Cow", "cow")
    )
    private val letters = QuizDecks.deck(DeckId.AR_LETTERS) + QuizDecks.deck(DeckId.EN_LETTERS)
    private val nums = QuizDecks.deck(DeckId.NUMBERS)
    private val counts = QuizDecks.deck(DeckId.COUNTING)

    private fun build(kind: MatchKind, n: Int, seed: Int = 1, lang: QuizLang = QuizLang.AR) =
        MatchBuilder.build(kind, objects, letters, nums, counts, lang, n, Random(seed))

    @Test fun pairsPerLevel() {
        assertEquals(2, MatchBuilder.pairCount(QuizLevel.BABY)); assertEquals(3, MatchBuilder.pairCount(QuizLevel.TODDLER)); assertEquals(4, MatchBuilder.pairCount(QuizLevel.OLDER))
    }

    @Test fun letterObject_pairsAreRealFirstLetters_andDistinct() {
        repeat(100) { seed ->
            val b = build(MatchKind.LETTER_OBJECT, 3, seed)!!
            assertEquals(3, b.pairs.size)
            assertEquals(3, b.pairs.map { it.left.id }.distinct().size)
            for (p in b.pairs) assertEquals(p.left.id, Phonics.letterCardId(QuizLang.AR, Phonics.arabicInitialKey(p.right.nameAr)!!))
        }
    }

    @Test fun numberCount_digitMatchesItsStars() {
        val b = build(MatchKind.NUMBER_COUNT, 3)!!
        for (p in b.pairs) {
            assertEquals((p.left.art as CardArt.Glyph).text.toInt(), (p.right.art as CardArt.Dots).count)
        }
        assertEquals(3, b.pairs.map { it.id }.distinct().size)
    }

    @Test fun soundPicture_onlyForCardsWithASound_andTheTileCarriesThatSound() {
        val b = build(MatchKind.SOUND_PICTURE, 3)!!
        assertEquals(3, b.pairs.size)
        for (p in b.pairs) {
            assertTrue(p.right.hasSound); assertTrue(p.left.hasSound)
            assertEquals(p.right.builtInSoundKey, p.left.builtInSoundKey)
            assertEquals("🔊", (p.left.art as CardArt.Glyph).text)
        }
    }

    @Test fun orders_areShuffledPermutationsOfThePairIds() {
        val b = build(MatchKind.NUMBER_COUNT, 4)!!
        val ids = b.pairs.map { it.id }.toSet()
        assertEquals(ids, b.leftOrder.toSet()); assertEquals(ids, b.rightOrder.toSet())
        assertEquals(4, b.leftOrder.size); assertEquals(4, b.rightOrder.size)
        val differ = (0 until 50).any { s -> build(MatchKind.NUMBER_COUNT, 4, s)!!.let { it.leftOrder != it.rightOrder } }
        assertTrue("the two sides should not always be in the same order", differ)
    }

    @Test fun notEnoughMaterial_givesNull() {
        assertNull(MatchBuilder.build(MatchKind.SOUND_PICTURE, listOf(obj("cat", "قطة", "Cat", "cat")), letters, nums, counts, QuizLang.AR, 3))
        assertNull(MatchBuilder.build(MatchKind.LETTER_OBJECT, emptyList(), letters, nums, counts, QuizLang.AR, 3))
        assertNull(MatchBuilder.build(MatchKind.LETTER_OBJECT, objects, emptyList(), nums, counts, QuizLang.AR, 3))
    }

    @Test fun availableKinds() {
        assertEquals(MatchKind.values().toList(), MatchBuilder.possibleKinds(objects, letters, QuizLang.AR))
        assertEquals(listOf(MatchKind.NUMBER_COUNT), MatchBuilder.possibleKinds(emptyList(), emptyList(), QuizLang.AR))
        assertTrue(MatchBuilder.isPlayable(emptyList(), emptyList(), QuizLang.AR))          // numbers need no pictures
    }

    // ── the rules ──
    private fun state() = MatchState(build(MatchKind.NUMBER_COUNT, 3, 7)!!)

    @Test fun firstTapSelects() {
        val s = state(); val id = s.board.pairs[0].id
        val (n, e) = s.tap(MatchSide.LEFT, id)
        assertEquals(MatchEvent.SELECTED, e); assertEquals(MatchSide.LEFT to id, n.selected)
    }

    @Test fun matchingPair_clearsBoth() {
        val s = state(); val id = s.board.pairs[0].id
        val (a, _) = s.tap(MatchSide.LEFT, id)
        val (b, e) = a.tap(MatchSide.RIGHT, id)
        assertEquals(MatchEvent.MATCHED, e); assertEquals(setOf(id), b.solved); assertNull(b.selected); assertEquals(0, b.mistakes)
    }

    @Test fun matchingWorksFromEitherSideFirst() {
        val s = state(); val id = s.board.pairs[1].id
        val (a, _) = s.tap(MatchSide.RIGHT, id)
        assertEquals(MatchEvent.MATCHED, a.tap(MatchSide.LEFT, id).second)
    }

    @Test fun wrongPartner_countsAMistake_andClearsTheSelection() {
        val s = state(); val (x, y) = s.board.pairs[0].id to s.board.pairs[1].id
        val (a, _) = s.tap(MatchSide.LEFT, x)
        val (b, e) = a.tap(MatchSide.RIGHT, y)
        assertEquals(MatchEvent.WRONG, e); assertNull(b.selected); assertEquals(1, b.mistakes); assertTrue(b.solved.isEmpty())
    }

    @Test fun tappingTheSameSideSwitchesTheSelection() {
        val s = state(); val (x, y) = s.board.pairs[0].id to s.board.pairs[1].id
        val (a, _) = s.tap(MatchSide.LEFT, x)
        val (b, e) = a.tap(MatchSide.LEFT, y)
        assertEquals(MatchEvent.SELECTED, e); assertEquals(MatchSide.LEFT to y, b.selected); assertEquals(0, b.mistakes)
    }

    @Test fun solvedTilesAndUnknownIdsAreIgnored() {
        var s = state(); val id = s.board.pairs[0].id
        s = s.tap(MatchSide.LEFT, id).first.tap(MatchSide.RIGHT, id).first
        assertEquals(MatchEvent.IGNORED, s.tap(MatchSide.LEFT, id).second)
        assertEquals(MatchEvent.IGNORED, s.tap(MatchSide.LEFT, "nope").second)
    }

    @Test fun finishingTheBoardReportsDone() {
        var s = state(); var last = MatchEvent.IGNORED
        for (p in s.board.pairs) {
            s = s.tap(MatchSide.LEFT, p.id).first
            val r = s.tap(MatchSide.RIGHT, p.id); s = r.first; last = r.second
        }
        assertTrue(s.isDone); assertEquals(MatchEvent.DONE, last)
        assertEquals(MatchEvent.IGNORED, s.tap(MatchSide.LEFT, s.board.pairs[0].id).second)      // nothing more to do
        assertFalse(state().isDone)
    }

    @Test fun mistakesNeverUndoProgress() {
        var s = state(); val ids = s.board.pairs.map { it.id }
        s = s.tap(MatchSide.LEFT, ids[0]).first.tap(MatchSide.RIGHT, ids[0]).first
        s = s.tap(MatchSide.LEFT, ids[1]).first.tap(MatchSide.RIGHT, ids[2]).first             // wrong
        assertEquals(setOf(ids[0]), s.solved); assertEquals(1, s.mistakes)
    }
}
