package com.example.familysafetube

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import java.time.LocalDate

/** One day of recorded usage, for history / charts. */
data class DayUsage(val date: LocalDate, val minutesByCategory: Map<String, Float>, val lockAttempts: Int) {
    val totalMinutes: Float get() = minutesByCategory.values.sum()
}

/**
 * Screen-time bookkeeping on top of [PrefsStore]. Replaces the old UsageStats SharedPreferences
 * and the manual "reset at midnight" code: each day has its own keys (see [UsageKeys]).
 */
class UsageRepository(
    private val store: PrefsStore,
    private val today: () -> LocalDate = { LocalDate.now() }
) {
    private var pruned = false

    fun record(category: String, minutes: Float) {
        if (minutes <= 0f) return
        val key = floatPreferencesKey(UsageKeys.usageKey(today(), category))   // fixed before the edit block runs
        store.edit { it[key] = (it[key] ?: 0f) + minutes }
    }

    fun recordLockAttempt() {
        val key = intPreferencesKey(UsageKeys.locksKey(today()))
        store.edit { it[key] = (it[key] ?: 0) + 1 }
    }

    fun categoryMinutes(category: String): Float =
        store.snapshot()[floatPreferencesKey(UsageKeys.usageKey(today(), category))] ?: 0f

    /** Today's minutes per category, e.g. {youtube=12.5, images=3.0}. */
    fun categoriesToday(): Map<String, Float> = categoriesOn(store.snapshot(), today())

    fun totalToday(): Float = categoriesToday().values.sum()

    fun lockAttemptsToday(): Int =
        store.snapshot()[intPreferencesKey(UsageKeys.locksKey(today()))] ?: 0

    // ── bonus minutes (granted by the parent through settings.json) ────────────
    fun bonusToday(): Int = store.snapshot()[intPreferencesKey(UsageKeys.bonusKey(today()))] ?: 0

    fun addBonus(minutes: Int) {
        if (minutes <= 0) return
        val key = intPreferencesKey(UsageKeys.bonusKey(today()))
        store.edit { it[key] = (it[key] ?: 0) + minutes }
    }

    /** Parent pressed "Reset timer": forget today's usage and tamper count. */
    fun resetToday() {
        val prefix = UsageKeys.usageDayPrefix(today())
        val locks = UsageKeys.locksKey(today())
        removeKeys { name -> name.startsWith(prefix) || name == locks }
    }

    /** Last [days] days including today, oldest first; days without data are included as zeros. */
    fun history(days: Int): List<DayUsage> {
        val snap = store.snapshot()
        val end = today()
        return (days - 1 downTo 0).map { back ->
            val d = end.minusDays(back.toLong())
            DayUsage(d, categoriesOn(snap, d), snap[intPreferencesKey(UsageKeys.locksKey(d))] ?: 0)
        }
    }

    /** Drops usage older than [keepDays]. Runs at most once per process. */
    fun pruneOnce(keepDays: Int = 90) {
        if (pruned) return
        pruned = true
        val cutoff = today().minusDays(keepDays.toLong())
        removeKeys { name -> UsageKeys.isOlderThan(name, cutoff) }
    }

    // ── helpers ──────────────────────────────────────────────────────────────
    private fun categoriesOn(snap: Preferences, day: LocalDate): Map<String, Float> {
        val prefix = UsageKeys.usageDayPrefix(day)
        val out = LinkedHashMap<String, Float>()
        for ((key, value) in snap.asMap()) {
            if (key.name.startsWith(prefix) && value is Float) out[key.name.removePrefix(prefix)] = value
        }
        return out
    }

    @Suppress("UNCHECKED_CAST")
    private fun removeKeys(matches: (String) -> Boolean) {
        store.edit { prefs ->
            val doomed = prefs.asMap().keys.filter { matches(it.name) }
            for (k in doomed) prefs.remove(k as Preferences.Key<Any>)
        }
    }
}
