package com.example.familysafetube.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.familysafetube.LockKind
import com.example.familysafetube.MainUiState

/** Where the parent currently is. Only one parent dialog is ever open at a time. */
private sealed interface Flow {
    data object None : Flow
    data class Gate(val then: GateNext) : Flow
    data object Settings : Flow
    data object PinMenu : Flow
    data object PinCreate : Flow
    data object ClearConfirm : Flow
    data object Dashboard : Flow
    data object QuizEditor : Flow
    data object Picker : Flow
    /** [fromSettings]: closing returns to Settings (otherwise to the home screen). */
    data class Updates(val fromSettings: Boolean) : Flow
    /** After the PIN on a time-up / bedtime lock: add minutes, open Settings, or stay locked. */
    data object GrantTime : Flow
    /** The home screen's "Lock now": how long to lock for. */
    data object LockChoice : Flow
}

private enum class GateNext { SETTINGS, RESET_TIMER, UPDATES, UNLOCK }

/** Everything the Activity does for the UI (intents, toasts that need Policy, sync). */
interface HomeActions : DriveUi {
    fun sync()
    /** Returns true if the mode may open now (otherwise a toast was shown and a tamper attempt logged). */
    fun tryOpen(category: String): Boolean
    fun openYoutube()
    fun openOffline()
    fun openImages(displayName: String, syncCategory: String)
    fun openQuiz()
    fun openMatch()
    fun resetTimer()
    fun clearStorage()
    fun settingsSaved()
    /** Cover the whole app with the lock screen: [minutes] > 0 for that long, 0 until the parent opens it. */
    fun lockNow(minutes: Int)
    /** Open the lock after the PIN: a manual lock simply opens; otherwise [minutes] of extra time are added. */
    fun unlock(minutes: Int)
}

@Composable
fun AppRoot(
    state: MainUiState,
    actions: HomeActions,
    /** The update notification was tapped: go to the parental PIN, then the update screen. */
    openUpdates: Boolean = false,
    onOpenUpdatesHandled: () -> Unit = {}
) {
    var flow by remember { mutableStateOf<Flow>(Flow.None) }
    val close = { flow = Flow.None }

    LaunchedEffect(openUpdates) {
        if (openUpdates) {
            flow = Flow.Gate(GateNext.UPDATES)
            onOpenUpdatesHandled()
        }
    }

    // When the lock switches on, whatever parent dialog was open is closed: the lock covers everything.
    var wasLocked by remember { mutableStateOf(state.lock != LockKind.NONE) }
    LaunchedEffect(state.lock) {
        val locked = state.lock != LockKind.NONE
        if (locked && !wasLocked) flow = Flow.None
        wasLocked = locked
    }

    if (state.lock != LockKind.NONE) {
        // Nothing of the app is shown while locked, only a padlock. The parent check (phone lock, PIN or maths) opens it.
        LockOverlay(onTap = { flow = Flow.Gate(GateNext.UNLOCK) })
    } else {
        HomeScreen(
            state = state,
            onSync = actions::sync,
            onYoutube = { if (actions.tryOpen(com.example.familysafetube.Policy.CAT_YOUTUBE)) actions.openYoutube() },
            onOffline = { if (actions.tryOpen(com.example.familysafetube.Policy.CAT_VIDEOS)) actions.openOffline() },
            onImages = { if (actions.tryOpen(com.example.familysafetube.Policy.CAT_IMAGES)) flow = Flow.Picker },
            onQuiz = { if (actions.tryOpen(com.example.familysafetube.Policy.CAT_IMAGES)) actions.openQuiz() },
            onMatch = { if (actions.tryOpen(com.example.familysafetube.Policy.CAT_IMAGES)) actions.openMatch() },
            onUpdates = { flow = Flow.Gate(GateNext.UPDATES) },
            onSettings = { flow = Flow.Gate(GateNext.SETTINGS) },
            onResetTimer = { flow = Flow.Gate(GateNext.RESET_TIMER) },
            onLockNow = { flow = Flow.LockChoice }
        )
    }

    when (val f = flow) {
        Flow.None -> Unit

        is Flow.Gate -> ParentGate(
            onSuccess = {
                when (f.then) {
                    GateNext.SETTINGS -> flow = Flow.Settings
                    GateNext.RESET_TIMER -> { actions.resetTimer(); flow = Flow.None }
                    GateNext.UPDATES -> flow = Flow.Updates(fromSettings = false)
                    GateNext.UNLOCK ->
                        if (state.lock == LockKind.MANUAL) { actions.unlock(0); flow = Flow.None } else flow = Flow.GrantTime
                }
            },
            onDismiss = close
        )

        Flow.Settings -> SettingsDialog(
            onDismiss = close,
            onSaved = { actions.settingsSaved(); flow = Flow.None },
            onPin = { flow = Flow.PinMenu },
            onClearStorage = { flow = Flow.ClearConfirm },
            onDashboard = { flow = Flow.Dashboard },
            onQuizEditor = { flow = Flow.QuizEditor },
            onUpdates = { flow = Flow.Updates(fromSettings = true) },
            drive = actions
        )

        Flow.PinMenu -> PinMenuDialog(
            onDismiss = { flow = Flow.Settings },
            onCreate = { flow = Flow.PinCreate }
        )

        Flow.PinCreate -> PinCreateDialog(onDismiss = { flow = Flow.Settings })

        Flow.ClearConfirm -> ClearStorageDialog(
            onConfirm = { actions.clearStorage(); flow = Flow.Settings },
            onDismiss = { flow = Flow.Settings }
        )

        Flow.Dashboard -> DashboardDialog(onDismiss = { flow = Flow.Settings })

        Flow.QuizEditor -> QuizEditorDialog(onDismiss = { flow = Flow.Settings })

        is Flow.Updates -> UpdatesDialog(onDismiss = { flow = if (f.fromSettings) Flow.Settings else Flow.None })

        Flow.GrantTime -> GrantTimeDialog(
            onGrant = { minutes -> actions.unlock(minutes); flow = Flow.None },
            onOpenSettings = { flow = Flow.Settings },
            onDismiss = close
        )

        Flow.LockChoice -> LockChoiceDialog(
            onPick = { minutes -> flow = Flow.None; actions.lockNow(minutes) },
            onDismiss = close
        )

        Flow.Picker -> CategoryPickerDialog(
            onPick = { display, category -> flow = Flow.None; actions.openImages(display, category) },
            onDismiss = close
        )
    }
}
