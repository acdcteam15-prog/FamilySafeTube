package com.example.familysafetube

import android.content.Context
import android.os.SystemClock

/**
 * Thin facade over [UsageRepository] kept so the player activities did not need to change.
 *
 * Usage categories (these must match the "cat_limit_<category>" setting keys):
 *   "youtube", "offline_videos", "images"
 *
 * There is no manual midnight reset any more: usage is stored per calendar day (see [UsageKeys]),
 * so a new day automatically starts from zero and a session that runs across midnight rolls over.
 */
@Suppress("UNUSED_PARAMETER")
object UsageTracker {

    /** Called on app start/resume: trims history older than 90 days (once per process). */
    fun rolloverIfNeeded(context: Context) = AppGraph.usage.pruneOnce()

    fun recordTime(context: Context, category: String, minutes: Float) =
        AppGraph.usage.record(category, minutes)

    fun recordLockAttempt(context: Context) = AppGraph.usage.recordLockAttempt()

    /** Minutes used today in one category. */
    fun categoryMinutes(context: Context, category: String): Float =
        AppGraph.usage.categoryMinutes(category)

    /** Parent pressed "Reset timer": clears today's usage and tamper count. */
    fun resetToday(context: Context) = AppGraph.usage.resetToday()

    fun getStats(context: Context): Map<String, Any> {
        val files      = context.filesDir.listFiles()
        val totalFiles = files?.size ?: 0
        val totalSize  = files?.sumOf { it.length() } ?: 0L
        return mapOf(
            "categories"    to AppGraph.usage.categoriesToday(),
            "lock_attempts" to AppGraph.usage.lockAttemptsToday(),
            "file_count"    to totalFiles,
            "storage_mb"    to (totalSize / (1024 * 1024))
        )
    }

    /**
     * Measures real elapsed time instead of counting fixed 10-second ticks.
     *   onResume -> start()    onPause -> stop()    every tick -> flush()
     */
    class Session(private val context: Context, private val category: String) {
        private var lastMs = 0L

        fun start() { lastMs = SystemClock.elapsedRealtime() }

        fun flush() {
            if (lastMs == 0L) return
            val now = SystemClock.elapsedRealtime()
            val mins = (now - lastMs) / 60_000f
            lastMs = now
            recordTime(context, category, mins)
        }

        fun stop() { flush(); lastMs = 0L }
    }
}
