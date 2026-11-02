package com.example.familysafetube

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import java.io.File

/**
 * SyncEngine is now a thin coordinator.
 * All actual downloading is done by SyncWorker (WorkManager).
 * This object is kept so MainActivity and other callers don't need refactoring.
 */
object SyncEngine {
    private const val TAG = "SyncEngine"

    // ── Called by MainActivity "SYNC" button ─────────────────────────────────
    fun syncAll(
        context: Context,
        onProgress: (String) -> Unit,
        onComplete: (Boolean, String?) -> Unit
    ) {
        if (!DriveAuth.isSignedIn()) {
            onComplete(false, context.getString(R.string.sync_not_signed_in))
            return
        }

        val wifiOnly = AppGraph.settings.getOr(Keys.SYNC_WIFI_ONLY, false)

        if (wifiOnly && !isWifiConnected(context)) {
            onComplete(false, context.getString(R.string.sync_wait_wifi))
            return
        }

        // If a sync is already running just tell the UI and return —
        // no point starting another one for the same files.
        if (SyncWorker.isRunning(context)) {
            onProgress(context.getString(R.string.sync_already_bg))
            onComplete(true, context.getString(R.string.sync_already_bg_done))
            return
        }

        // Enqueue the WorkManager job — it will run even if the app is closed
        SyncWorker.enqueue(context)
        onProgress(context.getString(R.string.sync_started_bg))
        onComplete(true, context.getString(R.string.sync_running_bg))
    }

    // ── Called by players and home screen to list local files ─────────────────
    fun getLocalFiles(context: Context, category: String): List<File> {
        val prefix = "${category.lowercase().trim()}_"
        val files  = context.filesDir.listFiles()
            ?.filter { it.name.lowercase().startsWith(prefix) } ?: emptyList()
        Log.d(TAG, "getLocalFiles: cat='$category' found=${files.size}")
        return files
    }

    private fun isWifiConnected(context: Context): Boolean {
        val cm   = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}