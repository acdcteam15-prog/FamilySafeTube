package com.example.familysafetube

/** Decides when to warn that time is nearly up. Pure, so the thresholds can be unit-tested. */
object WindDown {
    /** Warn at 5 minutes and at 1 minute remaining. */
    val THRESHOLDS = listOf(5, 1)

    /**
     * Given the minutes [left], returns the most urgent threshold newly crossed (and records it in
     * [warned] so it is never repeated), or null if nothing new. Starting a session with 3 minutes
     * left warns once (the "5" threshold is marked done together with it), not twice.
     */
    fun due(left: Float, warned: MutableSet<Int>): Int? {
        val crossed = THRESHOLDS.filter { left <= it && it !in warned }
        if (crossed.isEmpty()) return null
        warned.addAll(crossed)
        return crossed.min()
    }
}
