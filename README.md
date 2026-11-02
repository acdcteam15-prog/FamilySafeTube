# FamilySafeTube

<p align="center"><img src="docs/logo.png" width="160" alt="FamilySafeTube logo"></p>

A parental-control media app for children (Android, Kotlin, Jetpack Compose). Content comes from Google Drive
folders you control; the parent sets time limits, bedtime, a weekend schedule, and optional bonus time.

## Modes
- **YouTube mode** – a curated list of Shorts (`youtube.txt`); navigation outside the list is blocked.
- **Offline mode** – downloaded videos in a vertical pager (one shared ExoPlayer), loop or autoplay.
- **Images mode** – animals / letters / vegetables & fruits / other, with optional voice-over.
- **Matching mode** – tap two that go together: a letter and the picture that starts with it, a digit and that many stars, or an animal's sound and its picture. Finishing a board can win a sticker.
- **Quiz mode** – hear the name (and the animal's sound), or just the animal's sound, then tap the right picture out of 2-3. Arabic or English. After 5 right answers a calm "well done" screen offers Again or Bye-bye. Counts as Images time under the normal limits. Cards are edited in the app (see below).

## Parent controls (Settings, behind a PIN or maths gate)
Daily limit, per-category limits, bedtime, weekend schedule, Wi-Fi-only sync, voice-over, autoplay,
quiz rewards, remote control from Drive, PIN, clear storage, and an activity dashboard (7-day chart).
Wrong gate answers lock out with an escalating, persistent delay (1 min → 5 → 15 → 1 h).

## Build and install (no Android Studio needed)
Double-click **START-HERE.bat** and choose 1: it installs what it needs, builds the signed APK on GitHub and downloads it.
Later: **Rebuild-APK.bat**, or *Settings > App updates* on the phone (behind the parent PIN). Details: **[SETUP.md](SETUP.md)**.

> Play Protect may show its own "unknown app" prompt for any sideloaded APK: tap **More details > Install anyway**.

## Setup
1. Create a Google Cloud API key restricted to the **Drive API** and to this app (package `com.example.familysafetube` + the signing key's SHA-1, which the wizard prints).
   The wizard asks for it and stores it as a GitHub secret (`DRIVE_API_KEY`); it is never in source.
   For builds on your own PC put `DRIVE_API_KEY=your_key` in `local.properties` (see `local.properties.example`).
2. Drive layout (folder IDs are constants `FOLDER_*` in `SyncWorker.kt`; the folders must be shared "anyone with the link: viewer"):
   - videos folder: your `.mp4/.mkv/.mov/.webm` files, `youtube.txt` (one Shorts URL/ID per line), optional `settings.json`
   - one folder each for animals, letters, veg&fruits, other (images; file names like `animals_lion.png`)
3. First launch syncs automatically; a daily sync runs at the time set in Settings.

## Quiz cards (in-app editor)
Settings → **Edit quiz cards**. Every picture in the animals and veg&fruits folders is a card; the quiz works with no editing
(Arabic names for ~90 common animals/fruits/vegetables are built in and spoken by the phone's voice).
Per card you can change the Arabic/English name, switch it on or off, and attach **your own voice** saying the name (record from the
phone microphone, or import a file) and an **animal sound** (10 are built in; record or import others). The quiz language
(Arabic or English) is chosen in Settings. Wrong answers come back more often, and after 3 misses in a row the question
gets easier (2 pictures). The dashboard shows which words are learned and which need practice. Also in Settings → Edit quiz cards: **built-in letter and number decks** (Arabic letters, English letters, numbers, counting; switched on in Settings), **your own praise voice** for right / wrong / round finished, **family cards** made from your own photos, **focus words** (★, asked more often for a week) and a **quiz level** (Baby, Toddler, Older). Sound credits: `docs/SOUND-CREDITS.md`.

## Remote control (`settings.json`)
Enable "Apply settings from Drive" in Settings. Every key is optional; an invalid file is ignored entirely.
```json
{
  "version": 1,
  "time_limit_mins": 45,
  "bedtime_start": "20:00", "bedtime_end": "07:00",
  "category_limits": { "youtube": 20, "offline_videos": 0, "images": 30 },
  "weekend": { "enabled": true, "time_limit_mins": 90, "bedtime_start": "21:30",
               "bedtime_end": "08:00", "days": ["SAT", "SUN"] },
  "bonus": { "id": "2026-10-07-a", "minutes": 15, "expires": "2026-10-07" }
}
```
`bonus` is granted once per `id`. Anyone who can *edit* the Drive folder can change the limits.

## Architecture
```
ui/            Compose screens (Home, Settings, Gate, Dashboard, Quiz) + theme
MainViewModel  home-screen state;   Policy  "may the child use X now?";   Schedule  weekday/weekend rules
SettingsStore  DataStore-backed PrefsStore (in-memory reads, ordered background writes)
UsageRepository  per-day usage / bonus / lock attempts (history for the dashboard)
SyncWorker     Drive → local mirror (paginated, change-detecting, deletion-guarded) + RemoteSettingsApplier
*Player*       view-based full-screen players (Immersive, WindDownMonitor, UsageTracker.Session)
```
Pure-JVM logic (unit-tested): `TimeRules, Schedule, WindDown, GateRules, PinHasher, NavigationPolicy,
UsageKeys, UsageRepository, SettingsForm, MiniJson, RemoteSettings, QuizEngine, QuizCards, QuizCatalog, QuizAudioPlan, QuizCardStore, QuizProgress, QuizImageLayout, QuizDecks, QuizLevel, QuizPraise, QuizFamily, PhotoMath, Phonics, QuizStickers, QuizMatch, ToneSynth, UpdateParser, UpdatePolicy`.

## Build tooling
Gradle **9.8.2** · Android Gradle Plugin **9.5.0** (needs Gradle 9.6+ and JDK 17+; CI uses JDK 21) · Kotlin 2.4.21 (built in to AGP; used by the Compose compiler plugin) · compileSdk 37 · targetSdk 34 · minSdk 26.
Every version lives in `gradle/libs.versions.toml`; Dependabot proposes updates weekly. The audit enforces the Gradle/AGP pairing (see below).
Libraries: Media3 1.11.1, OkHttp 5.5.1, Glide 5.0.9, WorkManager 2.12.0, DataStore 1.3.0, Compose 1.12.1 / Material3 1.4.0.

## Quality checks
```
python3 tools/static_audit.py     # resources, strings (en/ar), manifest, interfaces, catalog – needs only Python
./gradlew testDebugUnitTest       # JVM unit tests
./gradlew assembleDebug assembleRelease
```
GitHub Actions (`.github/workflows/build.yml` on every push, `release.yml` for the signed release) run the tests and build the APK.

## Languages
English and Arabic (right-to-left). Strings live in `res/values/strings.xml` and `res/values-ar/strings.xml`
and must stay in sync (the audit enforces it). The Arabic is a first-pass translation and should be reviewed.
