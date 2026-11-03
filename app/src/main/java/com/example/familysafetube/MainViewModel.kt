package com.example.familysafetube

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class HomeStatus { ACTIVE, BEDTIME, LIMIT_REACHED, SYNCING, SYNC_FAILED }

/** Quiz card on the home screen: hidden by the parent, not enough pictures yet, ready to play, or all of today's rounds are done. */
enum class QuizState { HIDDEN, NOT_READY, READY, DONE_TODAY }

/** Everything the home screen shows, in one immutable snapshot. */
data class MainUiState(
    val status: HomeStatus = HomeStatus.ACTIVE,
    /** Localized progress text while [status] is SYNCING (e.g. "Downloading…  (3/10)"), else null. */
    val syncMessage: String? = null,
    val limitMins: Int = 30,
    val usedMins: Float = 0f,
    val youtubeCount: Int = 0,
    val offlineCount: Int = 0,
    val imagesCount: Int = 0,
    val quiz: QuizState = QuizState.HIDDEN,
    /** The matching game card (parent can hide it in Settings). */
    val matchVisible: Boolean = true,
    /** Not NONE: the whole app is covered by the lock screen (time is up, bedtime, or the parent pressed "Lock now"). */
    val lock: LockKind = LockKind.NONE
)

/**
 * Owns the home-screen state. The Activity only renders [ui] and forwards user actions, so rotation
 * or process recreation no longer re-derives everything by hand, and the rules live in [Policy].
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    // The lock is known at once (it only reads in-memory settings), so a locked app never flashes its home screen.
    private val _ui = MutableStateFlow(MainUiState(lock = Policy.lockKind(app)))
    val ui: StateFlow<MainUiState> = _ui.asStateFlow()

    private data class Counts(val youtube: Int, val offline: Int, val images: Int, val quizPlayable: Boolean)

    /** Non-null while a sync is running (text shown in the status pill). */
    private var syncText: String? = null
    private var syncFailed = false
    private var counts = Counts(0, 0, 0, false)

    /** Recompute everything from current settings, usage and files. Call on resume and on a timer. */
    fun refresh() {
        // Update the lock first and synchronously: coming back to a locked app must not show the home screen even for a moment.
        _ui.value = _ui.value.copy(lock = Policy.lockKind(getApplication<Application>()))
        syncFailed = false
        viewModelScope.launch {
            counts = withContext(Dispatchers.IO) { readCounts() }
            publish()
        }
    }

    /** User tapped SYNC. */
    fun onSyncRequested() {
        syncText = getApplication<Application>().getString(R.string.sync_connecting)
        publish()
    }

    fun onSyncProgressMessage(message: String) {
        syncText = message
        publish()
    }

    /** The sync request was rejected or finished immediately (e.g. waiting for Wi-Fi). */
    fun onSyncNotStarted() {
        syncText = null
        publish()
    }

    /** Feeds WorkManager's state for the manual sync job. */
    fun onSyncInfos(infos: List<WorkInfo>?) {
        if (infos.isNullOrEmpty()) return
        val running = infos.find { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
        if (running != null) {
            val progress = running.progress
            val text     = progress.getString(SyncWorker.KEY_PROGRESS_TEXT)
            val current  = progress.getInt(SyncWorker.KEY_PROGRESS_CURRENT, 0)
            val total    = progress.getInt(SyncWorker.KEY_PROGRESS_TOTAL, 0)
            val ctx = getApplication<Application>()
            syncText = when {
                text != null && total > 0 -> ctx.getString(R.string.sync_progress, text, current, total)
                text != null              -> text
                else                      -> ctx.getString(R.string.sync_running)
            }
            publish()
            return
        }
        syncText = null
        when (infos.maxByOrNull { it.state.ordinal }?.state) {
            WorkInfo.State.FAILED -> { refresh(); syncFailed = true; publish() }
            else                  -> refresh()
        }
    }

    // ── internals ────────────────────────────────────────────────────────────
    private fun publish() {
        val ctx = getApplication<Application>()
        val sync = syncText
        val status = when {
            sync != null                      -> HomeStatus.SYNCING
            syncFailed                        -> HomeStatus.SYNC_FAILED
            Policy.isBedtime(ctx)             -> HomeStatus.BEDTIME
            Policy.isGlobalLimitReached(ctx)  -> HomeStatus.LIMIT_REACHED
            else                              -> HomeStatus.ACTIVE
        }
        _ui.value = MainUiState(
            status       = status,
            syncMessage  = sync,
            limitMins    = Policy.globalLimit(ctx),
            usedMins     = Policy.globalElapsed(ctx),
            youtubeCount = counts.youtube,
            offlineCount = counts.offline,
            imagesCount  = counts.images,
            matchVisible = AppGraph.settings.getOr(Keys.MATCH_ENABLED, true),
            lock         = Policy.lockKind(ctx),
            quiz         = when {
                !AppGraph.settings.getOr(Keys.QUIZ_ENABLED, true) -> QuizState.HIDDEN
                !counts.quizPlayable                              -> QuizState.NOT_READY
                Policy.quizRoundsReached()                        -> QuizState.DONE_TODAY
                else                                              -> QuizState.READY
            }
        )
    }

    private fun readCounts(): Counts {
        val ctx = getApplication<Application>()
        val config = File(ctx.filesDir, "config_youtube.txt")
        val yt = if (config.exists() && config.length() > 0)
            config.readText().lines().count { it.trim().isNotEmpty() } else 0
        val offline = SyncEngine.getLocalFiles(ctx, "videos").count { !it.name.endsWith(".txt") }
        val images = listOf("animals", "letters", "veg&fruits", "other")
            .sumOf { SyncEngine.getLocalFiles(ctx, it).size }
        val quizPlayable = QuizEngine.isPlayable(QuizRepository.loadCards(ctx))
        return Counts(yt, offline, images, quizPlayable)
    }
}
