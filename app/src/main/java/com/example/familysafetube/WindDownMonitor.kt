package com.example.familysafetube

import android.content.Context
import android.widget.Toast
import kotlin.math.ceil

/**
 * Warns the child (not just the parent) before time runs out: once at 5 minutes and once at 1 minute
 * remaining, counting the tightest of global limit, category limit and bedtime. One instance per
 * player session; call [check] from the existing 10-second usage ticker.
 */
class WindDownMonitor(private val context: Context, private val category: String) {
    private val warned = mutableSetOf<Int>()

    fun check() {
        val left = Policy.minutesLeft(context, category)
        if (left <= 0f) return                       // the limit/bedtime check will end the session
        val threshold = WindDown.due(left, warned) ?: return
        val text = if (threshold <= 1) context.getString(R.string.warn_one_minute)
                   else context.getString(R.string.warn_minutes, ceil(left).toInt())
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }
}
