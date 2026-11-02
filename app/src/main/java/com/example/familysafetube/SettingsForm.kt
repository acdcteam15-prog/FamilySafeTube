package com.example.familysafetube

import java.time.DayOfWeek

/** Raw text the parent typed into the settings form. */
data class SettingsInput(
    val limitMins: String,
    val bedtimeStart: String,
    val bedtimeEnd: String,
    val youtubeLimit: String,
    val videosLimit: String,
    val imagesLimit: String,
    val syncTime: String,
    val weekendEnabled: Boolean = false,
    val weekendLimit: String = "",
    val weekendBedStart: String = "",
    val weekendBedEnd: String = "",
    val weekendDays: Set<DayOfWeek> = Schedule.DEFAULT_WEEKEND
)

/** Validated, normalised values ready to be stored. */
data class ValidSettings(
    val limitMins: Int,
    val bedtimeStart: String,
    val bedtimeEnd: String,
    val youtubeLimit: Int,
    val videosLimit: Int,
    val imagesLimit: Int,
    val syncTime: String,
    val weekendEnabled: Boolean,
    val weekendLimit: Int,
    val weekendBedStart: String,
    val weekendBedEnd: String,
    val weekendDays: Set<DayOfWeek>
)

enum class FormError { LIMIT, SYNC_TIME, BEDTIME, WEEKEND_LIMIT, WEEKEND_BEDTIME, WEEKEND_DAYS }

sealed interface FormResult {
    data class Ok(val settings: ValidSettings) : FormResult
    data class Error(val error: FormError) : FormResult
}

/** Pure validation for the settings dialog (unit-tested). */
object SettingsForm {
    fun validate(i: SettingsInput): FormResult {
        val limit = i.limitMins.trim().toIntOrNull()
        if (limit == null || limit < 1) return FormResult.Error(FormError.LIMIT)

        val sync = TimeRules.parseMinutes(i.syncTime) ?: return FormResult.Error(FormError.SYNC_TIME)
        val bedStart = TimeRules.parseMinutes(i.bedtimeStart)
        val bedEnd = TimeRules.parseMinutes(i.bedtimeEnd)
        if (bedStart == null || bedEnd == null) return FormResult.Error(FormError.BEDTIME)

        // Weekend values are strict when the weekend schedule is on; otherwise a bad value quietly
        // falls back to the weekday value so garbage is never stored.
        val wLimitParsed = i.weekendLimit.trim().toIntOrNull()
        val wStartParsed = TimeRules.parseMinutes(i.weekendBedStart)
        val wEndParsed = TimeRules.parseMinutes(i.weekendBedEnd)
        if (i.weekendEnabled) {
            if (wLimitParsed == null || wLimitParsed < 1) return FormResult.Error(FormError.WEEKEND_LIMIT)
            if (wStartParsed == null || wEndParsed == null) return FormResult.Error(FormError.WEEKEND_BEDTIME)
            if (i.weekendDays.isEmpty()) return FormResult.Error(FormError.WEEKEND_DAYS)
        }
        val wLimit = wLimitParsed?.takeIf { it >= 1 } ?: limit
        val wStart = wStartParsed ?: bedStart
        val wEnd = wEndParsed ?: bedEnd

        // Blank or invalid category limits mean "unlimited" (0); negative values are clamped to 0.
        fun cat(text: String) = (text.trim().toIntOrNull() ?: 0).coerceAtLeast(0)

        return FormResult.Ok(ValidSettings(
            limitMins       = limit,
            bedtimeStart    = TimeRules.format(bedStart),
            bedtimeEnd      = TimeRules.format(bedEnd),
            youtubeLimit    = cat(i.youtubeLimit),
            videosLimit     = cat(i.videosLimit),
            imagesLimit     = cat(i.imagesLimit),
            syncTime        = TimeRules.format(sync),
            weekendEnabled  = i.weekendEnabled,
            weekendLimit    = wLimit,
            weekendBedStart = TimeRules.format(wStart),
            weekendBedEnd   = TimeRules.format(wEnd),
            weekendDays     = i.weekendDays.ifEmpty { Schedule.DEFAULT_WEEKEND }
        ))
    }
}
