package com.example.familysafetube

import java.util.Locale

/**
 * Pure (Android-free) time helpers so the parental-control rules can be unit tested on the JVM.
 * All "minutes" values here are minutes since midnight unless stated otherwise.
 */
object TimeRules {

    private val HHMM = Regex("""^\s*(\d{1,2}):(\d{2})\s*$""")

    /** "7:30" / "07:30" -> 450. Returns null for anything that is not a valid 24h time. */
    fun parseMinutes(text: String?): Int? {
        val m = HHMM.matchEntire(text ?: return null) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        if (h !in 0..23 || min !in 0..59) return null
        return h * 60 + min
    }

    /** 450 -> "07:30" (always zero-padded so stored values are consistent). */
    fun format(minutesOfDay: Int): String =
        String.format(Locale.US, "%02d:%02d", minutesOfDay / 60, minutesOfDay % 60)

    /**
     * True when [now] falls inside the bedtime window [start, end).
     * Handles windows that cross midnight (e.g. 20:00 -> 07:00).
     * start == end means "no bedtime" (never locked).
     * The end is exclusive: with an end of 07:00 the app is usable again at 07:00.
     */
    fun isInWindow(now: Int, start: Int, end: Int): Boolean = when {
        start == end -> false
        start < end  -> now in start until end
        else         -> now >= start || now < end
    }

    /** Whole minutes left of a limit; never negative. */
    fun minutesLeft(limitMins: Int, usedMins: Float): Float =
        (limitMins - usedMins).coerceAtLeast(0f)
}
