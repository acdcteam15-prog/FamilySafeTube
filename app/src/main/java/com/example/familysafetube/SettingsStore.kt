package com.example.familysafetube

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * First start after the upgrade copies every key from the old "SafeTubePrefs" SharedPreferences
 * into DataStore (settings, PIN hash, gate state) and then clears the old file.
 */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    produceMigrations = { ctx -> listOf(SharedPreferencesMigration(ctx, "SafeTubePrefs")) }
)

/**
 * DataStore-backed [PrefsStore].
 *
 * Reads are synchronous and served from an in-memory StateFlow (the enforcement code in the player
 * activities needs instant answers). Writes update memory first, then are persisted to DataStore in
 * the exact order they were made by a single background consumer. DataStore is loaded once at app
 * start ([create]); that one blocking read is the price of keeping every caller simple.
 */
class SettingsStore private constructor(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
    initial: Preferences
) : PrefsStore {

    private val state = MutableStateFlow(initial)
    private val pending = Channel<(MutablePreferences) -> Unit>(Channel.UNLIMITED)

    /** Observe changes (used by ViewModels). */
    val changes: StateFlow<Preferences> = state.asStateFlow()

    init {
        scope.launch {
            for (block in pending) {
                try {
                    dataStore.edit { block(it) }
                } catch (e: Exception) {
                    Log.e("SettingsStore", "Persist failed: ${e.message}", e)
                }
            }
        }
    }

    override fun snapshot(): Preferences = state.value

    override fun edit(block: (MutablePreferences) -> Unit) {
        state.update { current -> current.toMutablePreferences().also(block).toPreferences() }
        pending.trySend(block)
    }

    companion object {
        fun create(context: Context): SettingsStore {
            val store = context.applicationContext.settingsDataStore
            val initial = runBlocking { store.data.first() }
            return SettingsStore(store, CoroutineScope(SupervisorJob() + Dispatchers.IO), initial)
        }
    }
}
