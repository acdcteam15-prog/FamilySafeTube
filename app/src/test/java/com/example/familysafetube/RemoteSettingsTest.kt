package com.example.familysafetube

import androidx.datastore.preferences.core.emptyPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class RemoteSettingsTest {
    private fun ok(json: String) = (RemoteSettingsParser.parse(json) as RemoteParse.Ok).settings
    private fun invalid(json: String) = RemoteSettingsParser.parse(json) is RemoteParse.Invalid

    @Test fun fullFile_parsesEveryField() {
        val s = ok("""{
          "version": 1, "time_limit_mins": 45, "bedtime_start": "8:30", "bedtime_end": "07:00",
          "category_limits": {"youtube": 20, "offline_videos": 0, "images": 30},
          "weekend": {"enabled": true, "time_limit_mins": 90, "bedtime_start": "21:30",
                      "bedtime_end": "08:00", "days": ["SAT","sunday"]},
          "bonus": {"id": "b1", "minutes": 15, "expires": "2026-10-07"}
        }""")
        assertEquals(45, s.limitMins)
        assertEquals("08:30", s.bedStart)                      // normalised
        assertEquals(mapOf("youtube" to 20, "offline_videos" to 0, "images" to 30), s.categoryLimits)
        assertEquals(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), s.weekend?.days)
        assertEquals(true, s.weekend?.enabled)
        assertEquals(RemoteBonus("b1", 15, LocalDate.of(2026, 10, 7)), s.bonus)
    }

    @Test fun partialFile_leavesOtherFieldsNull() {
        val s = ok("""{"time_limit_mins": 20}""")
        assertEquals(20, s.limitMins)
        assertNull(s.bedStart); assertNull(s.weekend); assertNull(s.bonus)
        assertTrue(s.categoryLimits.isEmpty())
    }

    @Test fun emptyObject_isValidAndChangesNothing() {
        assertEquals(RemoteSettings(), ok("{}"))
    }

    @Test fun anyOutOfRangeValue_rejectsTheWholeFile() {
        assertTrue(invalid("""{"time_limit_mins": 0}"""))
        assertTrue(invalid("""{"time_limit_mins": 601}"""))
        assertTrue(invalid("""{"time_limit_mins": 12.5}"""))
        assertTrue(invalid("""{"time_limit_mins": "30"}"""))
        assertTrue(invalid("""{"bedtime_start": "25:00"}"""))
        assertTrue(invalid("""{"category_limits": {"tiktok": 5}}"""))
        assertTrue(invalid("""{"category_limits": {"youtube": -1}}"""))
        assertTrue(invalid("""{"weekend": {"days": ["FUNDAY"]}}"""))
        assertTrue(invalid("""{"weekend": {"days": []}}"""))
        assertTrue(invalid("""{"bonus": {"id": "", "minutes": 10}}"""))
        assertTrue(invalid("""{"bonus": {"id": "x", "minutes": 500}}"""))
        assertTrue(invalid("""{"bonus": {"id": "x", "minutes": 10, "expires": "tomorrow"}}"""))
        assertTrue(invalid("""{"version": 2}"""))
    }

    @Test fun validValuePlusInvalidValue_appliesNothing() {
        assertTrue(invalid("""{"time_limit_mins": 30, "bedtime_end": "nope"}"""))
    }

    @Test fun notJsonOrNotObject_isInvalid() {
        assertTrue(invalid("hello"))
        assertTrue(invalid("[1,2,3]"))
        assertTrue(invalid(""))
        assertTrue(invalid("x".repeat(RemoteSettingsParser.MAX_FILE_CHARS + 1)))
    }

    @Test fun applyTo_writesOnlyProvidedKeys() {
        val s = ok("""{"time_limit_mins": 45, "category_limits": {"images": 10}, "weekend": {"enabled": true, "days": ["FRI"]}}""")
        val p = emptyPreferences().toMutablePreferences()
        RemoteSettingsParser.applyTo(p, s)
        assertEquals(45, p[Keys.TIME_LIMIT_MINS])
        assertEquals(10, p[Keys.catLimit("images")])
        assertEquals(true, p[Keys.WEEKEND_ENABLED])
        assertEquals("FRI", p[Keys.WEEKEND_DAYS])
        assertNull(p[Keys.BEDTIME_START])               // not provided -> untouched
        assertNull(p[Keys.catLimit("youtube")])
    }

    @Test fun bonusDue_onlyOncePerIdAndNotAfterExpiry() {
        val today = LocalDate.of(2026, 10, 7)
        val b = RemoteBonus("b1", 15, LocalDate.of(2026, 10, 7))
        assertTrue(RemoteSettingsParser.bonusDue(b, null, today))
        assertFalse(RemoteSettingsParser.bonusDue(b, "b1", today))            // already applied
        assertTrue(RemoteSettingsParser.bonusDue(b, "old", today))             // new id
        assertFalse(RemoteSettingsParser.bonusDue(b, null, today.plusDays(1))) // expired
        assertTrue(RemoteSettingsParser.bonusDue(RemoteBonus("n", 5, null), null, today.plusDays(99)))
    }
}
