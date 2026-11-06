package com.example.familysafetube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LockPolicyTest {
    private val minute = 60_000L

    // ── which lock ──
    @Test fun noLockWhenNothingApplies() = assertEquals(LockKind.NONE, LockPolicy.kind(false, false, false))

    @Test fun eachReasonLocks() {
        assertEquals(LockKind.MANUAL, LockPolicy.kind(true, false, false))
        assertEquals(LockKind.BEDTIME, LockPolicy.kind(false, true, false))
        assertEquals(LockKind.TIME_UP, LockPolicy.kind(false, false, true))
    }

    @Test fun priority_manualThenBedtimeThenTimeUp() {
        assertEquals(LockKind.MANUAL, LockPolicy.kind(true, true, true))
        assertEquals(LockKind.BEDTIME, LockPolicy.kind(false, true, true))
    }

    @Test fun isLocked() {
        assertFalse(LockPolicy.isLocked(LockKind.NONE))
        for (k in listOf(LockKind.MANUAL, LockKind.BEDTIME, LockKind.TIME_UP)) assertTrue(LockPolicy.isLocked(k))
    }

    // ── bedtime extension ──
    @Test fun bedtime_countsOnlyAfterTheExtensionRunsOut() {
        val now = 1_000_000L
        assertTrue(LockPolicy.bedtimeActive(true, now, 0L))                  // no extension
        assertFalse(LockPolicy.bedtimeActive(true, now, now + 5 * minute))   // extended: not locked
        assertTrue(LockPolicy.bedtimeActive(true, now, now))                 // exactly at the deadline: locked again
        assertTrue(LockPolicy.bedtimeActive(true, now, now - 1))             // expired
        assertFalse(LockPolicy.bedtimeActive(false, now, 0L))                // not bedtime at all
    }

    @Test fun extensionMinutesLeft_onlyDuringBedtimeWithARunningExtension() {
        val now = 5_000_000L
        assertEquals(10f, LockPolicy.extensionMinutesLeft(true, now, now + 10 * minute)!!, 0.001f)
        assertNull(LockPolicy.extensionMinutesLeft(true, now, now))
        assertNull(LockPolicy.extensionMinutesLeft(true, now, 0L))
        assertNull(LockPolicy.extensionMinutesLeft(false, now, now + 10 * minute))
    }

    // ── exact timing ──
    @Test fun nextCheck_landsJustAfterTheDeadline_notTenSecondsLate() {
        assertEquals(10_000L, LockPolicy.nextCheckDelayMs(null))                    // nothing to wait for: safety poll
        assertEquals(10_000L, LockPolicy.nextCheckDelayMs(25f))                     // far away: safety poll
        assertEquals(10_000L, LockPolicy.nextCheckDelayMs(1f))                      // 60 s away: still the 10 s poll
        assertEquals(4_300L, LockPolicy.nextCheckDelayMs(4f / 60f))                 // 4 s left: fire at 4.3 s
        assertEquals(1_300L, LockPolicy.nextCheckDelayMs(1f / 60f))                 // 1 s left: 1.3 s
    }

    @Test fun nextCheck_neverBusyLoops() {
        assertEquals(300L, LockPolicy.nextCheckDelayMs(0f))
        assertEquals(300L, LockPolicy.nextCheckDelayMs(-5f))
        assertTrue(LockPolicy.nextCheckDelayMs(0.0001f) >= LockPolicy.MIN_CHECK_MS)
        for (m in listOf(0f, 0.01f, 0.1f, 0.5f, 2f, 100f)) assertTrue(LockPolicy.nextCheckDelayMs(m) in LockPolicy.MIN_CHECK_MS..LockPolicy.MAX_CHECK_MS)
    }

    @Test fun nextCheck_neverFiresBeforeTheDeadline() {
        for (secs in listOf(0.5f, 1f, 3f, 7f, 9f)) {
            val delay = LockPolicy.nextCheckDelayMs(secs / 60f)
            assertTrue("$secs s -> $delay ms", delay >= (secs * 1000).toLong())
        }
    }

    // ── what a grant changes ──
    @Test fun grant_whenTimeIsUp_addsTheMinutesToTodaysLimit() {
        val g = LockPolicy.grant(LockKind.TIME_UP, true, 15, 1_000L, 0L)
        assertEquals(LockPolicy.Grant(15, null), g)
    }

    @Test fun grant_atBedtime_liftsBedtimeForThatLong() {
        val now = 10_000_000L
        val g = LockPolicy.grant(LockKind.BEDTIME, false, 10, now, 0L)
        assertEquals(0, g.bonusMinutes)                                   // the limit is not used up: no bonus needed
        assertEquals(now + 10 * minute, g.bedtimeExtensionUntilMs)
    }

    @Test fun grant_atBedtimeWithTheLimitUsedUp_alsoAddsMinutes_otherwiseTheChildIsLockedAgainAtOnce() {
        val g = LockPolicy.grant(LockKind.BEDTIME, true, 15, 10_000_000L, 0L)
        assertEquals(15, g.bonusMinutes)
        assertEquals(10_000_000L + 15 * minute, g.bedtimeExtensionUntilMs)
    }

    @Test fun grant_extendsAnExtensionThatIsStillRunning() {
        val now = 10_000_000L
        val g = LockPolicy.grant(LockKind.BEDTIME, false, 5, now, now + 3 * minute)
        assertEquals(now + 8 * minute, g.bedtimeExtensionUntilMs)         // 3 left + 5 more
    }

    @Test fun grant_manualLockOrNoLockOrZeroMinutes_changesNothing() {
        assertEquals(LockPolicy.Grant(0, null), LockPolicy.grant(LockKind.MANUAL, false, 15, 1L, 0L))
        assertEquals(LockPolicy.Grant(0, null), LockPolicy.grant(LockKind.NONE, false, 15, 1L, 0L))
        assertEquals(LockPolicy.Grant(0, null), LockPolicy.grant(LockKind.TIME_UP, true, 0, 1L, 0L))
        assertEquals(LockPolicy.Grant(0, null), LockPolicy.grant(LockKind.TIME_UP, true, -5, 1L, 0L))
    }

    @Test fun grantChoices_areSmallAndSorted() {
        assertEquals(listOf(5, 10, 15, 30), LockPolicy.GRANT_CHOICES)
        assertEquals(LockPolicy.GRANT_CHOICES.sorted(), LockPolicy.GRANT_CHOICES)
    }

    // ── "lock for N minutes" ──
    @Test fun lockChoices_startWithUntilAParentOpensIt_andAreSorted() {
        assertEquals(0, LockPolicy.LOCK_CHOICES.first())
        assertEquals(LockPolicy.LOCK_CHOICES.sorted(), LockPolicy.LOCK_CHOICES)
        assertTrue(LockPolicy.LOCK_CHOICES.drop(1).all { it > 0 })
    }

    @Test fun timedLock_endsAtNowPlusMinutes_zeroHasNoEnd() {
        assertEquals(1_000L + 15 * minute, LockPolicy.timedLockEnd(15, 1_000L))
        assertEquals(0L, LockPolicy.timedLockEnd(0, 1_000L))
        assertEquals(0L, LockPolicy.timedLockEnd(-3, 1_000L))
    }

    @Test fun manualLock_isOnWhileATimedLockRuns_andOffAfterItEnds() {
        val now = 5_000_000L
        assertFalse(LockPolicy.manualActive(false, 0L, now))
        assertTrue(LockPolicy.manualActive(true, 0L, now))                 // until a parent opens it
        assertTrue(LockPolicy.manualActive(false, now + minute, now))      // timed, still running
        assertFalse(LockPolicy.manualActive(false, now, now))              // exactly at the end: open again
        assertFalse(LockPolicy.manualActive(false, now - 1, now))          // over
        assertTrue(LockPolicy.manualActive(true, now - 1, now))            // the manual flag wins
    }

    @Test fun delayWithTimedLock_looksAgainWhenTheTimedLockEnds() {
        val now = 5_000_000L
        assertEquals(30_000L, LockPolicy.delayWithTimedLock(30_000L, 0L, now))                    // no timed lock
        assertEquals(30_000L, LockPolicy.delayWithTimedLock(30_000L, now - 1, now))               // already over
        assertEquals(30_000L, LockPolicy.delayWithTimedLock(30_000L, now + 10 * minute, now))     // far away: normal rhythm
        val soon = LockPolicy.delayWithTimedLock(30_000L, now + 5_000L, now)
        assertTrue("fires after the end, not before: $soon", soon >= 5_000L)
        assertTrue("and soon after it: $soon", soon < 6_000L)
        assertTrue(LockPolicy.delayWithTimedLock(30_000L, now + 1L, now) >= LockPolicy.MIN_CHECK_MS)
    }

    // ── daily quiz rounds ──
    @Test fun roundLimit_zeroMeansNoLimit() {
        assertFalse(RoundLimit.reached(0, 0)); assertFalse(RoundLimit.reached(0, 999))
        assertNull(RoundLimit.remaining(0, 5))
    }

    @Test fun roundLimit_reachedAtTheLimit() {
        assertFalse(RoundLimit.reached(5, 4)); assertTrue(RoundLimit.reached(5, 5)); assertTrue(RoundLimit.reached(5, 6))
        assertEquals(1, RoundLimit.remaining(5, 4)); assertEquals(0, RoundLimit.remaining(5, 5)); assertEquals(0, RoundLimit.remaining(5, 9))
    }

    @Test fun roundLimit_unknownStoredValueMeansNoLimit() {
        for (c in RoundLimit.CHOICES) assertEquals(c, RoundLimit.sanitize(c))
        assertEquals(0, RoundLimit.sanitize(7)); assertEquals(0, RoundLimit.sanitize(-1))
        assertEquals(0, RoundLimit.CHOICES.first())
    }
}
