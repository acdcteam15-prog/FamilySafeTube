package com.example.familysafetube

import java.time.DayOfWeek
import java.time.LocalDateTime

/** Limit and bedtime for one kind of day. Times are minutes since midnight. */
data class DayRules(val limitMins: Int, val bedStart: Int, val bedEnd: Int)

/** Weekday rules, optional weekend rules, and which days count as weekend. */
data class ScheduleConfig(
    val weekday: DayRules,
    val weekend: DayRules? = null,
    val weekendDays: Set<DayOfWeek> = Schedule.DEFAULT_WEEKEND
) {
    fun rulesFor(day: DayOfWeek): DayRules = if (weekend != null && day in weekendDays) weekend else weekday
}

/**
 * Pure schedule logic (unit-tested).
 *
 * A bedtime window belongs to the day it STARTS on. Example: with a weekday window 20:00-07:00 and a
 * weekend window 22:00-09:00 (Sat/Sun), Saturday 08:00 is still governed by Friday night's weekday
 * window (it ended at 07:00), and Saturday night's weekend window runs until 09:00 on Sunday.
 */
object Schedule {
    val DEFAULT_WEEKEND: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    /** "SAT,SUN" -> {SATURDAY, SUNDAY}. Blank/invalid input falls back to the default weekend. */
    fun parseDays(text: String?): Set<DayOfWeek> {
        val parts = text?.split(',')?.map { it.trim().uppercase() }?.filter { it.isNotEmpty() } ?: return DEFAULT_WEEKEND
        val days = parts.mapNotNull { dayFromName(it) }.toSet()
        return if (days.isEmpty()) DEFAULT_WEEKEND else days
    }

    fun formatDays(days: Set<DayOfWeek>): String =
        days.sortedBy { it.value }.joinToString(",") { it.name.take(3) }

    /** "SAT", "saturday" -> SATURDAY; null if unrecognised. */
    fun dayFromName(name: String): DayOfWeek? {
        val n = name.trim().uppercase()
        return DayOfWeek.values().firstOrNull { it.name == n || it.name.take(3) == n }
    }

    private fun evening(r: DayRules, nowMin: Int): Boolean = when {
        r.bedStart == r.bedEnd -> false                           // no bedtime
        r.bedStart < r.bedEnd  -> nowMin in r.bedStart until r.bedEnd   // same-day window
        else                   -> nowMin >= r.bedStart            // crosses midnight: first part
    }

    private fun morning(r: DayRules, nowMin: Int): Boolean =
        r.bedStart > r.bedEnd && nowMin < r.bedEnd                // crosses midnight: second part

    private fun minuteOfDay(t: LocalDateTime) = t.hour * 60 + t.minute

    fun isBedtime(now: LocalDateTime, cfg: ScheduleConfig): Boolean {
        val n = minuteOfDay(now)
        return evening(cfg.rulesFor(now.dayOfWeek), n) || morning(cfg.rulesFor(now.dayOfWeek.minus(1)), n)
    }

    /** Minute-of-day at which the current lock ends, or null when it is not bedtime. */
    fun activeBedtimeEnd(now: LocalDateTime, cfg: ScheduleConfig): Int? {
        val n = minuteOfDay(now)
        val yesterday = cfg.rulesFor(now.dayOfWeek.minus(1))
        if (morning(yesterday, n)) return yesterday.bedEnd
        val today = cfg.rulesFor(now.dayOfWeek)
        if (evening(today, n)) return today.bedEnd
        return null
    }

    /** Minutes until tonight's bedtime starts; null if it is already bedtime or there is none left today. */
    fun minutesUntilBedtime(now: LocalDateTime, cfg: ScheduleConfig): Int? {
        if (isBedtime(now, cfg)) return null
        val r = cfg.rulesFor(now.dayOfWeek)
        if (r.bedStart == r.bedEnd) return null
        val n = minuteOfDay(now)
        return if (r.bedStart > n) r.bedStart - n else null
    }
}
