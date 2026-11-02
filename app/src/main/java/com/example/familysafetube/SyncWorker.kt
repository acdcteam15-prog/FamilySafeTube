package com.example.familysafetube

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * WorkManager CoroutineWorker that mirrors the Drive folders to local storage.
 * Survives app close. Progress is exposed through WorkInfo.progress (no notifications).
 *
 * Phase 1 hardening vs. the previous version:
 *  - Deletion sync can no longer wipe local files when a folder scan fails, is truncated, or
 *    comes back unexpectedly empty.
 *  - Drive listings are paginated (the old code silently stopped at the first page).
 *  - Files replaced in Drive under the same name are detected and re-downloaded.
 *  - The YouTube list is replaced atomically instead of being deleted before the scan.
 *  - Downloads run on a cancellable coroutine loop instead of a blocking latch.
 *  - The daily schedule is a PeriodicWork, so it no longer cancels itself or depends on the app
 *    being opened to keep the chain alive.
 *  - The Drive API key is read from BuildConfig (local.properties), not source code.
 */
class SyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SyncWorker"
        const val WORK_NAME = "FamilySafeTubeSync"
        private const val SCHEDULED_NAME = "ScheduledSyncDaily"
        private const val LEGACY_SCHEDULED_NAME = "ScheduledSync"   // old one-shot chain, cancelled on upgrade
        private const val INDEX_PREFS = "SyncIndex"

        const val KEY_PROGRESS_TEXT    = "progress_text"
        const val KEY_PROGRESS_CURRENT = "progress_current"
        const val KEY_PROGRESS_TOTAL   = "progress_total"
        const val KEY_STATUS           = "status"   // "running" | "done" | "error"

        // Drive folders (category -> folder id). Folder ids are not secrets; the API key is.
        private const val FOLDER_OFFLINE = "1KRE3OHgNn4GCiuw_1P5jTWmaHsepsxjS"
        private const val FOLDER_ANIMALS = "1FRh_6mBewivxn_ilvi09XapNMMqT1Wdu"
        private const val FOLDER_LETTERS = "1Pe-9QtLlYkFntPzJFVf74cGuj2L1UEHw"
        private const val FOLDER_VEG     = "1GVpT2B6hkzL6-S-nR5z9X3dsl8mniYnA"
        private const val FOLDER_OTHER   = "1cQkpJC2llqxtAwjIAa6IWT_I5fLMpPKy"
        private const val USER_AGENT     = "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36"

        private val FOLDERS = linkedMapOf(
            "videos"     to FOLDER_OFFLINE,
            "animals"    to FOLDER_ANIMALS,
            "letters"    to FOLDER_LETTERS,
            "veg&fruits" to FOLDER_VEG,
            "other"      to FOLDER_OTHER
        )

        /** Local file-name prefixes this worker owns. "config" lives in the "videos" Drive folder. */
        private val OWNED_CATEGORIES = listOf("videos", "animals", "letters", "veg&fruits", "other", "config")

        // Prevents two workers (manual + scheduled) from racing on the same .tmp file.
        private val inProgressIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

        /** "Sync on Wi-Fi only" now applies to every sync, including the scheduled one. */
        private fun networkType(context: Context): NetworkType {
            val wifiOnly = AppGraph.settings.getOr(Keys.SYNC_WIFI_ONLY, false)
            return if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        }

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(networkType(context))
                .build()

            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
            Log.d(TAG, "Sync enqueued")
        }

        /**
         * Schedules the daily sync at the configured time as a 24h periodic job.
         * @param replace true when the user just changed the time in Settings (re-anchors the schedule);
         *                false on normal app start (keeps the existing schedule untouched).
         */
        fun scheduleDaily(context: Context, replace: Boolean = false) {
            val minutes = TimeRules.parseMinutes(AppGraph.settings.getOr(Keys.SYNC_TIME, "06:00")) ?: (6 * 60)

            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, minutes / 60)
                set(Calendar.MINUTE, minutes % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (!target.after(now)) target.add(Calendar.DAY_OF_YEAR, 1)
            val delayMs = target.timeInMillis - now.timeInMillis

            val request = PeriodicWorkRequestBuilder<SyncWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(networkType(context)).build())
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
                .addTag("scheduled_sync")
                .build()

            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(LEGACY_SCHEDULED_NAME)
            wm.enqueueUniquePeriodicWork(
                SCHEDULED_NAME,
                if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "Daily sync scheduled for ${TimeRules.format(minutes)} (first run in ${delayMs / 60_000} min)")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        fun isRunning(context: Context): Boolean {
            val info = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(WORK_NAME).get()
            return info.any {
                it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
            }
        }

        /** Forget remembered file signatures (called when the parent clears offline storage). */
        fun clearIndex(context: Context) {
            context.getSharedPreferences(INDEX_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        }
    }

    // Cookie jar: Drive sets a download_warning cookie on the virus-scan interstitial that must be
    // echoed back on the confirmed request. Synchronised because the client is shared.
    private val cookieJar = object : okhttp3.CookieJar {
        private val store = mutableMapOf<String, MutableList<okhttp3.Cookie>>()
        override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
            synchronized(store) {
                store.getOrPut(url.host) { mutableListOf() }.apply {
                    removeAll { c -> cookies.any { it.name == c.name } }
                    addAll(cookies)
                }
            }
        }
        override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> =
            synchronized(store) { store[url.host]?.toList() ?: emptyList() }
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .protocols(listOf(Protocol.HTTP_1_1))   // single-connection HTTP/1.1: keeps heap usage flat on big mp4s
        .cookieJar(cookieJar)
        .build()

    private val apiKey: String get() = BuildConfig.DRIVE_API_KEY

    private fun str(id: Int, vararg args: Any): String = applicationContext.getString(id, *args)

    private val index by lazy {
        applicationContext.getSharedPreferences(INDEX_PREFS, Context.MODE_PRIVATE)
    }

    // ── Entry point ──────────────────────────────────────────────────────────
    override suspend fun doWork(): Result {
        if (apiKey.isBlank()) {
            // Retrying cannot fix a missing key.
            emitProgress(str(R.string.sync_no_key), 0, 0, status = "error")
            return Result.failure()
        }

        // Persistent failures (403, bad sharing) will not be fixed by retrying forever.
        if (runAttemptCount >= 2) {
            emitProgress(str(R.string.sync_gave_up), 0, 0, status = "error")
            return Result.failure()
        }

        emitProgress(str(R.string.sync_connecting_drive), 0, 0)

        return try {
            val allOk = withContext(Dispatchers.IO) { runSync() }
            if (allOk) Result.success() else Result.retry()
        } catch (e: CancellationException) {
            // Cancelled by Android (battery optimizer, a newer enqueue…). Rethrow so WorkManager
            // can reschedule cleanly instead of treating it as a failure.
            Log.w(TAG, "Sync cancelled — will retry when conditions allow")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ${e.message}", e)
            emitProgress(str(R.string.sync_failed_short), 0, 0, status = "error")
            Result.retry()
        }
    }

    // ── Main sync flow ───────────────────────────────────────────────────────
    /** @return true when everything that should sync did; false if anything failed (worker retries). */
    private suspend fun runSync(): Boolean {
        emitProgress(str(R.string.sync_scanning), 0, 0)

        val scan = scanAllFolders()
        Log.d(TAG, "Drive scan: ${scan.files.size} files, failed folders=${scan.failedCategories}")

        val deleted = deleteRemovedFiles(scan)
        if (deleted > 0) broadcastComplete()

        val toDownload = scan.files.filter { needsDownload(it) }
        Log.d(TAG, "To download: ${toDownload.size} files")

        var done = 0
        var failed = 0
        val total = toDownload.size

        for ((i, df) in toDownload.withIndex()) {
            currentCoroutineContext().ensureActive()
            emitProgress(str(R.string.sync_downloading), i, total)
            try {
                downloadFile(df)
                index.edit().putString(storageName(df.category, df.name), df.signature).apply()
                done++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed: ${df.name} — ${e.message}")
                failed++
            }
        }

        // Apply the parent's settings.json (if enabled). Never allowed to fail the sync.
        try {
            RemoteSettingsApplier.apply(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Applying remote settings failed: ${e.message}", e)
        }

        val scanProblems = scan.failedCategories
        val msg = when {
            scanProblems.isNotEmpty() -> str(R.string.sync_partial, scanProblems.joinToString())
            failed > 0                -> str(R.string.sync_done_some, done, failed)
            total > 0 && deleted > 0  -> str(R.string.sync_complete_removed, done, deleted)
            total > 0                 -> str(R.string.sync_complete, done)
            deleted > 0               -> str(R.string.sync_removed_only, deleted)
            else                      -> str(R.string.sync_uptodate)
        }
        emitProgress(msg, total, total, status = "done")
        broadcastComplete()

        return scanProblems.isEmpty() && failed == 0
    }

    private fun broadcastComplete() {
        applicationContext.sendBroadcast(
            Intent("com.example.familysafetube.SYNC_COMPLETE").setPackage(applicationContext.packageName)
        )
    }

    // ── Deletion sync (guarded) ──────────────────────────────────────────────
    /**
     * Removes local files that were deleted from Drive, but only when the scan for that
     * category is trustworthy:
     *  - the folder scan did not fail, and
     *  - it did not come back empty while we still hold files for it (a transient API oddity or a
     *    permissions change must not wipe the device; use Settings > Clear Offline Storage for that).
     * @return number of files deleted
     */
    private fun deleteRemovedFiles(scan: ScanResult): Int {
        val expected = scan.files.map { storageName(it.category, it.name) }.toHashSet()
        val scannedCount = scan.files.groupingBy { it.category }.eachCount()

        val locals = applicationContext.filesDir.listFiles()?.toList() ?: return 0
        val localCount = locals.groupingBy { categoryOfLocal(it.name) }.eachCount()

        fun mayDelete(cat: String): Boolean {
            if (cat == "config") {
                // The YouTube list comes from the "videos" Drive folder: trust it only if that listing worked.
                return "videos" in scan.okCategories && (scannedCount["videos"] ?: 0) > 0
            }
            if (cat !in scan.okCategories) return false
            val scanned = scannedCount[cat] ?: 0
            return !(scanned == 0 && (localCount[cat] ?: 0) > 0)
        }

        var n = 0
        for (f in locals) {
            if (f.name.endsWith(".tmp") || f.name.startsWith("pref_")) continue
            if (f.name in expected) continue
            val cat = categoryOfLocal(f.name) ?: continue   // not ours — leave it alone
            if (!mayDelete(cat)) {
                Log.w(TAG, "Not deleting ${f.name}: scan for '$cat' not trustworthy")
                continue
            }
            Log.d(TAG, "🗑 Deleting removed file: ${f.name}")
            if (f.delete()) { index.edit().remove(f.name).apply(); n++ }
        }
        return n
    }

    private fun categoryOfLocal(name: String): String? =
        OWNED_CATEGORIES.firstOrNull { name.startsWith("${it}_") }

    // ── Change detection ─────────────────────────────────────────────────────
    private fun needsDownload(df: DriveFileInfo): Boolean {
        val name  = storageName(df.category, df.name)
        val local = File(applicationContext.filesDir, name)

        if (!local.exists() || local.length() == 0L) return true
        if (df.category == "config") return true                     // tiny; always refresh the list
        if (df.size > 0 && local.length() != df.size) return true    // truncated / replaced

        val stored = index.getString(name, null)
        if (stored == null) {
            // File predates change tracking: adopt it as current instead of re-downloading everything.
            index.edit().putString(name, df.signature).apply()
            return false
        }
        return stored != df.signature
    }

    // ── Progress helper ──────────────────────────────────────────────────────
    private suspend fun emitProgress(
        text: String, current: Int, total: Int, status: String = "running"
    ) {
        setProgress(workDataOf(
            KEY_PROGRESS_TEXT    to text,
            KEY_PROGRESS_CURRENT to current,
            KEY_PROGRESS_TOTAL   to total,
            KEY_STATUS           to status
        ))
    }

    // ── Folder scanning ──────────────────────────────────────────────────────
    private class ScanResult(
        val files: List<DriveFileInfo>,
        val okCategories: Set<String>,
        val failedCategories: List<String>
    )

    private suspend fun scanAllFolders(): ScanResult {
        val files  = mutableListOf<DriveFileInfo>()
        val ok     = mutableSetOf<String>()
        val failed = mutableListOf<String>()

        for ((category, folderId) in FOLDERS) {
            currentCoroutineContext().ensureActive()
            try {
                val found = fetchFolder(folderId, category)
                files.addAll(found)
                ok.add(category)
                Log.d(TAG, "Folder '$category': ${found.size} files")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed.add(category)
                Log.e(TAG, "Scan failed for '$category': ${e.message}")
            }
        }
        return ScanResult(files, ok, failed)
    }

    /** Lists every file in a folder, following nextPageToken until the listing is complete. */
    private fun fetchFolder(folderId: String, category: String): List<DriveFileInfo> {
        val query  = "'$folderId' in parents and trashed = false"
        val fields = "nextPageToken,files(id,name,mimeType,size,md5Checksum,modifiedTime)"
        val result = mutableListOf<DriveFileInfo>()
        var pageToken: String? = null

        do {
            val tokenForRequest = pageToken   // local val: avoids smart-cast problems on the captured var
            val url = buildString {
                append("https://www.googleapis.com/drive/v3/files")
                append("?q=").append(URLEncoder.encode(query, "UTF-8"))
                append("&pageSize=1000")
                append("&fields=").append(URLEncoder.encode(fields, "UTF-8"))
                append("&key=").append(apiKey)
                if (tokenForRequest != null) append("&pageToken=").append(URLEncoder.encode(tokenForRequest, "UTF-8"))
            }

            val response = http.newCall(
                Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            ).execute()

            response.use {
                if (!response.isSuccessful) throw IOException("HTTP ${response.code} scanning $folderId")
                val json = JSONObject(response.body.string())
                val arr  = json.optJSONArray("files")

                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val obj  = arr.getJSONObject(i)
                        val name = obj.getString("name").trim()
                        val mime = obj.optString("mimeType", "")
                        if (mime == "application/vnd.google-apps.folder") continue

                        val nameLower    = name.lowercase()
                        val isYoutubeTxt = nameLower == "youtube.txt" ||
                                (nameLower.contains("youtube") && nameLower.endsWith(".txt"))
                        val isSettingsJson = nameLower == "settings.json"   // parent's remote settings

                        val size     = obj.optLong("size", 0L)
                        val md5      = obj.optString("md5Checksum", "")
                        val modified = obj.optString("modifiedTime", "")

                        result.add(DriveFileInfo(
                            id        = obj.getString("id"),
                            name      = when { isYoutubeTxt -> "youtube.txt"; isSettingsJson -> "settings.json"; else -> name },
                            category  = if (isYoutubeTxt || isSettingsJson) "config" else category,
                            size      = size,
                            signature = if (md5.isNotEmpty()) md5 else "$modified|$size"
                        ))
                    }
                }
                pageToken = json.optString("nextPageToken", "").ifEmpty { null }
            }
        } while (pageToken != null)

        return result
    }

    // ── Downloading ──────────────────────────────────────────────────────────
    private fun downloadFile(df: DriveFileInfo) {
        if (!inProgressIds.add(df.id)) {
            Log.d(TAG, "Skipping ${df.name} — already in progress")
            return
        }
        try {
            downloadFileInternal(df)
        } finally {
            inProgressIds.remove(df.id)
        }
    }

    private fun downloadFileInternal(df: DriveFileInfo) {
        val dest = File(applicationContext.filesDir, storageName(df.category, df.name))
        val tmp  = File(applicationContext.filesDir, "${dest.name}.tmp")

        val baseUrl = "https://drive.usercontent.google.com/download" +
                      "?id=${df.id}&export=download&authuser=0"

        // First request: the file itself (small files) or a virus-scan warning page (large files).
        val confirmedUrl: String? = http.newCall(
            Request.Builder().url(baseUrl).header("User-Agent", USER_AGENT).build()
        ).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} for ${df.name}")

            val contentType = resp.header("Content-Type", "") ?: ""
            if (!contentType.contains("text/html", ignoreCase = true)) {
                streamBodyToDisk(resp, tmp, dest, df.name)
                return@use null   // already on disk
            }

            // Warning page (~15 KB HTML). Extract the confirm token and uuid from the form.
            val html = resp.body.string()

            val token = Regex("""[?&]confirm=([^&"'\s]+)""").find(html)?.groupValues?.get(1)
                ?: Regex("""name="confirm"\s+value="([^"]+)"""").find(html)?.groupValues?.get(1)
            val uuid = Regex("""[?&]uuid=([^&"'\s]+)""").find(html)?.groupValues?.get(1)
                ?: Regex("""name="uuid"\s+value="([^"]+)"""").find(html)?.groupValues?.get(1)

            if (token == null) Log.w(TAG, "No confirm token found for ${df.name} — retrying without token")

            buildString {
                append(baseUrl)
                if (token != null) append("&confirm=$token")
                if (uuid != null) append("&uuid=$uuid")
            }
        }

        if (confirmedUrl == null) {
            Log.d(TAG, "✓ ${dest.name} (${dest.length()} bytes)")
            return
        }

        // Second request: confirmed download (the CookieJar replays the download_warning cookie).
        http.newCall(
            Request.Builder().url(confirmedUrl).header("User-Agent", USER_AGENT).build()
        ).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} (confirmed) for ${df.name}")

            val contentType = resp.header("Content-Type", "") ?: ""
            if (contentType.contains("text/html", ignoreCase = true)) {
                val snippet = resp.body.string().take(200)
                throw IOException("Still got HTML after confirm for ${df.name}: $snippet")
            }
            streamBodyToDisk(resp, tmp, dest, df.name)
        }
        Log.d(TAG, "✓ ${dest.name} (${dest.length()} bytes)")
    }

    /**
     * Streams a response body to disk in 32 KB chunks (flat heap use for any file size) into a
     * .tmp file, then renames it over the destination so the player never sees a partial file and
     * an existing good copy survives a failed re-download.
     */
    private fun streamBodyToDisk(response: okhttp3.Response, tmp: File, dest: File, fileName: String) {
        val body = response.body
        tmp.delete()
        FileOutputStream(tmp).use { out ->
            body.byteStream().use { input ->
                val buf = ByteArray(32 * 1024)
                var n: Int
                while (input.read(buf).also { n = it } != -1) out.write(buf, 0, n)
            }
        }
        if (tmp.length() == 0L) { tmp.delete(); throw IOException("Zero-byte file for $fileName") }
        if (!tmp.renameTo(dest)) {
            dest.outputStream().use { out -> tmp.inputStream().use { it.copyTo(out) } }
            tmp.delete()
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    private fun storageName(category: String, fileName: String) =
        "${category.lowercase()}_${fileName.lowercase()}".trim()

    data class DriveFileInfo(
        val id: String,
        val name: String,
        val category: String,
        val size: Long,
        /** md5 when Drive provides it, else modifiedTime|size. A change means the file was replaced. */
        val signature: String
    )
}
