package com.example.familysafetube

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** In-memory [PrefsStore] with the same "apply block to a copy" semantics as the real one. */
class FakePrefsStore : PrefsStore {
    var current: Preferences = emptyPreferences()
    override fun snapshot(): Preferences = current
    override fun edit(block: (MutablePreferences) -> Unit) {
        current = current.toMutablePreferences().also(block).toPreferences()
    }
}

class UsageRepositoryTest {
    private var day = LocalDate.of(2026, 10, 6)
    private val store = FakePrefsStore()
    private val repo = UsageRepository(store) { day }

    @Test fun record_accumulatesPerCategoryAndTotals() {
        repo.record("youtube", 2.5f)
        repo.record("youtube", 1.5f)
        repo.record("offline_videos", 3f)
        assertEquals(4f, repo.categoryMinutes("youtube"), 0.001f)
        assertEquals(3f, repo.categoryMinutes("offline_videos"), 0.001f)
        assertEquals(7f, repo.totalToday(), 0.001f)
        assertEquals(setOf("youtube", "offline_videos"), repo.categoriesToday().keys)
    }

    @Test fun record_ignoresZeroAndNegative() {
        repo.record("images", 0f)
        repo.record("images", -2f)
        assertEquals(0f, repo.totalToday(), 0.001f)
    }

    @Test fun newDay_startsFromZero_andKeepsHistory() {
        repo.record("youtube", 10f)
        day = day.plusDays(1)
        assertEquals(0f, repo.totalToday(), 0.001f)       // no manual reset needed
        repo.record("youtube", 4f)
        val h = repo.history(2)
        assertEquals(10f, h[0].totalMinutes, 0.001f)       // yesterday, oldest first
        assertEquals(4f, h[1].totalMinutes, 0.001f)
    }

    @Test fun resetToday_clearsOnlyToday() {
        repo.record("youtube", 10f)
        repo.recordLockAttempt()
        day = day.plusDays(1)
        repo.record("images", 5f)
        repo.recordLockAttempt()
        repo.resetToday()
        assertEquals(0f, repo.totalToday(), 0.001f)
        assertEquals(0, repo.lockAttemptsToday())
        val yesterday = repo.history(2)[0]
        assertEquals(10f, yesterday.totalMinutes, 0.001f)  // untouched
        assertEquals(1, yesterday.lockAttempts)
    }

    @Test fun lockAttempts_countPerDay() {
        repo.recordLockAttempt(); repo.recordLockAttempt()
        assertEquals(2, repo.lockAttemptsToday())
        day = day.plusDays(1)
        assertEquals(0, repo.lockAttemptsToday())
    }

    @Test fun history_includesEmptyDaysOldestFirst() {
        repo.record("youtube", 1f)
        val h = repo.history(7)
        assertEquals(7, h.size)
        assertEquals(day.minusDays(6), h.first().date)
        assertEquals(day, h.last().date)
        assertEquals(0f, h.first().totalMinutes, 0.001f)
        assertEquals(1f, h.last().totalMinutes, 0.001f)
    }

    @Test fun prune_removesOldUsage_keepsRecent_andNeverTouchesSettings() {
        val limitKey = intPreferencesKey("time_limit_mins")
        store.edit { it[limitKey] = 45 }
        val today = day
        day = today.minusDays(120); repo.record("youtube", 9f); repo.recordLockAttempt()
        day = today.minusDays(30);  repo.record("images", 6f)
        day = today
        repo.pruneOnce(keepDays = 90)
        assertEquals(6f, repo.history(31).first().totalMinutes, 0.001f)          // 30 days ago survives
        assertTrue(store.current.asMap().keys.none { it.name.contains("20260") && it.name.startsWith("usage_") && it.name.contains(UsageKeys.dayStamp(today.minusDays(120))) })
        assertEquals(45, store.current[limitKey])
    }

    @Test fun quizRounds_countPerDay() {
        assertEquals(0, repo.quizRoundsToday())
        repo.recordQuizRound(); repo.recordQuizRound(); repo.recordQuizRound()
        assertEquals(3, repo.quizRoundsToday())
        day = day.plusDays(1)
        assertEquals(0, repo.quizRoundsToday())             // a new day starts again at zero
        repo.recordQuizRound()
        assertEquals(1, repo.quizRoundsToday())
        day = day.minusDays(1)
        assertEquals(3, repo.quizRoundsToday())             // yesterday's count is still there
    }

    @Test fun bonus_isPerDay_andAccumulates() {
        repo.addBonus(10); repo.addBonus(5); repo.addBonus(0); repo.addBonus(-4)
        assertEquals(15, repo.bonusToday())
        day = day.plusDays(1)
        assertEquals(0, repo.bonusToday())
    }

    @Test fun resetToday_keepsBonus() {
        repo.addBonus(10); repo.record("youtube", 5f)
        repo.resetToday()
        assertEquals(0f, repo.totalToday(), 0.001f)
        assertEquals(10, repo.bonusToday())
    }

    @Test fun prune_removesOldBonusKeys() {
        val today = day
        day = today.minusDays(200); repo.addBonus(5)
        day = today
        repo.pruneOnce(90)
        day = today.minusDays(200)
        assertEquals(0, repo.bonusToday())
    }

    @Test fun prune_runsOncePerProcess() {
        val today = day
        day = today.minusDays(200); repo.record("youtube", 1f)
        day = today
        repo.pruneOnce(90)
        day = today.minusDays(200); repo.record("youtube", 1f)   // written after the first prune
        day = today
        repo.pruneOnce(90)                                         // second call is a no-op
        day = today.minusDays(200)
        assertEquals(1f, repo.categoryMinutes("youtube"), 0.001f)
    }
}
