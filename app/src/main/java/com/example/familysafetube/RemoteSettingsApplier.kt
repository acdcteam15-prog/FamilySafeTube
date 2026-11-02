package com.example.familysafetube

import android.content.Context
import android.util.Log
import java.io.File
import java.time.LocalDate

/**
 * Applies the parent's `settings.json` (synced from Drive as `config_settings.json`) to the app.
 *
 * Opt-in: nothing happens unless "Apply settings from Drive" is ticked in Settings. When enabled,
 * the fields present in the file overwrite the same settings on every sync (fields left out are
 * untouched, and removing the file does not revert anything). An invalid file changes nothing and is
 * reported in Settings. A `bonus` grants extra minutes today, once per bonus id.
 */
object RemoteSettingsApplier {
    private const val TAG = "RemoteSettings"
    const val LOCAL_NAME = "config_settings.json"

    enum class Status { DISABLED, NO_FILE, APPLIED, INVALID }

    fun apply(context: Context): Status {
        val store = AppGraph.settings
        if (!store.getOr(Keys.REMOTE_ENABLED, false)) return Status.DISABLED

        val file = File(context.filesDir, LOCAL_NAME)
        if (!file.exists()) return Status.NO_FILE
        if (file.length() > RemoteSettingsParser.MAX_FILE_CHARS * 4L) return record(false, "file too large")

        val text = try { file.readText() } catch (e: Exception) { return record(false, "unreadable: ${e.message}") }

        return when (val result = RemoteSettingsParser.parse(text)) {
            is RemoteParse.Invalid -> record(false, result.reason)
            is RemoteParse.Ok -> {
                val s = result.settings
                store.edit { RemoteSettingsParser.applyTo(it, s) }
                s.bonus?.let { b ->
                    if (RemoteSettingsParser.bonusDue(b, store[Keys.BONUS_APPLIED_ID], LocalDate.now())) {
                        AppGraph.usage.addBonus(b.minutes)
                        store.edit { it[Keys.BONUS_APPLIED_ID] = b.id }
                        Log.d(TAG, "Granted ${b.minutes} bonus minutes (id=${b.id})")
                    }
                }
                record(true, "ok")
            }
        }
    }

    private fun record(ok: Boolean, detail: String): Status {
        if (!ok) Log.w(TAG, "settings.json not applied: $detail")
        AppGraph.settings.edit {
            it[Keys.REMOTE_LAST_OK] = ok
            it[Keys.REMOTE_LAST_TS] = System.currentTimeMillis()
        }
        return if (ok) Status.APPLIED else Status.INVALID
    }
}
