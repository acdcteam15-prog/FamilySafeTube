package com.example.familysafetube.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
}

private enum class GateNext { SETTINGS, RESET_TIMER, UPDATES }

/** Everything the Activity does for the UI (intents, toasts that need Policy, sync). */
interface HomeActions {
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
        onResetTimer = { flow = Flow.Gate(GateNext.RESET_TIMER) }
    )

    when (val f = flow) {
        Flow.None -> Unit

        is Flow.Gate -> ParentGate(
            onSuccess = {
                when (f.then) {
                    GateNext.SETTINGS -> flow = Flow.Settings
                    GateNext.RESET_TIMER -> { actions.resetTimer(); flow = Flow.None }
                    GateNext.UPDATES -> flow = Flow.Updates(fromSettings = false)
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
            onUpdates = { flow = Flow.Updates(fromSettings = true) }
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

        Flow.Picker -> CategoryPickerDialog(
            onPick = { display, category -> flow = Flow.None; actions.openImages(display, category) },
            onDismiss = close
        )
    }
}
