package com.example.familysafetube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuizEngineTest {
    private fun card(id: String, label: String = id.substringAfter('_'), enabled: Boolean = true) =
        QuizCard(id, "/p/$id.png", label.replaceFirstChar { it.uppercase() }, label, label, enabled, false, null, null, null, null)

    private val animals = listOf("lion", "tiger", "bear", "zebra", "fox", "wolf").map { card("animals_$it") }
    private val fruit = listOf("apple", "banana", "mango").map { card("veg&fruits_$it") }
    private val all = animals + fruit

    @Test fun question_hasDistinctOptionsIncludingTarget() {
        repeat(300) { seed ->
            val q = QuizEngine.pick(all, emptyMap(), emptyList(), 3, Random(seed))
            assertNotNull(q); q!!
            assertEquals(3, q.options.size)
            assertEquals(3, q.options.map { it.labelEn }.distinct().size)
            assertTrue(q.target in q.options)
        }
    }

    @Test fun distractors_stayInTheTargetsCategory() {
        repeat(300) { seed ->
            val q = QuizEngine.pick(all, emptyMap(), emptyList(), 3, Random(seed))!!
            assertEquals(1, q.options.map { it.category }.distinct().size)
        }
    }

    @Test fun smallCategory_borrowsFromOthersInsteadOfFailing() {
        val cards = listOf(card("animals_lion"), card("animals_tiger"), card("veg&fruits_apple"))
        repeat(100) { seed ->
            // fruit has just one card, so a 3-option question must mix categories
            val q = QuizEngine.pick(cards, emptyMap(), emptyList(), 3, Random(seed))!!
            assertEquals(3, q.options.size)
        }
    }

    @Test fun neverTwoPicturesWithTheSameLabel() {
        val dup = listOf(card("animals_lion1", "lion"), card("animals_lion2", "lion"), card("animals_tiger"), card("animals_bear"))
        repeat(200) { seed ->
            val q = QuizEngine.pick(dup, emptyMap(), emptyList(), 3, Random(seed))!!
            assertEquals(3, q.options.map { it.labelEn.lowercase() }.distinct().size)
        }
    }

    @Test fun disabledCards_areNeverUsed() {
        val cards = animals.map { if (it.id == "animals_lion") it.copy(enabled = false) else it }
        repeat(300) { seed ->
            val q = QuizEngine.pick(cards, emptyMap(), emptyList(), 3, Random(seed))!!
            assertTrue(q.options.none { it.id == "animals_lion" })
        }
    }

    @Test fun playable_needsTwoDistinctEnabledCards() {
        assertFalse(QuizEngine.isPlayable(emptyList()))
        assertFalse(QuizEngine.isPlayable(listOf(card("animals_lion"))))
        assertFalse(QuizEngine.isPlayable(listOf(card("animals_a", "lion"), card("animals_b", "lion"))))
        assertTrue(QuizEngine.isPlayable(listOf(card("animals_lion"), card("animals_tiger"))))
        assertNull(QuizEngine.pick(listOf(card("animals_lion")), emptyMap(), emptyList(), 3))
    }

    @Test fun twoCards_giveATwoPictureQuestion() {
        val q = QuizEngine.pick(listOf(card("animals_lion"), card("animals_tiger")), emptyMap(), emptyList(), 3, Random(1))!!
        assertEquals(2, q.options.size)
    }

    @Test fun recentlyAskedCards_areSkippedWhenThereIsChoice() {
        val recent = listOf("animals_lion", "animals_tiger", "animals_bear")
        repeat(200) { seed ->
            val q = QuizEngine.pick(animals, emptyMap(), recent, 3, Random(seed))!!
            assertTrue(q.target.id !in recent)
        }
    }

    @Test fun recentExclusion_isDroppedWhenItWouldLeaveNothing() {
        val two = listOf(card("animals_lion"), card("animals_tiger"))
        assertNotNull(QuizEngine.pick(two, emptyMap(), listOf("animals_lion", "animals_tiger"), 3, Random(3)))
    }

    @Test fun missedWords_comeBackMoreOften() {
        val stats = mapOf("animals_lion" to CardStats(shown = 10, correct = 2, wrong = 8))
        var lionTarget = 0
        val n = 3000
        repeat(n) { seed ->
            if (QuizEngine.pick(animals, stats, emptyList(), 3, Random(seed))!!.target.id == "animals_lion") lionTarget++
        }
        val share = lionTarget.toDouble() / n
        assertTrue("lion share $share should be well above the uniform 1/6", share > 0.40)
    }

    @Test fun masteredWords_stillAppear_justLessOften() {
        val stats = animals.associate { it.id to CardStats(10, 10, 0) }
        assertEquals(0.3, QuizEngine.weight(stats["animals_lion"]), 0.0001)   // floor, never zero
        assertEquals(1.0, QuizEngine.weight(null), 0.0001)
        assertTrue(QuizEngine.weight(CardStats(5, 0, 5)) > QuizEngine.weight(CardStats(5, 5, 0)))
    }

    @Test fun threeMissesInARow_makeTheQuestionEasier() {
        assertEquals(3, QuizEngine.optionCount(0))
        assertEquals(3, QuizEngine.optionCount(2))
        assertEquals(2, QuizEngine.optionCount(3))
        assertEquals(2, QuizEngine.optionCount(9))
    }

    @Test fun targetPosition_variesBetweenQuestions() {
        val positions = (0 until 200).map { s ->
            val q = QuizEngine.pick(animals, emptyMap(), emptyList(), 3, Random(s))!!; q.options.indexOf(q.target)
        }.toSet()
        assertEquals(setOf(0, 1, 2), positions)
    }

    private fun withSound(c: QuizCard) = c.copy(builtInSoundKey = "dog")

    @Test fun soundOnly_neverUsedWhenChanceIsZero_orCardHasNoSound() {
        repeat(300) { seed ->
            assertEquals(QuizMode.HEAR_NAME, QuizEngine.pick(animals.map(::withSound), emptyMap(), emptyList(), 3, Random(seed), 0.0)!!.mode)
            assertEquals(QuizMode.HEAR_NAME, QuizEngine.pick(animals, emptyMap(), emptyList(), 3, Random(seed), 1.0)!!.mode)   // no sounds
        }
    }

    @Test fun soundOnly_alwaysWhenChanceIsOneAndCardHasSound() {
        repeat(200) { seed ->
            val q = QuizEngine.pick(animals.map(::withSound), emptyMap(), emptyList(), 3, Random(seed), 1.0)!!
            assertEquals(QuizMode.HEAR_SOUND, q.mode)
            assertTrue(q.target.hasSound)
        }
    }

    @Test fun soundOnly_mixesWithNameQuestionsAtTheRequestedRate() {
        val cards = animals.map(::withSound)
        val n = 4000
        val soundOnly = (0 until n).count { s -> QuizEngine.pick(cards, emptyMap(), emptyList(), 3, Random(s), 0.3)!!.mode == QuizMode.HEAR_SOUND }
        val rate = soundOnly.toDouble() / n
        assertTrue("rate $rate should be near 0.30", rate in 0.25..0.35)
    }

    @Test fun soundOnly_onlyForCardsThatHaveASound_inAMixedDeck() {
        val cards = animals.mapIndexed { i, c -> if (i % 2 == 0) withSound(c) else c }
        repeat(400) { seed ->
            val q = QuizEngine.pick(cards, emptyMap(), emptyList(), 3, Random(seed), 1.0)!!
            if (q.mode == QuizMode.HEAR_SOUND) assertTrue(q.target.hasSound)
        }
    }

    @Test fun focusWords_areAskedMoreOften() {
        val cards = animals.map { if (it.id == "animals_lion") it.copy(focus = true) else it }
        var lion = 0; val n = 3000
        repeat(n) { s -> if (QuizEngine.pick(cards, emptyMap(), emptyList(), 3, Random(s))!!.target.id == "animals_lion") lion++ }
        val share = lion.toDouble() / n            // weights: 3 vs 1x5 => 3/8 = 0.375, versus 1/6 = 0.167 without focus
        assertTrue("focus share $share", share in 0.32..0.43)
    }

    @Test fun focusAndMistakes_stack() {
        val cards = animals.map { if (it.id == "animals_lion") it.copy(focus = true) else it }
        val stats = mapOf("animals_lion" to CardStats(10, 2, 8))
        var lion = 0; val n = 2000
        repeat(n) { s -> if (QuizEngine.pick(cards, stats, emptyList(), 3, Random(s))!!.target.id == "animals_lion") lion++ }
        assertTrue("share ${lion.toDouble() / n}", lion.toDouble() / n > 0.6)
    }

    @Test fun stats_recordAnswers() {
        val s = CardStats().record(true).record(false).record(true)
        assertEquals(CardStats(shown = 3, correct = 2, wrong = 1), s)
    }
}
