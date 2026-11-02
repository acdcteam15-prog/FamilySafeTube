package com.example.familysafetube

import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Full-screen "immersive sticky" mode for the player screens: status and navigation bars hidden,
 * revealed only transiently by an edge swipe. Replaces four copies of the deprecated
 * `systemUiVisibility` flags with the supported insets-controller API (same behaviour; it also
 * keeps working when the app later targets API 35+).
 *
 * Deliberately does NOT change `decorFitsSystemWindows`, so layout (and display-cutout handling) is
 * exactly as before.
 */
object Immersive {
    fun enter(activity: Activity) {
        val window = activity.window
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
