package com.example.familysafetube

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkManager
import kotlinx.coroutines.launch
import com.example.familysafetube.ui.AppRoot
import com.example.familysafetube.ui.FamilySafeTubeTheme
import com.example.familysafetube.ui.HomeActions

/**
 * Home screen host. The UI is Jetpack Compose (see the ui package); this class only owns
 * lifecycle-bound plumbing: the ViewModel, the sync broadcast receiver, the periodic refresh,
 * and the actions that need an Activity (starting other screens, system dialogs).
 */
class MainActivity : AppCompatActivity(), HomeActions {

    private val viewModel: MainViewModel by viewModels()
    private val drive = DriveAccessFlow(this) { sync() }   // Google sign-in for Drive (see DriveAccessFlow)
    private val tickHandler = Handler(Looper.getMainLooper())

    /** True when the update notification was tapped: the UI then asks for the parental PIN and opens the update screen. */
    private var openUpdates by mutableStateOf(false)

    private val liveTick = object : Runnable {
        override fun run() {
            viewModel.refresh()
            // The home screen does not use up time, but bedtime can start (or a bedtime extension end) while it is open:
            // look again exactly then. Otherwise (or when already locked) every 30 seconds is plenty.
            val left = Policy.minutesLeft(this@MainActivity, null)
            val locked = Policy.lockKind(this@MainActivity) != LockKind.NONE
            val base = if (locked || left * 60_000f > 30_000f) 30_000L else LockPolicy.nextCheckDelayMs(left)
            // a "lock for N minutes" should lift exactly when it ends
            val delay = LockPolicy.delayWithTimedLock(base, Policy.timedLockUntilMs(), System.currentTimeMillis())
            tickHandler.postDelayed(this, delay)
        }
    }

    private val syncCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            viewModel.refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The home screen is always dark, so light status/navigation bar icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        openUpdates = intent?.getBooleanExtra(EXTRA_OPEN_UPDATES, false) == true

        UsageTracker.rolloverIfNeeded(this)   // trims usage history older than 90 days
        PinStore.migrateLegacy(this)          // converts an old plain-text PIN to a salted hash

        setContent {
            FamilySafeTubeTheme {
                val state by viewModel.ui.collectAsStateWithLifecycle()
                AppRoot(
                    state = state,
                    actions = this@MainActivity,
                    openUpdates = openUpdates,
                    onOpenUpdatesHandled = { openUpdates = false }
                )
            }
        }

        // Registered once (it used to be re-registered on every resume, stacking observers).
        WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData(SyncWorker.WORK_NAME)
            .observe(this) { infos -> viewModel.onSyncInfos(infos) }

        // Updates: say "updated to X" once after an update, show a finished download, and look for a new build
        // (at most every six hours).
        AppGraph.updates.announceIfJustUpdated()
        AppGraph.updates.restoreReady()
        lifecycleScope.launch { AppGraph.updates.checkOnLaunchIfDue() }

        checkBatteryOptimization()
        SyncWorker.scheduleDaily(this)        // keeps the existing daily schedule (or creates it)
        sync()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finishAffinity()
        })
    }

    // singleTop: tapping the update notification while the app is open arrives here instead of starting a second copy.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_UPDATES, false)) openUpdates = true
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
        tickHandler.postDelayed(liveTick, 1_000L)
        try {
            registerReceiver(
                syncCompleteReceiver,
                IntentFilter("com.example.familysafetube.SYNC_COMPLETE"),
                Context.RECEIVER_NOT_EXPORTED
            )
        } catch (_: Exception) {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(syncCompleteReceiver, IntentFilter("com.example.familysafetube.SYNC_COMPLETE"))
        }
    }

    override fun onPause() {
        super.onPause()
        tickHandler.removeCallbacks(liveTick)
        try { unregisterReceiver(syncCompleteReceiver) } catch (_: Exception) {}
    }

    // ── HomeActions ───────────────────────────────────────────────────────────
    override fun sync() {
        viewModel.onSyncRequested()
        SyncEngine.syncAll(
            this,
            onProgress = { msg -> viewModel.onSyncProgressMessage(msg) },
            onComplete = { started, message ->
                // The worker reports its own progress through WorkManager; this only handles
                // "did not start" cases such as waiting for Wi-Fi.
                if (!started) {
                    viewModel.onSyncNotStarted()
                    message?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
                }
            }
        )
    }

    /**
     * Opens a content mode unless bedtime, the global limit, or that mode's own daily limit blocks it.
     * Only a blocked launch counts as a "tamper attempt".
     */
    override fun tryOpen(category: String): Boolean {
        val reason = Policy.blockReason(this, category)
        if (reason != null) {
            UsageTracker.recordLockAttempt(this)
            Toast.makeText(this, reason, Toast.LENGTH_LONG).show()
            return false
        }
        val left = Policy.categoryRemaining(this, category)
        if (left != null && left <= 5f) {
            Toast.makeText(
                this,
                getString(R.string.min_left_warn, left.toInt(), Policy.categoryLabel(this, category)),
                Toast.LENGTH_SHORT
            ).show()
        }
        return true
    }

    override fun openYoutube() = startActivity(Intent(this, YoutubePlayerActivity::class.java))

    override fun openOffline() = startActivity(Intent(this, OfflinePlayerActivity::class.java))

    override fun openImages(displayName: String, syncCategory: String) {
        startActivity(Intent(this, ImagePlayerActivity::class.java).apply {
            putExtra("CATEGORY_NAME", displayName)
            putExtra("SYNC_CATEGORY", syncCategory)
        })
    }

    override fun openQuiz() {
        if (Policy.quizRoundsReached()) {
            Toast.makeText(this, getString(R.string.quiz_done_today_toast), Toast.LENGTH_LONG).show()
            return
        }
        startActivity(Intent(this, QuizActivity::class.java))
    }

    override fun openMatch() = startActivity(Intent(this, MatchActivity::class.java))

    override fun resetTimer() {
        UsageTracker.resetToday(this)
        Toast.makeText(this, getString(R.string.timer_reset_done), Toast.LENGTH_SHORT).show()
        viewModel.refresh()
    }

    override fun clearStorage() {
        filesDir.listFiles()?.forEach { it.delete() }
        SyncWorker.clearIndex(this)
        Toast.makeText(this, getString(R.string.storage_cleared), Toast.LENGTH_SHORT).show()
        viewModel.refresh()
    }

    override fun settingsSaved() = viewModel.refresh()

    /** "Lock now": the whole app is covered by the lock screen for [minutes], or until the parent opens it (0). */
    override fun lockNow(minutes: Int) {
        Policy.lockNow(minutes)
        viewModel.refresh()
        tickHandler.removeCallbacks(liveTick)
        tickHandler.postDelayed(liveTick, 1_000L)      // re-aim the timer at the end of a timed lock
    }

    /** The parent passed the check on the lock screen: opens a manual lock, or adds [minutes] on a time-up / bedtime lock. */
    override fun unlock(minutes: Int) {
        Policy.unlock(this, minutes)
        viewModel.refresh()
        tickHandler.removeCallbacks(liveTick)
        tickHandler.postDelayed(liveTick, 1_000L)      // re-aim the exact timer at the new deadline
    }

    // ── Google Drive (delegated to DriveAccessFlow) ───────────────────────────
    override fun driveSignIn(done: (String) -> Unit) = drive.driveSignIn(done)
    override fun driveSignOut(done: () -> Unit) = drive.driveSignOut(done)
    override fun driveCheck(create: Boolean, done: (String) -> Unit) = drive.driveCheck(create, done)

    // ── Battery optimization ──────────────────────────────────────────────────
    @Suppress("BatteryLife")
    private fun checkBatteryOptimization() {
        if (AppGraph.settings.getOr(Keys.BATTERY_PROMPTED, false)) return
        AppGraph.settings.edit { it[Keys.BATTERY_PROMPTED] = true }
        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.battery_title))
            .setMessage(getString(R.string.battery_message))
            .setPositiveButton(getString(R.string.action_allow)) { _, _ ->
                try {
                    startActivity(Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = android.net.Uri.parse("package:$packageName")
                    })
                } catch (e: Exception) {
                    startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.fromParts("package", packageName, null)
                    })
                }
            }
            .setNegativeButton(getString(R.string.action_not_now), null).show()
    }

    companion object {
        /** Intent extra set by the update notification. */
        const val EXTRA_OPEN_UPDATES = "open_updates"
    }
}
