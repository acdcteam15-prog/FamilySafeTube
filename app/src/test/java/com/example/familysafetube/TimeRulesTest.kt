package com.example.familysafetube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeRulesTest {

    @Test fun parse_acceptsWithAndWithoutLeadingZero() {
        assertEquals(450, TimeRules.parseMinutes("07:30"))
        assertEquals(450, TimeRules.parseMinutes("7:30"))
        assertEquals(0, TimeRules.parseMinutes("00:00"))
        assertEquals(23 * 60 + 59, TimeRules.parseMinutes("23:59"))
        assertEquals(480, TimeRules.parseMinutes("  08:00 "))
    }

    @Test fun parse_rejectsGarbage() {
        assertNull(TimeRules.parseMinutes(null))
        assertNull(TimeRules.parseMinutes(""))
        assertNull(TimeRules.parseMinutes("24:00"))
        assertNull(TimeRules.parseMinutes("12:60"))
        assertNull(TimeRules.parseMinutes("8pm"))
        assertNull(TimeRules.parseMinutes("8:5"))
        assertNull(TimeRules.parseMinutes("-1:30"))
    }

    @Test fun format_zeroPads() {
        assertEquals("07:05", TimeRules.format(7 * 60 + 5))
        assertEquals("00:00", TimeRules.format(0))
        assertEquals("23:59", TimeRules.format(23 * 60 + 59))
    }

    @Test fun window_crossingMidnight() {
        val start = 20 * 60   // 20:00
        val end = 7 * 60      // 07:00
        assertTrue(TimeRules.isInWindow(20 * 60, start, end))      // start inclusive
        assertTrue(TimeRules.isInWindow(23 * 60 + 30, start, end))
        assertTrue(TimeRules.isInWindow(0, start, end))
        assertTrue(TimeRules.isInWindow(6 * 60 + 59, start, end))
        assertFalse(TimeRules.isInWindow(7 * 60, start, end))      // end exclusive: usable again at 07:00
        assertFalse(TimeRules.isInWindow(12 * 60, start, end))
        assertFalse(TimeRules.isInWindow(19 * 60 + 59, start, end))
    }

    @Test fun window_sameDay() {
        val start = 13 * 60   // quiet time 13:00-15:00
        val end = 15 * 60
        assertFalse(TimeRules.isInWindow(12 * 60 + 59, start, end))
        assertTrue(TimeRules.isInWindow(13 * 60, start, end))
        assertTrue(TimeRules.isInWindow(14 * 60 + 59, start, end))
        assertFalse(TimeRules.isInWindow(15 * 60, start, end))
    }

    @Test fun window_equalStartEndMeansNoBedtime() {
        assertFalse(TimeRules.isInWindow(0, 600, 600))
        assertFalse(TimeRules.isInWindow(600, 600, 600))
    }

    @Test fun minutesLabel_roundsUp_soItNeverSaysZeroWhileTimeRemains() {
        assertEquals(1, TimeRules.minutesLabel(0.01f))       // the last seconds
        assertEquals(1, TimeRules.minutesLabel(0.99f))
        assertEquals(1, TimeRules.minutesLabel(1.0f))
        assertEquals(2, TimeRules.minutesLabel(1.01f))
        assertEquals(30, TimeRules.minutesLabel(29.2f))
        assertEquals(0, TimeRules.minutesLabel(0f))          // only when it really is over
        assertEquals(0, TimeRules.minutesLabel(-3f))
    }

    @Test fun minutesLeft_neverNegative() {
        assertEquals(20f, TimeRules.minutesLeft(30, 10f), 0.001f)
        assertEquals(0f, TimeRules.minutesLeft(30, 45f), 0.001f)
    }
}
