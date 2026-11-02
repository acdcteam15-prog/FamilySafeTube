package com.example.familysafetube.update

/** The decisions the updater makes, as pure functions (unit-tested; the Android code only calls them). */
object UpdatePolicy {
    /** The home screen checks on launch only if the last check is older than this. */
    const val LAUNCH_CHECK_INTERVAL_MS = 6L * 3_600_000L

    /** The background check runs this often (WorkManager's minimum is 15 minutes). */
    const val BACKGROUND_CHECK_HOURS = 12L

    /** True when it is time to look again. A clock that was set back counts as "due" rather than "never". */
    fun checkDue(nowMs: Long, lastCheckMs: Long, intervalMs: Long = LAUNCH_CHECK_INTERVAL_MS): Boolean =
        lastCheckMs <= 0L || lastCheckMs > nowMs || nowMs - lastCheckMs > intervalMs

    /** One notification per build, and none for a build the parent chose to skip. */
    fun shouldNotify(build: Int, skippedBuild: Int, notifiedBuild: Int): Boolean =
        build != skippedBuild && build != notifiedBuild

    /** Fetch the new build in the background only on an unmetered network, and only if installing is already allowed. */
    fun shouldAutoDownload(autoDownload: Boolean, installAllowed: Boolean, metered: Boolean, alreadyDownloaded: Boolean): Boolean =
        !alreadyDownloaded && autoDownload && installAllowed && !metered

    /** True once, right after the app was replaced by a newer build. */
    fun justUpdated(lastSeenBuild: Int, currentBuild: Int): Boolean = lastSeenBuild in 1 until currentBuild

    /** The release to advertise on the home screen, or null (nothing new, or the parent skipped it). */
    fun bannerRelease(available: ReleaseInfo?, skippedBuild: Int): ReleaseInfo? = available?.takeIf { it.build != skippedBuild }

    /** "owner/repo" as GitHub writes it. The placeholder written into a build made outside GitHub does not count. */
    fun isConfigured(repo: String): Boolean {
        if (repo.isBlank() || repo.startsWith("CHANGE_ME")) return false
        val parts = repo.split('/')
        return parts.size == 2 && parts.all { it.isNotBlank() }
    }

    fun latestReleaseUrl(repo: String): String = "https://api.github.com/repos/$repo/releases/latest"

    /** Only https downloads are accepted. */
    fun isSecureUrl(url: String): Boolean = url.startsWith("https://")

    /** A token is trimmed; a blank one means "public repository". */
    fun cleanToken(raw: String): String = raw.trim()
}
