package com.example.familysafetube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuizLevelTest {
    private fun summary(answers: Int, accuracy: Int?, learned: Int): ProgressSummary {
        val words = (1..learned).map { WordProgress(QuizCard("a_$it", "/p", "w$it", "w$it", "w$it", true, false, null, null, null, null), CardStats(5, 5, 0), WordLevel.LEARNED) }
        return ProgressSummary(words, emptyList(), emptyList(), 0, answers, accuracy)
    }

    @Test fun codes() {
        assertEquals(QuizLevel.BABY, QuizLevel.fromCode("baby")); assertEquals(QuizLevel.OLDER, QuizLevel.fromCode("older"))
        assertEquals(QuizLevel.TODDLER, QuizLevel.fromCode(null)); assertEquals(QuizLevel.TODDLER, QuizLevel.fromCode("zzz"))
    }

    @Test fun levelsGetHarder() {
        assertEquals(2, QuizLevel.BABY.options); assertEquals(0.0, QuizLevel.BABY.soundOnlyChance, 0.0)
        assertEquals(2, QuizLevel.TODDLER.options); assertEquals(3, QuizLevel.OLDER.options)       // two stacked pictures, three for Older
        assertEquals(true, QuizLevel.OLDER.soundOnlyChance > QuizLevel.TODDLER.soundOnlyChance)
        assertEquals(true, QuizLevel.BABY.idleRepeatMs > QuizLevel.TODDLER.idleRepeatMs)
    }

    @Test fun optionCountPerLevel() {
        for (m in listOf(0, 1, 5, 50)) assertEquals(2, QuizEngine.optionCount(m, QuizLevel.BABY))
        for (m in listOf(0, 3, 50)) assertEquals(2, QuizEngine.optionCount(m, QuizLevel.TODDLER))   // always two
        assertEquals(3, QuizEngine.optionCount(3, QuizLevel.OLDER)); assertEquals(2, QuizEngine.optionCount(4, QuizLevel.OLDER))
    }

    @Test fun advisor_suggestsHarderWhenDoingVeryWell() {
        assertEquals(QuizLevel.TODDLER, LevelAdvisor.suggest(QuizLevel.BABY, summary(30, 92, 6)))
        assertEquals(QuizLevel.OLDER, LevelAdvisor.suggest(QuizLevel.TODDLER, summary(30, 92, 6)))
        assertNull(LevelAdvisor.suggest(QuizLevel.OLDER, summary(30, 99, 9)))          // already the top
    }

    @Test fun advisor_needsEnoughEvidence() {
        assertNull(LevelAdvisor.suggest(QuizLevel.BABY, summary(10, 100, 6)))           // too few answers
        assertNull(LevelAdvisor.suggest(QuizLevel.BABY, summary(30, 92, 2)))            // too few learned words
        assertNull(LevelAdvisor.suggest(QuizLevel.BABY, summary(30, 80, 6)))            // not good enough yet
        assertNull(LevelAdvisor.suggest(QuizLevel.BABY, summary(0, null, 0)))
    }

    @Test fun advisor_suggestsEasierWhenStruggling() {
        assertEquals(QuizLevel.TODDLER, LevelAdvisor.suggest(QuizLevel.OLDER, summary(20, 30, 0)))
        assertNull(LevelAdvisor.suggest(QuizLevel.BABY, summary(40, 10, 0)))            // nothing easier than Baby
        assertNull(LevelAdvisor.suggest(QuizLevel.OLDER, summary(10, 20, 0)))           // too few answers to judge
    }
}
