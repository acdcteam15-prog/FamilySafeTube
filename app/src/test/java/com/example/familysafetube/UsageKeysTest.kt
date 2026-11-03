package com.example.familysafetube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class UsageKeysTest {
    private val d = LocalDate.of(2026, 10, 6)

    @Test fun keys_areZeroPaddedAndPrefixed() {
        assertEquals("usage_20261006_youtube", UsageKeys.usageKey(d, "youtube"))
        assertEquals("usage_20260105_images", UsageKeys.usageKey(LocalDate.of(2026, 1, 5), "images"))
        assertEquals("locks_20261006", UsageKeys.locksKey(d))
    }

    @Test fun parse_handlesUnderscoresInCategory() {
        assertEquals("20261006" to "offline_videos", UsageKeys.parseUsageKey("usage_20261006_offline_videos"))
        assertEquals("20261006", UsageKeys.parseLocksKey("locks_20261006"))
    }

    @Test fun parse_rejectsOtherKeys() {
        assertNull(UsageKeys.parseUsageKey("time_limit_mins"))
        assertNull(UsageKeys.parseUsageKey("usage_2026_youtube"))
        assertNull(UsageKeys.parseLocksKey("lock_attempts"))
        assertNull(UsageKeys.dayOf("pin_hash"))
    }

    @Test fun isOlderThan_onlyAffectsUsageAndLocksKeys() {
        val cutoff = LocalDate.of(2026, 7, 8)
        assertTrue(UsageKeys.isOlderThan("usage_20260707_youtube", cutoff))
        assertTrue(UsageKeys.isOlderThan("locks_20260101", cutoff))
        assertFalse(UsageKeys.isOlderThan("usage_20260708_youtube", cutoff))   // cutoff day itself is kept
        assertFalse(UsageKeys.isOlderThan("usage_20261006_youtube", cutoff))
        assertFalse(UsageKeys.isOlderThan("time_limit_mins", cutoff))           // settings are never pruned
        assertFalse(UsageKeys.isOlderThan("pin_hash", cutoff))
    }

    @Test fun bonusKeys_areRecognisedByDay() {
        assertEquals("bonus_20261006", UsageKeys.bonusKey(d))
        assertEquals("20261006", UsageKeys.dayOf("bonus_20261006"))
        assertTrue(UsageKeys.isOlderThan("bonus_20260101", LocalDate.of(2026, 7, 8)))
        assertFalse(UsageKeys.isOlderThan("bonus_20261006", LocalDate.of(2026, 7, 8)))
        assertNull(UsageKeys.dayOf("quiz_enabled"))                // a setting, not a day key
    }

    @Test fun roundsKeys_areRecognisedByDay_soTheyArePruned() {
        assertEquals("rounds_20261006", UsageKeys.roundsKey(d))
        assertEquals("20261006", UsageKeys.dayOf("rounds_20261006"))
        assertTrue(UsageKeys.isOlderThan("rounds_20260101", LocalDate.of(2026, 7, 8)))
        assertFalse(UsageKeys.isOlderThan("rounds_20261006", LocalDate.of(2026, 7, 8)))
    }

    @Test fun parseDay_roundTrips() {
        assertEquals(d, UsageKeys.parseDay(UsageKeys.dayStamp(d)))
        assertNull(UsageKeys.parseDay("2026xx06"))
    }
}
