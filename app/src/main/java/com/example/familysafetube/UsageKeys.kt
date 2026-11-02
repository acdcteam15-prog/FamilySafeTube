package com.example.familysafetube

import java.time.LocalDate
import java.util.Locale

/**
 * Key scheme for the per-day usage tally. Pure JVM so it is unit-testable.
 *
 * Usage is stored under one key per (day, category):  usage_20261006_youtube
 * Tamper attempts per day:                             locks_20261006
 *
 * Because the date is part of the key, "midnight rollover" needs no reset logic at all: a new day
 * simply starts reading and writing new keys. Old days stay available as history (weekly charts)
 * until they are pruned.
 */
object UsageKeys {
    const val USAGE_PREFIX = "usage_"
    const val LOCKS_PREFIX = "locks_"
    const val BONUS_PREFIX = "bonus_"

    private val USAGE_RE = Regex("""^usage_(\d{8})_(.+)$""")
    private val LOCKS_RE = Regex("""^locks_(\d{8})$""")
    private val BONUS_RE = Regex("""^bonus_(\d{8})$""")

    fun dayStamp(d: LocalDate): String =
        String.format(Locale.US, "%04d%02d%02d", d.year, d.monthValue, d.dayOfMonth)

    fun usageKey(d: LocalDate, category: String) = "$USAGE_PREFIX${dayStamp(d)}_$category"
    fun usageDayPrefix(d: LocalDate) = "$USAGE_PREFIX${dayStamp(d)}_"
    fun locksKey(d: LocalDate) = "$LOCKS_PREFIX${dayStamp(d)}"
    /** Extra minutes granted for this day (from the parent's remote settings.json). */
    fun bonusKey(d: LocalDate) = "$BONUS_PREFIX${dayStamp(d)}"

    /** "usage_20261006_offline_videos" -> ("20261006", "offline_videos"); null if not a usage key. */
    fun parseUsageKey(name: String): Pair<String, String>? =
        USAGE_RE.matchEntire(name)?.let { it.groupValues[1] to it.groupValues[2] }

    /** "locks_20261006" -> "20261006"; null if not a locks key. */
    fun parseLocksKey(name: String): String? = LOCKS_RE.matchEntire(name)?.groupValues?.get(1)

    /** The day stamp a key belongs to (usage, locks or bonus), or null for any other key. */
    fun dayOf(name: String): String? = parseUsageKey(name)?.first ?: parseLocksKey(name)
        ?: BONUS_RE.matchEntire(name)?.groupValues?.get(1)

    /** True if the key is a usage/locks key from before [cutoff]. yyyyMMdd stamps sort chronologically. */
    fun isOlderThan(name: String, cutoff: LocalDate): Boolean {
        val stamp = dayOf(name) ?: return false
        return stamp < dayStamp(cutoff)
    }

    fun parseDay(stamp: String): LocalDate? = try {
        LocalDate.of(stamp.substring(0, 4).toInt(), stamp.substring(4, 6).toInt(), stamp.substring(6, 8).toInt())
    } catch (_: Exception) { null }
}
