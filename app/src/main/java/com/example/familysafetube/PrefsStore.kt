package com.example.familysafetube

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/** Minimal read/write contract the rest of the app depends on (lets tests use an in-memory fake). */
interface PrefsStore {
    fun snapshot(): Preferences
    /** Applies [block] to the current values immediately (in memory) and persists it in order. */
    fun edit(block: (MutablePreferences) -> Unit)
}

operator fun <T> PrefsStore.get(key: Preferences.Key<T>): T? = snapshot()[key]
fun <T> PrefsStore.getOr(key: Preferences.Key<T>, default: T): T = snapshot()[key] ?: default

/** Every persisted setting. Types match what the old SharedPreferences stored, so migration is lossless. */
object Keys {
    val TIME_LIMIT_MINS   = intPreferencesKey("time_limit_mins")
    val BEDTIME_START     = stringPreferencesKey("bedtime_start")
    val BEDTIME_END       = stringPreferencesKey("bedtime_end")
    fun catLimit(category: String) = intPreferencesKey("cat_limit_$category")
    val SYNC_WIFI_ONLY    = booleanPreferencesKey("sync_wifi_only")
    val SYNC_TIME         = stringPreferencesKey("scheduled_sync_time")
    val VOICE_OVER        = booleanPreferencesKey("enable_voice_over")
    val AUTOPLAY_NEXT     = booleanPreferencesKey("autoplay_next_video")
    val BATTERY_PROMPTED  = booleanPreferencesKey("battery_prompt_shown")

    // Weekend schedule
    val WEEKEND_ENABLED   = booleanPreferencesKey("weekend_enabled")
    val WEEKEND_DAYS      = stringPreferencesKey("weekend_days")            // e.g. "SAT,SUN"
    val WEEKEND_LIMIT     = intPreferencesKey("weekend_time_limit_mins")
    val WEEKEND_BED_START = stringPreferencesKey("weekend_bedtime_start")
    val WEEKEND_BED_END   = stringPreferencesKey("weekend_bedtime_end")

    // Remote settings from Drive (settings.json)
    val REMOTE_ENABLED    = booleanPreferencesKey("remote_settings_enabled")
    val REMOTE_LAST_OK    = booleanPreferencesKey("remote_last_ok")
    val REMOTE_LAST_TS    = longPreferencesKey("remote_last_ts")
    val BONUS_APPLIED_ID  = stringPreferencesKey("bonus_applied_id")

    // Quiz
    val QUIZ_ENABLED      = booleanPreferencesKey("quiz_enabled")
    val QUIZ_LANG         = stringPreferencesKey("quiz_language")          // "ar" | "en"
    val QUIZ_SOUND_ONLY   = booleanPreferencesKey("quiz_sound_only")        // include hear-the-animal questions
    val QUIZ_LEVEL        = stringPreferencesKey("quiz_level")             // baby | toddler | older
    val QUIZ_PHONICS      = booleanPreferencesKey("quiz_phonics")          // include letter <-> word questions
    val MATCH_ENABLED     = booleanPreferencesKey("match_enabled")         // show the matching game
    val QUIZ_STICKERS_JSON = stringPreferencesKey("quiz_stickers_json")    // collected sticker ids, in order
    val MANUAL_LOCK       = booleanPreferencesKey("manual_lock")           // the parent pressed "Lock now"
    val BEDTIME_EXTENSION_UNTIL = longPreferencesKey("bedtime_extension_until_ms")   // bedtime lifted by the parent until then
    val QUIZ_ROUNDS_LIMIT = intPreferencesKey("quiz_rounds_limit")         // finished quiz rounds allowed per day, 0 = no limit
    val QUIZ_DECKS        = stringPreferencesKey("quiz_decks")             // enabled built-in decks, e.g. "arletters,numbers"
    val QUIZ_PRAISE_JSON  = stringPreferencesKey("quiz_praise_json")       // the parent's praise-voice clips
    val QUIZ_FAMILY_JSON  = stringPreferencesKey("quiz_family_json")       // the parent's photo cards
    val QUIZ_CARDS_JSON   = stringPreferencesKey("quiz_cards_json")        // the parent's per-card changes
    val QUIZ_STATS_JSON   = stringPreferencesKey("quiz_stats_json")        // per-card shown/correct/wrong

    // In-app updates (GitHub Releases)
    val UPDATE_AUTO_CHECK    = booleanPreferencesKey("auto_update_check")
    val UPDATE_AUTO_DOWNLOAD = booleanPreferencesKey("auto_download_updates")
    val UPDATE_SKIPPED       = intPreferencesKey("update_skipped_build")
    val UPDATE_NOTIFIED      = intPreferencesKey("update_notified_build")
    val UPDATE_LAST_SEEN     = intPreferencesKey("update_last_seen_build")
    val UPDATE_LAST_CHECK    = longPreferencesKey("update_last_check")
    val UPDATE_TOKEN         = stringPreferencesKey("update_github_token")
    val UPDATE_ASKED_NOTIFICATIONS = booleanPreferencesKey("update_asked_notifications")

    // Parental gate
    val PIN_SALT          = stringPreferencesKey("pin_salt")
    val PIN_HASH          = stringPreferencesKey("pin_hash")
    val PIN_LEGACY        = stringPreferencesKey("parental_pin")   // plain-text PIN from very old builds
    val GATE_FAILS        = intPreferencesKey("gate_fail_count")
    val GATE_LOCKOUTS     = intPreferencesKey("gate_lockout_count")
    val GATE_LOCK_UNTIL   = longPreferencesKey("gate_lock_until_ms")
}
