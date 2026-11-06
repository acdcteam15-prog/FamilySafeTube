package com.example.familysafetube

import kotlin.math.max

/** Why the whole app is covered by the lock screen (NONE = it is not). */
enum class LockKind { NONE, MANUAL, BEDTIME, TIME_UP }

/** The rules of the full-app lock, as pure functions (unit-tested; Policy and the screens only call them). */
object LockPolicy {
    /** A safety re-check at least this often, even when the limit is far away. */
    const val MAX_CHECK_MS = 10_000L
    /** Never spin faster than this. */
    const val MIN_CHECK_MS = 250L
    /** Fire a hair AFTER the deadline so the elapsed time has really passed the limit. */
    private const val MARGIN_MS = 300L

    /** Extra minutes the parent can add with the PIN. */
    val GRANT_CHOICES = listOf(5, 10, 15, 30)

    /** "Lock now" choices in minutes; 0 = stay locked until a parent opens it. */
    val LOCK_CHOICES = listOf(0, 15, 30, 60)

    /** A manual lock is on when it was set "until a parent opens it", or a timed lock has not run out yet. */
    fun manualActive(manualFlag: Boolean, timedUntilMs: Long, nowMs: Long): Boolean = manualFlag || nowMs < timedUntilMs

    /** When a timed lock started now ends (epoch ms); 0 = it has no end ("until a parent opens it"). */
    fun timedLockEnd(minutes: Int, nowMs: Long): Long = if (minutes <= 0) 0L else nowMs + minutes * 60_000L

    /** Look again when a running timed lock ends (a hair after it), but never later than [normalDelayMs]. */
    fun delayWithTimedLock(normalDelayMs: Long, timedUntilMs: Long, nowMs: Long): Long {
        if (timedUntilMs <= nowMs) return normalDelayMs
        return minOf(normalDelayMs, (timedUntilMs - nowMs) + MARGIN_MS).coerceAtLeast(MIN_CHECK_MS)
    }

    /** Manual lock beats bedtime, which beats "time is up". */
    fun kind(manual: Boolean, bedtime: Boolean, globalReached: Boolean): LockKind = when {
        manual -> LockKind.MANUAL
        bedtime -> LockKind.BEDTIME
        globalReached -> LockKind.TIME_UP
        else -> LockKind.NONE
    }

    fun isLocked(kind: LockKind): Boolean = kind != LockKind.NONE

    /** Bedtime counts only after a parent's bedtime extension (a deadline in epoch ms) has run out. */
    fun bedtimeActive(rawBedtime: Boolean, nowMs: Long, extensionUntilMs: Long): Boolean =
        rawBedtime && nowMs >= extensionUntilMs

    /** Minutes left of a bedtime extension, or null when there is none running. */
    fun extensionMinutesLeft(rawBedtime: Boolean, nowMs: Long, extensionUntilMs: Long): Float? =
        if (rawBedtime && nowMs < extensionUntilMs) (extensionUntilMs - nowMs) / 60_000f else null

    /**
     * When to look again. The next check lands exactly when the time runs out (so the lock is immediate, not up to
     * 10 seconds late), but never later than [MAX_CHECK_MS] and never sooner than [MIN_CHECK_MS].
     */
    fun nextCheckDelayMs(minutesLeft: Float?): Long {
        if (minutesLeft == null) return MAX_CHECK_MS
        val ms = (minutesLeft.coerceAtLeast(0f) * 60_000f).toLong() + MARGIN_MS
        return ms.coerceIn(MIN_CHECK_MS, MAX_CHECK_MS)
    }

    /** What a parent's "add [minutes]" changes. */
    data class Grant(val bonusMinutes: Int, val bedtimeExtensionUntilMs: Long?)

    /**
     * Time is up: the minutes are added to today's limit. Bedtime: the bedtime lock is lifted for that long (and the
     * minutes are also added to the limit when that is used up too, otherwise the child would be locked again at once).
     * A manual lock needs no minutes: the PIN simply opens it.
     */
    fun grant(kind: LockKind, globalReached: Boolean, minutes: Int, nowMs: Long, currentExtensionUntilMs: Long): Grant {
        if (minutes <= 0) return Grant(0, null)
        return when (kind) {
            LockKind.TIME_UP -> Grant(minutes, null)
            LockKind.BEDTIME -> Grant(
                if (globalReached) minutes else 0,
                max(nowMs, currentExtensionUntilMs) + minutes * 60_000L
            )
            LockKind.MANUAL, LockKind.NONE -> Grant(0, null)
        }
    }
}

/** The optional "quiz rounds per day" limit (0 = no limit). */
object RoundLimit {
    val CHOICES = listOf(0, 3, 5, 10, 20)

    fun reached(limit: Int, doneToday: Int): Boolean = limit > 0 && doneToday >= limit

    /** Rounds still allowed today, or null when there is no limit. */
    fun remaining(limit: Int, doneToday: Int): Int? = if (limit <= 0) null else (limit - doneToday).coerceAtLeast(0)

    /** A stored value that is not one of the offered choices means "no limit". */
    fun sanitize(value: Int): Int = if (value in CHOICES) value else 0
}
