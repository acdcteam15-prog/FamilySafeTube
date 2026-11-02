package com.example.familysafetube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsFormTest {
    private fun input(
        limit: String = "30", bs: String = "20:00", be: String = "07:00",
        yt: String = "", vid: String = "", img: String = "", sync: String = "06:00"
    ) = SettingsInput(limit, bs, be, yt, vid, img, sync)

    private fun ok(i: SettingsInput) = (SettingsForm.validate(i) as FormResult.Ok).settings
    private fun err(i: SettingsInput) = (SettingsForm.validate(i) as FormResult.Error).error

    @Test fun validDefaults_areAccepted() {
        val v = ok(input())
        assertEquals(30, v.limitMins)
        assertEquals("20:00", v.bedtimeStart)
        assertEquals(0, v.youtubeLimit)       // blank = unlimited
    }

    @Test fun times_areNormalisedToZeroPadded() {
        val v = ok(input(bs = "8:05", be = "7:00", sync = "6:30"))
        assertEquals("08:05", v.bedtimeStart)
        assertEquals("07:00", v.bedtimeEnd)
        assertEquals("06:30", v.syncTime)
    }

    @Test fun limit_mustBePositiveNumber() {
        assertEquals(FormError.LIMIT, err(input(limit = "")))
        assertEquals(FormError.LIMIT, err(input(limit = "0")))
        assertEquals(FormError.LIMIT, err(input(limit = "-5")))
        assertEquals(FormError.LIMIT, err(input(limit = "abc")))
    }

    @Test fun badSyncTime_reported() {
        assertEquals(FormError.SYNC_TIME, err(input(sync = "25:00")))
        assertEquals(FormError.SYNC_TIME, err(input(sync = "")))
    }

    @Test fun badBedtime_reportedForEitherField() {
        assertEquals(FormError.BEDTIME, err(input(bs = "8pm")))
        assertEquals(FormError.BEDTIME, err(input(be = "99:99")))
    }

    @Test fun limitErrorTakesPrecedenceOverTimeErrors() {
        assertEquals(FormError.LIMIT, err(input(limit = "x", sync = "bad", bs = "bad")))
    }

    @Test fun categoryLimits_parseBlankInvalidAndNegative() {
        val v = ok(input(yt = "15", vid = "abc", img = "-3"))
        assertEquals(15, v.youtubeLimit)
        assertEquals(0, v.videosLimit)
        assertEquals(0, v.imagesLimit)
    }

    @Test fun weekendDisabled_badWeekendValuesFallBackToWeekday() {
        val v = ok(input().copy(weekendEnabled = false, weekendLimit = "zzz", weekendBedStart = "", weekendBedEnd = "nope"))
        assertEquals(30, v.weekendLimit)
        assertEquals("20:00", v.weekendBedStart)
        assertEquals("07:00", v.weekendBedEnd)
        assertFalse(v.weekendEnabled)
    }

    @Test fun weekendEnabled_isStrict() {
        val base = input().copy(weekendEnabled = true, weekendLimit = "90", weekendBedStart = "22:00", weekendBedEnd = "09:00")
        val v = ok(base)
        assertEquals(90, v.weekendLimit); assertEquals("22:00", v.weekendBedStart); assertTrue(v.weekendEnabled)
        assertEquals(FormError.WEEKEND_LIMIT, err(base.copy(weekendLimit = "0")))
        assertEquals(FormError.WEEKEND_LIMIT, err(base.copy(weekendLimit = "")))
        assertEquals(FormError.WEEKEND_BEDTIME, err(base.copy(weekendBedEnd = "25:00")))
        assertEquals(FormError.WEEKEND_DAYS, err(base.copy(weekendDays = emptySet())))
    }

    @Test fun whitespaceIsTrimmed() {
        val v = ok(input(limit = " 45 ", yt = " 10 "))
        assertEquals(45, v.limitMins)
        assertEquals(10, v.youtubeLimit)
        assertTrue(SettingsForm.validate(input()) is FormResult.Ok)
    }
}
