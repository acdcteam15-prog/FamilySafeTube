package com.example.familysafetube

import android.content.Context
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Single source of truth for "is the child allowed to use the app right now?".
 * No side effects: callers decide whether to record a tamper attempt.
 *
 * Limits and bedtime come from [Schedule] (weekday vs weekend rules); bonus minutes (remote grants
 * and quiz rewards) are added to today's global limit.
 */
object Policy {
    const val CAT_YOUTUBE = Categories.YOUTUBE
    const val CAT_VIDEOS  = Categories.VIDEOS
    const val CAT_IMAGES  = Categories.IMAGES

    private val settings get() = AppGraph.settings
    private val usage get() = AppGraph.usage

    private const val DEFAULT_BED_START = 20 * 60
    private const val DEFAULT_BED_END = 7 * 60

    /** Builds the schedule from stored settings. A malformed stored time falls back to the default. */
    fun schedule(): ScheduleConfig {
        val weekday = DayRules(
            limitMins = settings.getOr(Keys.TIME_LIMIT_MINS, 30).coerceAtLeast(1),
            bedStart = TimeRules.parseMinutes(settings.getOr(Keys.BEDTIME_START, "20:00")) ?: DEFAULT_BED_START,
            bedEnd = TimeRules.parseMinutes(settings.getOr(Keys.BEDTIME_END, "07:00")) ?: DEFAULT_BED_END
        )
        val weekend = if (settings.getOr(Keys.WEEKEND_ENABLED, false)) DayRules(
            limitMins = settings.getOr(Keys.WEEKEND_LIMIT, weekday.limitMins).coerceAtLeast(1),
            bedStart = TimeRules.parseMinutes(settings.getOr(Keys.WEEKEND_BED_START, "")) ?: weekday.bedStart,
            bedEnd = TimeRules.parseMinutes(settings.getOr(Keys.WEEKEND_BED_END, "")) ?: weekday.bedEnd
        ) else null
        return ScheduleConfig(weekday, weekend, Schedule.parseDays(settings.getOr(Keys.WEEKEND_DAYS, "")))
    }

    fun isBedtime(c: Context): Boolean = Schedule.isBedtime(LocalDateTime.now(), schedule())

    /** "HH:mm" at which the current bedtime lock ends (falls back to today's configured end). */
    fun bedtimeEnd(c: Context): String {
        val cfg = schedule()
        val now = LocalDateTime.now()
        val end = Schedule.activeBedtimeEnd(now, cfg) ?: cfg.rulesFor(now.dayOfWeek).bedEnd
        return TimeRules.format(end)
    }

    fun minutesUntilBedtime(c: Context): Int? = Schedule.minutesUntilBedtime(LocalDateTime.now(), schedule())

    /** Today's global limit in minutes: the weekday/weekend limit plus any bonus minutes. */
    fun globalLimit(c: Context): Int =
        schedule().rulesFor(LocalDate.now().dayOfWeek).limitMins + usage.bonusToday()

    /** Minutes used today across all categories. */
    fun globalElapsed(c: Context): Float = usage.totalToday()

    fun isGlobalLimitReached(c: Context) = globalElapsed(c) >= globalLimit(c)

    /** Per-category daily limit in minutes, 0 = unlimited. */
    fun categoryLimit(c: Context, category: String): Int = settings.getOr(Keys.catLimit(category), 0)

    /** Minutes left in a category, or null when the category has no limit. */
    fun categoryRemaining(c: Context, category: String): Float? {
        val limit = categoryLimit(c, category)
        if (limit <= 0) return null
        return TimeRules.minutesLeft(limit, usage.categoryMinutes(category))
    }

    /** The tightest of: global time left, this category's time left, time until bedtime. */
    fun minutesLeft(c: Context, category: String?): Float {
        var left = TimeRules.minutesLeft(globalLimit(c), globalElapsed(c))
        if (category != null) categoryRemaining(c, category)?.let { left = minOf(left, it) }
        minutesUntilBedtime(c)?.let { left = minOf(left, it.toFloat()) }
        return left
    }

    fun categoryLabel(c: Context, cat: String): String = when (cat) {
        CAT_YOUTUBE -> c.getString(R.string.cat_label_youtube)
        CAT_VIDEOS  -> c.getString(R.string.cat_label_videos)
        CAT_IMAGES  -> c.getString(R.string.cat_label_images)
        else        -> cat.replaceFirstChar { it.uppercase() }
    }

    /**
     * Returns a user-facing message if the child must not use [category] right now, otherwise null.
     * Checked on launch AND repeatedly while a player is open, so bedtime and per-category
     * limits also stop a session that is already running.
     */
    fun blockReason(c: Context, category: String?): String? {
        if (isBedtime(c)) return c.getString(R.string.block_bedtime, bedtimeEnd(c))
        if (isGlobalLimitReached(c)) return c.getString(R.string.block_daily)
        if (category != null) {
            val left = categoryRemaining(c, category)
            if (left != null && left <= 0f) {
                return c.getString(R.string.block_category, categoryLabel(c, category), categoryLimit(c, category))
            }
        }
        return null
    }
}
