package com.example.familysafetube.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.example.familysafetube.Keys
import com.example.familysafetube.PrefsStore
import com.example.familysafetube.R
import com.example.familysafetube.getOr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val versionName: String, val build: Int) : UpdateState
    data class Available(val info: ReleaseInfo) : UpdateState
    /** [fraction] is 0..1, or -1 when the size isn't known. */
    data class Downloading(val info: ReleaseInfo, val fraction: Float) : UpdateState
    data class Ready(val info: ReleaseInfo, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/** The HTTP client the updater uses (kept apart from the Drive-sync client: different timeouts, no cookies). */
internal object UpdateHttp {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}

/**
 * Looks for a newer build in this app's GitHub Releases (see .github/workflows/release.yml), downloads it,
 * checks it, and hands it to Android's installer. The release's APK file name carries the build number
 * (FamilySafeTube-1_3_0-build101.apk); a build is newer when its number is above the installed versionCode.
 * The decisions themselves live in [UpdatePolicy] and [UpdateParser] (unit-tested).
 */
class UpdateRepository(
    private val context: Context,
    private val client: OkHttpClient,
    private val settings: PrefsStore,
    /** "owner/repo" of the GitHub repository the app was built in. */
    val repo: String,
    val currentBuild: Int,
    val currentVersion: String
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    // Lives as long as the process, so a started download is not cancelled when the update screen is closed.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _skipped = MutableStateFlow(settings.getOr(Keys.UPDATE_SKIPPED, 0))
    /** A build the parent chose to skip: no banner / notification for it (a newer build shows again). */
    val skippedBuild: StateFlow<Int> = _skipped

    // ---- settings (read straight from the store; it keeps them in memory) ----
    var autoCheck: Boolean
        get() = settings.getOr(Keys.UPDATE_AUTO_CHECK, true)
        set(value) = settings.edit { it[Keys.UPDATE_AUTO_CHECK] = value }

    /** Download a new build in the background on an unmetered network so installing it is one tap. */
    var autoDownload: Boolean
        get() = settings.getOr(Keys.UPDATE_AUTO_DOWNLOAD, true)
        set(value) = settings.edit { it[Keys.UPDATE_AUTO_DOWNLOAD] = value }

    /** Optional fine-grained GitHub token (Contents: read): only needed if the repository is private. */
    var token: String
        get() = settings.getOr(Keys.UPDATE_TOKEN, "")
        set(value) = settings.edit { it[Keys.UPDATE_TOKEN] = UpdatePolicy.cleanToken(value) }

    var notifiedBuild: Int
        get() = settings.getOr(Keys.UPDATE_NOTIFIED, 0)
        set(value) = settings.edit { it[Keys.UPDATE_NOTIFIED] = value }

    val lastCheck: Long get() = settings.getOr(Keys.UPDATE_LAST_CHECK, 0L)

    private val updateDir get() = File(context.cacheDir, "updates").apply { mkdirs() }
    private val apkFile get() = File(updateDir, "FamilySafeTube.apk")

    private fun request(url: String, token: String, accept: String): Request = Request.Builder()
        .url(url)
        .header("Accept", accept)
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("User-Agent", "FamilySafeTube/$currentVersion")
        .apply { if (token.isNotBlank()) header("Authorization", "Bearer $token") }
        .build()

    private fun text(id: Int, vararg args: Any): String = context.getString(id, *args)

    /** Asks GitHub for the latest release and returns (and publishes) the outcome. */
    suspend fun check(): UpdateState {
        if (_state.value is UpdateState.Downloading) return _state.value
        _state.value = UpdateState.Checking
        val result = withContext(Dispatchers.IO) { runCatching { fetchLatest() } }
        settings.edit { it[Keys.UPDATE_LAST_CHECK] = System.currentTimeMillis() }
        val next = result.fold(
            onSuccess = { info ->
                if (info != null && UpdateParser.isNewer(info.build, currentBuild)) {
                    // A finished download of exactly this build may already be waiting.
                    val waiting = apkFile.takeIf { it.exists() && it.length() > 0 }
                    val sameBuild = waiting?.let { archiveInfo(it) }?.takeIf { a ->
                        a.packageName == context.packageName && PackageInfoCompat.getLongVersionCode(a) == info.build.toLong()
                    }
                    if (waiting != null && sameBuild != null) UpdateState.Ready(info, waiting) else UpdateState.Available(info)
                } else UpdateState.UpToDate(currentVersion, currentBuild)
            },
            onFailure = { UpdateState.Failed(it.message ?: text(R.string.update_err_check)) }
        )
        _state.value = next
        return next
    }

    /** For buttons: start a check / download without waiting for it. */
    fun checkAsync() { scope.launch { check() } }
    fun downloadAsync(info: ReleaseInfo) { scope.launch { download(info) } }

    private fun archiveInfo(file: File) = runCatching { context.packageManager.getPackageArchiveInfo(file.absolutePath, 0) }.getOrNull()

    private fun fetchLatest(): ReleaseInfo? {
        if (!UpdatePolicy.isConfigured(repo)) throw IOException(text(R.string.update_err_not_configured))
        val authToken = token
        client.newCall(request(UpdatePolicy.latestReleaseUrl(repo), authToken, "application/vnd.github+json")).execute().use { r ->
            when {
                r.code == 404 -> throw IOException(
                    text(if (authToken.isBlank()) R.string.update_err_none_private else R.string.update_err_none)
                )
                r.code == 401 -> throw IOException(text(R.string.update_err_token))
                r.code == 403 || r.code == 429 -> throw IOException(text(R.string.update_err_rate))
                !r.isSuccessful -> throw IOException(text(R.string.update_err_http, r.code))
            }
            return UpdateParser.parseRelease(r.body.string())
        }
    }

    /** Downloads the APK of [info], checks its hash and identity, and returns it as [UpdateState.Ready]. */
    suspend fun download(info: ReleaseInfo): UpdateState = withContext(Dispatchers.IO) {
        val authToken = token
        _state.value = UpdateState.Downloading(info, if (info.sizeBytes > 0) 0f else -1f)
        val outcome = runCatching {
            val file = apkFile.also { it.delete() }
            // Private repositories need the API address with the token; public ones use the plain link.
            val url = if (authToken.isNotBlank() && info.apkApiUrl != null) info.apkApiUrl else info.apkUrl
            if (!UpdatePolicy.isSecureUrl(url)) throw IOException(text(R.string.update_err_not_https))

            val digest = MessageDigest.getInstance("SHA-256")
            client.newCall(request(url, authToken, "application/octet-stream")).execute().use { r ->
                if (!r.isSuccessful) throw IOException(text(R.string.update_err_download_http, r.code))
                val total = r.body.contentLength().takeIf { it > 0 } ?: info.sizeBytes
                var done = 0L
                var lastEmit = 0L
                r.body.byteStream().use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            done += n
                            val now = System.currentTimeMillis()
                            if (now - lastEmit > 200) {
                                lastEmit = now
                                _state.value = UpdateState.Downloading(info, if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else -1f)
                            }
                        }
                    }
                }
            }

            // Integrity: compare with the published checksum when there is one.
            val expected = fetchChecksum(info, authToken)
            val actual = digest.digest().joinToString("") { String.format(Locale.ROOT, "%02x", it) }
            if (expected != null && !expected.equals(actual, ignoreCase = true)) {
                file.delete()
                throw IOException(text(R.string.update_err_checksum))
            }

            // Identity: it must be this app, at the build the release promised.
            val archive = archiveInfo(file) ?: run { file.delete(); throw IOException(text(R.string.update_err_invalid)) }
            if (archive.packageName != context.packageName) { file.delete(); throw IOException(text(R.string.update_err_other_app)) }
            if (PackageInfoCompat.getLongVersionCode(archive) != info.build.toLong()) { file.delete(); throw IOException(text(R.string.update_err_version)) }
            file
        }
        val next = outcome.fold(
            onSuccess = { UpdateState.Ready(info, it) },
            onFailure = { UpdateState.Failed(it.message ?: text(R.string.update_err_failed)) }
        )
        _state.value = next
        next
    }

    private fun fetchChecksum(info: ReleaseInfo, token: String): String? {
        val url = (if (token.isNotBlank()) info.sha256ApiUrl else null) ?: info.sha256Url ?: return null
        return runCatching {
            client.newCall(request(url, token, "application/octet-stream")).execute().use { r ->
                if (r.isSuccessful) UpdateParser.parseSha256(r.body.string()) else null
            }
        }.getOrNull()
    }

    /**
     * Installs [file] through Android's PackageInstaller. After the first manual install Android 12+ lets an app that
     * installed itself update itself without another prompt; otherwise the system shows its usual "Update" dialog
     * (the receiver opens it). Falls back to the classic install intent if a session cannot be created.
     */
    fun install(file: File) {
        UpdateInstallResult.failure.value = null
        val started = runCatching {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val id = installer.createSession(params)
            installer.openSession(id).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("update.apk", 0, file.length()).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
                val callback = Intent(context, UpdateInstallReceiver::class.java).setAction(UpdateInstallReceiver.ACTION)
                val pending = PendingIntent.getBroadcast(context, id, callback, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                session.commit(pending.intentSender)
            }
        }.isSuccess
        if (!started) context.startActivity(installIntent(file))
    }

    /** A finished download from an earlier run (the app was closed since): show it as ready to install. */
    fun restoreReady() {
        if (_state.value !is UpdateState.Idle && _state.value !is UpdateState.UpToDate) return
        val file = apkFile.takeIf { it.exists() && it.length() > 0 } ?: return
        val archive = archiveInfo(file) ?: return
        val build = PackageInfoCompat.getLongVersionCode(archive)
        if (archive.packageName != context.packageName || build <= currentBuild) return
        _state.value = UpdateState.Ready(
            ReleaseInfo(
                versionName = archive.versionName ?: "", build = build.toInt(), notes = "", required = false,
                apkUrl = "", apkApiUrl = null, sizeBytes = file.length(), sha256Url = null, sha256ApiUrl = null, publishedAt = null
            ),
            file
        )
    }

    /** Android asks the user once to allow installs from an app ("Allow installing from FamilySafeTube"). */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun allowInstallIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun installIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Back to "nothing shown" after a failed attempt, so the buttons reappear. */
    fun reset() { _state.value = UpdateState.Idle }

    /** The parent chose not to install [info]: hide its banner and notification (a newer build still shows). */
    fun skip(info: ReleaseInfo) {
        settings.edit { it[Keys.UPDATE_SKIPPED] = info.build }
        _skipped.value = info.build
        reset()
    }

    /** After an update: say so once ("FamilySafeTube was updated to 1.3.0 (build 102)"), then remember this build. */
    fun announceIfJustUpdated() {
        val last = settings.getOr(Keys.UPDATE_LAST_SEEN, 0)
        if (UpdatePolicy.justUpdated(last, currentBuild)) {
            val message = text(R.string.update_done, text(R.string.app_name), currentVersion, currentBuild)
            Handler(Looper.getMainLooper()).post { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
            runCatching { File(context.cacheDir, "updates").deleteRecursively() }   // the installed APK isn't needed any more
        }
        if (last != currentBuild) settings.edit { it[Keys.UPDATE_LAST_SEEN] = currentBuild }
    }

    /** One automatic check per launch: only if switched on, not done in the last six hours, and nothing is on screen yet. */
    suspend fun checkOnLaunchIfDue() {
        if (autoCheck && UpdatePolicy.checkDue(System.currentTimeMillis(), lastCheck) && _state.value is UpdateState.Idle) check()
    }
}
