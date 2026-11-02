package com.example.familysafetube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizProgressTest {
    private fun card(id: String, enabled: Boolean = true) =
        QuizCard(id, "/p/$id.png", id.substringAfter('_'), id, id, enabled, false, null, null, null, null)

    @Test fun level_newUntilAnswered() {
        assertEquals(WordLevel.NEW, QuizProgress.level(null))
        assertEquals(WordLevel.NEW, QuizProgress.level(CardStats()))
    }

    @Test fun level_learnedNeedsEnoughCorrectAndGoodAccuracy() {
        assertEquals(WordLevel.LEARNED, QuizProgress.level(CardStats(shown = 4, correct = 3, wrong = 1)))
        assertEquals(WordLevel.LEARNED, QuizProgress.level(CardStats(shown = 10, correct = 10, wrong = 0)))
        // 2 correct out of 2 is not enough evidence yet
        assertEquals(WordLevel.PRACTICING, QuizProgress.level(CardStats(shown = 2, correct = 2, wrong = 0)))
        // accuracy just under 75%
        assertEquals(WordLevel.PRACTICING, QuizProgress.level(CardStats(shown = 8, correct = 5, wrong = 3)))
    }

    @Test fun level_strugglingNeedsEnoughAttemptsAndLowAccuracy() {
        assertEquals(WordLevel.STRUGGLING, QuizProgress.level(CardStats(shown = 4, correct = 1, wrong = 3)))
        assertEquals(WordLevel.PRACTICING, QuizProgress.level(CardStats(shown = 2, correct = 0, wrong = 2)))   // too early to say
        assertEquals(WordLevel.PRACTICING, QuizProgress.level(CardStats(shown = 4, correct = 2, wrong = 2)))   // exactly 50%: not struggling
    }

    @Test fun summary_groupsAndOrdersWords() {
        val cards = listOf(card("a_lion"), card("a_dog"), card("a_cat"), card("a_cow"), card("a_pig"))
        val stats = mapOf(
            "a_lion" to CardStats(10, 9, 1),
            "a_dog" to CardStats(5, 5, 0),
            "a_cat" to CardStats(6, 1, 5),
            "a_cow" to CardStats(4, 1, 3),
            "a_pig" to CardStats(2, 1, 1)
        )
        val s = QuizProgress.summarize(cards, stats)
        assertEquals(listOf("a_lion", "a_dog"), s.learned.map { it.card.id })        // most correct first
        assertEquals(listOf("a_cat", "a_cow"), s.struggling.map { it.card.id })      // most wrong first
        assertEquals(listOf("a_pig"), s.practicing.map { it.card.id })
        assertEquals(0, s.newCount)
        assertEquals(27, s.totalAnswers)
        assertEquals(Math.round(100.0 * 17 / 27).toInt(), s.accuracyPercent)
    }

    @Test fun summary_ignoresDisabledCards() {
        val s = QuizProgress.summarize(
            listOf(card("a_lion"), card("a_dog", enabled = false)),
            mapOf("a_dog" to CardStats(9, 9, 0), "a_lion" to CardStats(1, 1, 0))
        )
        assertEquals(1, s.totalAnswers)
        assertTrue(s.learned.isEmpty())
    }

    @Test fun summary_beforeAnyAnswer() {
        val s = QuizProgress.summarize(listOf(card("a_lion"), card("a_dog")), emptyMap())
        assertEquals(2, s.newCount)
        assertEquals(0, s.totalAnswers)
        assertNull(s.accuracyPercent)
        assertFalse(s.learned.isNotEmpty() || s.struggling.isNotEmpty())
    }

    @Test fun summary_statsForMissingCardsAreIgnored() {
        val s = QuizProgress.summarize(listOf(card("a_lion")), mapOf("gone_card" to CardStats(50, 50, 0)))
        assertEquals(0, s.totalAnswers)
    }

    // ── RoundTracker ──
    @Test fun round_completesEveryFiveCorrectAnswersAndRestarts() {
        val r = RoundTracker(5)
        repeat(4) { assertFalse(r.correctAnswer()) }
        assertEquals(4, r.correctInRound)
        assertTrue(r.correctAnswer())
        assertEquals(0, r.correctInRound)
        assertEquals(1, r.roundsCompleted)
        repeat(4) { assertFalse(r.correctAnswer()) }
        assertTrue(r.correctAnswer())
        assertEquals(2, r.roundsCompleted)
    }

    @Test fun round_defaultSizeMatchesTheSharedConstant() {
        val r = RoundTracker()
        repeat(QuizRules.SET_SIZE - 1) { assertFalse(r.correctAnswer()) }
        assertTrue(r.correctAnswer())
    }
}
