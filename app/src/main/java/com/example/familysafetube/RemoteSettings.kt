package com.example.familysafetube

import androidx.datastore.preferences.core.MutablePreferences
import java.time.DayOfWeek
import java.time.LocalDate

data class RemoteWeekend(
    val enabled: Boolean?, val limitMins: Int?, val bedStart: String?, val bedEnd: String?, val days: Set<DayOfWeek>?
)

data class RemoteBonus(val id: String, val minutes: Int, val expires: LocalDate?)

/** Only the fields present in the file are non-null / non-empty; everything else is left untouched. */
data class RemoteSettings(
    val limitMins: Int? = null,
    val bedStart: String? = null,
    val bedEnd: String? = null,
    val categoryLimits: Map<String, Int> = emptyMap(),
    val weekend: RemoteWeekend? = null,
    val bonus: RemoteBonus? = null
)

sealed interface RemoteParse {
    data class Ok(val settings: RemoteSettings) : RemoteParse
    data class Invalid(val reason: String) : RemoteParse
}

/**
 * Parses and strictly validates the parent's `settings.json` from Drive. The whole file is rejected
 * if ANY value is out of range, so a typo can never half-apply or set absurd values.
 *
 * Example:
 * {
 *   "version": 1,
 *   "time_limit_mins": 45,
 *   "bedtime_start": "20:00", "bedtime_end": "07:00",
 *   "category_limits": { "youtube": 20, "offline_videos": 0, "images": 30 },
 *   "weekend": { "enabled": true, "time_limit_mins": 90, "bedtime_start": "21:30",
 *                "bedtime_end": "08:00", "days": ["SAT", "SUN"] },
 *   "bonus": { "id": "2026-10-07-a", "minutes": 15, "expires": "2026-10-07" }
 * }
 */
object RemoteSettingsParser {
    const val MAX_FILE_CHARS = 64 * 1024
    private val CATEGORIES = Categories.ALL

    private class Bad(message: String) : Exception(message)

    fun parse(text: String): RemoteParse {
        if (text.length > MAX_FILE_CHARS) return RemoteParse.Invalid("file too large")
        return try {
            @Suppress("UNCHECKED_CAST")
            val root = MiniJson.parse(text) as? Map<String, Any?> ?: throw Bad("top level must be an object")
            RemoteParse.Ok(build(root))
        } catch (e: JsonException) {
            RemoteParse.Invalid("not valid JSON: ${e.message}")
        } catch (e: Bad) {
            RemoteParse.Invalid(e.message ?: "invalid")
        }
    }

    private fun build(root: Map<String, Any?>): RemoteSettings {
        root["version"]?.let { if (intIn(it, "version", 1, 1) != 1) throw Bad("unsupported version") }

        val limit = root["time_limit_mins"]?.let { intIn(it, "time_limit_mins", 1, 600) }
        val bedStart = root["bedtime_start"]?.let { time(it, "bedtime_start") }
        val bedEnd = root["bedtime_end"]?.let { time(it, "bedtime_end") }

        val cats = LinkedHashMap<String, Int>()
        (root["category_limits"])?.let { raw ->
            @Suppress("UNCHECKED_CAST")
            val m = raw as? Map<String, Any?> ?: throw Bad("category_limits must be an object")
            for ((k, v) in m) {
                if (k !in CATEGORIES) throw Bad("unknown category '$k'")
                cats[k] = intIn(v, "category_limits.$k", 0, 600)
            }
        }

        val weekend = (root["weekend"])?.let { raw ->
            @Suppress("UNCHECKED_CAST")
            val m = raw as? Map<String, Any?> ?: throw Bad("weekend must be an object")
            RemoteWeekend(
                enabled = m["enabled"]?.let { it as? Boolean ?: throw Bad("weekend.enabled must be true/false") },
                limitMins = m["time_limit_mins"]?.let { intIn(it, "weekend.time_limit_mins", 1, 600) },
                bedStart = m["bedtime_start"]?.let { time(it, "weekend.bedtime_start") },
                bedEnd = m["bedtime_end"]?.let { time(it, "weekend.bedtime_end") },
                days = m["days"]?.let { days(it) }
            )
        }

        val bonus = (root["bonus"])?.let { raw ->
            @Suppress("UNCHECKED_CAST")
            val m = raw as? Map<String, Any?> ?: throw Bad("bonus must be an object")
            val id = (m["id"] as? String)?.trim().orEmpty()
            if (id.isEmpty() || id.length > 64) throw Bad("bonus.id must be 1-64 characters")
            val minutes = intIn(m["minutes"] ?: throw Bad("bonus.minutes missing"), "bonus.minutes", 1, 120)
            val expires = m["expires"]?.let {
                try { LocalDate.parse(it as? String ?: throw Bad("bonus.expires must be a date")) }
                catch (e: java.time.format.DateTimeParseException) { throw Bad("bonus.expires must be yyyy-MM-dd") }
            }
            RemoteBonus(id, minutes, expires)
        }

        return RemoteSettings(limit, bedStart, bedEnd, cats, weekend, bonus)
    }

    private fun intIn(v: Any?, name: String, min: Int, max: Int): Int {
        val d = v as? Double ?: throw Bad("$name must be a number")
        if (d != Math.floor(d)) throw Bad("$name must be a whole number")
        val i = d.toInt()
        if (i < min || i > max) throw Bad("$name must be between $min and $max")
        return i
    }

    private fun time(v: Any?, name: String): String {
        val m = TimeRules.parseMinutes(v as? String) ?: throw Bad("$name must be HH:mm (24h)")
        return TimeRules.format(m)
    }

    private fun days(v: Any?): Set<DayOfWeek> {
        val list = v as? List<*> ?: throw Bad("weekend.days must be a list")
        if (list.isEmpty() || list.size > 7) throw Bad("weekend.days must have 1-7 entries")
        return list.map { Schedule.dayFromName(it as? String ?: throw Bad("weekend.days entries must be text"))
            ?: throw Bad("unknown day '$it'") }.toSet()
    }

    /** Writes only the provided fields into the settings store (bonus is handled separately). */
    fun applyTo(p: MutablePreferences, s: RemoteSettings) {
        s.limitMins?.let { p[Keys.TIME_LIMIT_MINS] = it }
        s.bedStart?.let { p[Keys.BEDTIME_START] = it }
        s.bedEnd?.let { p[Keys.BEDTIME_END] = it }
        for ((cat, v) in s.categoryLimits) p[Keys.catLimit(cat)] = v
        s.weekend?.let { w ->
            w.enabled?.let { p[Keys.WEEKEND_ENABLED] = it }
            w.limitMins?.let { p[Keys.WEEKEND_LIMIT] = it }
            w.bedStart?.let { p[Keys.WEEKEND_BED_START] = it }
            w.bedEnd?.let { p[Keys.WEEKEND_BED_END] = it }
            w.days?.let { p[Keys.WEEKEND_DAYS] = Schedule.formatDays(it) }
        }
    }

    /** True if this bonus should be granted now: not yet applied (by id) and not expired. */
    fun bonusDue(bonus: RemoteBonus, alreadyAppliedId: String?, today: LocalDate): Boolean =
        bonus.id != alreadyAppliedId && (bonus.expires == null || !today.isAfter(bonus.expires))
}
