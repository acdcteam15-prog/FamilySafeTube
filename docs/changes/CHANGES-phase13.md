# Phase 13 - GitHub builds and in-app updates

Goal: build, test, sign and publish the app with GitHub Actions, run from two double-click files on Windows,
and let the app update itself from inside. Modelled on a finished sibling project that already has this build and update system.

## Versions (looked up 2026-11-01 in official sources)
Gradle 9.8.2, AGP 9.5.0 (needs Gradle 9.6+, JDK 17+; CI uses Temurin 21), Kotlin 2.4.21, compileSdk 37 (targetSdk stays 34),
core-ktx 1.19.1, appcompat 1.8.0, activity 1.13.0, lifecycle 2.11.0, WorkManager 2.12.0, DataStore 1.3.0, ViewPager2 1.1.0,
Compose 1.12.1, Material3 1.4.0 (still the newest stable), Media3 1.11.1, OkHttp 5.5.1, Glide 5.0.9, coroutines 1.11.0, JUnit 4.13.2.
Compose now uses explicit versions instead of the BOM.

## Library migrations (read from the library source at the new version)
- **OkHttp 4 -> 5.5.1:** `CookieJar`, `Protocol`, `OkHttpClient.Builder` calls unchanged; `Response.body` is now non-null (SyncWorker cleaned).
- **Glide 4.16 -> 5.0.9:** `RequestListener` and the request-builder calls are unchanged.
- **Media3 1.9 -> 1.11.1:** release notes read; the removed APIs (MetadataRetriever, MediaExtractorCompat, FrameExtractor, ...) are not used.
- **Removed:** the Material Components library (only the theme parent used it; the theme is now AppCompat) and glide-transformations
  (unmaintained, RenderScript). The blurred picture background now uses `RenderEffect` on Android 12+ and a tiny scaled-up picture below.
- Removed: `gradle-daemon-jvm.properties` and the foojay plugin (the build uses the JDK that CI installs), the empty androidTest stub.

## Build and release
- `version.properties` (versionName 1.3.0, versionCodeOffset 100); versionCode = offset + GitHub run number; `UPDATE_REPO` from `GITHUB_REPOSITORY`.
- Release signing from `KEYSTORE_FILE / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD` or `keystore.properties`.
- `build.yml` (tests + debug APK), `release.yml` (tests, signed APK, `FamilySafeTube-<ver_with_underscores>-build<code>.apk` + `.sha256`,
  `apksigner` verification, GitHub Release with notes; `[required]` marks a required update), `dependabot.yml`.
- The Drive API key is a repository secret (`DRIVE_API_KEY`) passed to the build; the release warns if it is missing.

## In-app updater (update/ package)
Pure and unit-tested: `UpdateParser` (release JSON -> version/build/notes/required/links), `UpdatePolicy` (when to check, notify, auto-download,
announce). Android: `UpdateRepository` (check, download with SHA-256 + package + versionCode verification, PackageInstaller session with an
install-intent fallback), `UpdateInstallReceiver`, `UpdateCheckWorker` (every 12 hours, Wi-Fi pre-download, one notification per build).
UI (Compose): home-screen banner, Updates dialog (notes, skip this version, "Allow installing from FamilySafeTube", optional GitHub token for
private repositories). Every way in (banner, Settings, notification) goes through the parental PIN. "FamilySafeTube was updated to X" is shown once.
Strings: English and Arabic.

## Wizard (START-HERE.bat, Rebuild-APK.bat, setup/Setup-FamilySafeTube.ps1)
Installs Git and the GitHub CLI with winget; browser sign-in; creates or attaches the repository (a fresh unzip is attached on top of the
existing history, no force push); signing-key choice (Android Studio debug key / your .jks / a new key); prints the key's SHA-1; asks for the
Drive key; secrets via `gh secret set --body`; starts the release, reads the run id from gh's output, watches it, downloads the APK to `APK/`;
short filtered `build-error.txt` on failure; menu entry to follow a running build. Pure ASCII, CRLF; parse-checked with PowerShell 7.6.9.

## Checked here
- 263 JVM unit tests pass (243 existing + 20 new) with the Kotlin 2.4.21 compiler.
- Every Kotlin file parses (0 syntax errors); the identifiers added in this phase all resolve.
- `tools/static_audit.py`: 13 checks, now including Gradle/AGP pairing, workflow rules, APK name vs parser, secrets vs wizard, updater wiring,
  wizard encoding and repo hygiene. Each new rule was proven by deliberately breaking it (23 of 23 caught).

## NOT checked here (no Android SDK, no Windows, no GitHub account in my sandbox)
- No Gradle build, so the first real compile is the GitHub build. That includes all earlier phases, which have never been compiled either.
- The wizard was parse-checked, not run on Windows; the `gh` and `git` steps are the template's proven ones.
- The installer flow, the notification and the Compose screens are untested on a device.

## First GitHub build (real compiler) - fixed
The first build reached `compileDebugKotlin` and reported 3 errors in 2 files, both Compose-specific (invisible without the Android SDK):
- `HomeScreen.kt`: `val x by animateFloatAsState(...)` lacked `import androidx.compose.runtime.getValue`.
- `MatchScreen.kt`: `maxWidth` / `maxHeight` of `BoxWithConstraints` were read inside a nested `Row`; they are now captured first.
The audit has a new check for both classes (`tools/compose_check.py`); it finds no other instance in the project.
