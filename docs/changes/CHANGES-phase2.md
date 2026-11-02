# Phase 2 — Foundation (architecture + build upgrade)

> **Build tooling superseded:** the project now uses Gradle 9.4.1 / AGP 9.2.1 (see CHANGES-phase8.md). The version table below is the Phase 2 state.

Builds on the corrected Phase 1. No visible UI changes; behaviour should be identical except where noted.

## Build & dependencies  (all versions live in `gradle/libs.versions.toml`)
| | Before | Now |
|---|---|---|
| Android Gradle Plugin | 8.2.1 | 8.13.0 |
| Gradle wrapper | 8.5 | 8.13 |
| Kotlin | 1.9.24 | 2.2.20 |
| Java / JVM target | 8 | 17 |
| compileSdk / targetSdk | 34 / 34 | 36 / 34 (target stays until Phase 3 — 35+ forces edge-to-edge layouts) |
| Media3 | 1.3.1 | 1.9.0 |
| WorkManager | 2.9.0 | 2.11.2 |
| Core / AppCompat | 1.10.1 / 1.6.1 | 1.17.0 / 1.7.1 |
| New | | DataStore 1.2.1, Lifecycle 2.10.0, Activity-ktx 1.10.1, Coroutines 1.10.2 |
- Every dependency is now in the version catalog (no more inline version strings).
- If Gradle sync says a version doesn't resolve, change it in `libs.versions.toml` only.
- AGP 9 (built-in Kotlin, new DSL) is deliberately NOT adopted yet — it is a separate, riskier migration.

## Architecture
- `FamilySafeTubeApp` + `AppGraph`: process-wide singletons created at startup (also covers background sync).
- **DataStore replaces SharedPreferences** (`SettingsStore`). First launch migrates every old key automatically
  (limits, bedtime, PIN hash, lockout state…). Reads are instant from memory; writes are persisted in order in the background.
- **Usage is stored per day** (`UsageRepository`, keys like `usage_20261006_youtube`). This deletes all manual
  "reset at midnight" code: a new day just starts from zero, a session crossing midnight rolls over, and
  history is kept 90 days (the data Phase 3/4 charts will use).
- `MainViewModel` + `MainUiState`: status badge, usage bar, file counts and sync progress are computed once
  in the ViewModel; `MainActivity` only renders. The WorkManager observer is registered once (it used to
  stack a new one on every resume).
- `Policy`, `UsageTracker`, `GateGuard`, `PinStore` keep their public API, now backed by the new layer.
- Pure, JVM-testable pieces: `PrefsStore`/`Keys`, `UsageKeys`, `UsageRepository`.

## Decision: Room postponed to Phase 4
The app persists ~15 flat settings and a per-day tally; Room would add a compiler plugin (KSP) and a
schema for no benefit. It will be added when playlists/collections (relational data) arrive in Phase 4.

## Behaviour notes
- **One-time reset:** today's usage counters restart at 0 after this upgrade (old counters lived in a different file).
- "Wi-Fi unavailable" now shows a toast instead of leaving the status pill on "Connecting…".

## Tests (35 total)
Run here against a stand-in for the DataStore types: TimeRules, PinHasher, GateRules, NavigationPolicy,
UsageKeys, UsageRepository. NOT run here (no Android SDK): everything that needs Android or the real
DataStore, the Gradle build itself, and the migration.

## What to check when you test
- [ ] Gradle sync succeeds (if not, paste the error — it will be a version in the catalog)
- [ ] Existing settings survive (limits, bedtime, PIN still works, voice-over, Wi-Fi-only)
- [ ] Usage bar moves while watching; after midnight it starts from 0 without opening Settings
- [ ] Reset timer (via gate) zeroes today; yesterday's history is untouched
- [ ] Sync progress shows in the status pill; leaving and returning to the app does not double-update
