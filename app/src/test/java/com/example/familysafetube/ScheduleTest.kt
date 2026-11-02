package com.example.familysafetube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class ScheduleTest {
    private fun m(h: Int, min: Int = 0) = h * 60 + min
    // 2026-10-05 is a Monday
    private fun at(day: Int, h: Int, min: Int = 0): LocalDateTime = LocalDate.of(2026, 10, 4 + day).atTime(h, min)

    private val weekday = DayRules(limitMins = 30, bedStart = m(20), bedEnd = m(7))
    private val weekend = DayRules(limitMins = 90, bedStart = m(22), bedEnd = m(9))
    private val uniform = ScheduleConfig(weekday)
    private val split = ScheduleConfig(weekday, weekend, setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))

    @Test fun uniformConfig_matchesOldBedtimeBehaviourForEveryMinute() {
        for (day in 1..7) for (minute in 0 until 24 * 60) {
            val now = at(day, minute / 60, minute % 60)
            assertEquals("day=$day minute=$minute",
                TimeRules.isInWindow(minute, m(20), m(7)), Schedule.isBedtime(now, uniform))
        }
    }

    @Test fun weekendDays_useWeekendLimit() {
        assertEquals(30, split.rulesFor(DayOfWeek.MONDAY).limitMins)
        assertEquals(30, split.rulesFor(DayOfWeek.FRIDAY).limitMins)
        assertEquals(90, split.rulesFor(DayOfWeek.SATURDAY).limitMins)
        assertEquals(90, split.rulesFor(DayOfWeek.SUNDAY).limitMins)
    }

    @Test fun disabledWeekend_alwaysUsesWeekdayRules() {
        assertEquals(30, uniform.rulesFor(DayOfWeek.SATURDAY).limitMins)
    }

    @Test fun saturdayMorning_isGovernedByFridayNightsWeekdayWindow() {
        // Fri 20:00 -> Sat 07:00 (weekday window). Saturday 08:00 must be free even though the
        // weekend window (22:00-09:00) is "later".
        assertTrue(Schedule.isBedtime(at(5, 23), split))      // Fri 23:00 locked
        assertTrue(Schedule.isBedtime(at(6, 6, 30), split))   // Sat 06:30 still locked (Fri's window)
        assertFalse(Schedule.isBedtime(at(6, 8), split))      // Sat 08:00 free
        assertFalse(Schedule.isBedtime(at(6, 21), split))     // Sat 21:00 free (weekend starts 22:00)
        assertTrue(Schedule.isBedtime(at(6, 22), split))      // Sat 22:00 locked
    }

    @Test fun sundayMorning_isGovernedBySaturdayNightsWeekendWindow() {
        assertTrue(Schedule.isBedtime(at(7, 8, 59), split))   // Sun 08:59 locked (Sat's window to 09:00)
        assertFalse(Schedule.isBedtime(at(7, 9), split))      // Sun 09:00 free
    }

    @Test fun mondayMorning_isGovernedBySundayNightsWeekendWindow() {
        // Sun window 22:00 -> Mon 09:00 (weekend rules belong to Sunday)
        assertTrue(Schedule.isBedtime(at(8, 8), split))       // Mon 08:00 locked
        assertFalse(Schedule.isBedtime(at(8, 9), split))
    }

    @Test fun activeBedtimeEnd_reportsTheWindowThatIsActuallyLocking() {
        assertEquals(m(7), Schedule.activeBedtimeEnd(at(6, 6, 30), split))   // Sat morning -> Fri's 07:00
        assertEquals(m(9), Schedule.activeBedtimeEnd(at(6, 23), split))      // Sat night  -> 09:00
        assertEquals(m(7), Schedule.activeBedtimeEnd(at(1, 21), split))      // Mon night  -> 07:00
        assertNull(Schedule.activeBedtimeEnd(at(1, 12), split))              // midday: not bedtime
    }

    @Test fun sameDayWindow_andNoWindow() {
        val quiet = ScheduleConfig(DayRules(30, m(13), m(15)))
        assertTrue(Schedule.isBedtime(at(2, 14), quiet))
        assertFalse(Schedule.isBedtime(at(2, 15), quiet))
        val none = ScheduleConfig(DayRules(30, m(8), m(8)))
        assertFalse(Schedule.isBedtime(at(2, 8), none))
    }

    @Test fun minutesUntilBedtime() {
        assertEquals(30, Schedule.minutesUntilBedtime(at(1, 19, 30), uniform))
        assertNull(Schedule.minutesUntilBedtime(at(1, 20, 5), uniform))   // already bedtime
        assertNull(Schedule.minutesUntilBedtime(at(1, 8), ScheduleConfig(DayRules(30, m(8), m(8)))))
        // Weekend evening uses the weekend start (22:00)
        assertEquals(60, Schedule.minutesUntilBedtime(at(6, 21), split))
    }

    @Test fun dayParsingAndFormatting() {
        assertEquals(setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), Schedule.parseDays("fri, SAT"))
        assertEquals(setOf(DayOfWeek.SUNDAY), Schedule.parseDays("Sunday"))
        assertEquals(Schedule.DEFAULT_WEEKEND, Schedule.parseDays(""))
        assertEquals(Schedule.DEFAULT_WEEKEND, Schedule.parseDays("xyz"))
        assertEquals(Schedule.DEFAULT_WEEKEND, Schedule.parseDays(null))
        assertEquals("FRI,SAT", Schedule.formatDays(setOf(DayOfWeek.SATURDAY, DayOfWeek.FRIDAY)))
    }
}
