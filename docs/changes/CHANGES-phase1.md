# Phase 1 — Stabilize (no new features, no UI changes)

> **Re-issued build.** The first Phase 1 zip was missing `updateAll`, `updateStatusBadge`, `updateUsageBar`, `updateFileCounts` and `openMode` in `MainActivity` (my edit removed them by mistake) and would not compile. This zip restores them; every function and call was re-audited against the original.

## One-time setup (required — sync will not run without it)
1. **Rotate the Drive API key.** The old one was in the source and in shared zips.
   Google Cloud Console → APIs & Services → Credentials → create a new key, restrict it to
   *Google Drive API* and *Android apps* (package `com.example.familysafetube` + your SHA-1), then delete the old key.
2. Add it to `local.properties` (git-ignored; Android Studio keeps `sdk.dir` there):
   `DRIVE_API_KEY=your_new_key`   (see `local.properties.example`) — then **Sync Project with Gradle Files**.
3. Uninstall nothing — settings, PIN (auto-migrated to a hash) and downloaded files carry over.

## Sync (SyncWorker)
- Deletion sync no longer wipes files when a folder scan fails, is truncated, or returns empty while files exist locally.
- Drive listings are paginated (previously stopped at the first page).
- Files replaced in Drive under the same name are re-downloaded (md5/modifiedTime/size check).
- `config_youtube.txt` is replaced atomically instead of deleted before the scan.
- Cancellable coroutine download loop (no blocking latch); progress shows "Downloading… (3/10)".
- Daily schedule is now a 24h PeriodicWork (old chain cancelled itself and broke when everything was up to date).
- "Sync on Wi-Fi only" now also applies to the scheduled sync.
- Missing API key fails fast with a clear message.

## Parental controls
- Per-category usage and tamper count now reset at midnight (only the global timer did before).
- **Images limit never worked** (time was recorded as animals/letters/…; limit read "images"). Fixed.
- Bedtime, global limit and per-category limits are enforced *inside* players too, and on return from background.
- Usage is measured in real elapsed time (leaving within 10 s used to count as 0).
- Tamper counter no longer inflates on every status refresh; counts blocked launches and wrong gate answers.
- Bedtime / sync times are validated (24h HH:mm) and stored zero-padded; end time is exclusive (usable again at 07:00).
- Parental PIN stored as salted PBKDF2 (legacy plain-text PIN auto-migrated and deleted).
- Gate lockout persists across app restarts and escalates: 1 min → 5 min → 15 min → 1 h.
- Gate maths is now `a × b + c` instead of adding two 2-digit numbers.

## Safety / hardening
- YouTube WebView only allows taps on the curated Shorts; other navigation is blocked.
- WebView pauses when the app is backgrounded (audio used to continue).
- HTTPS only (`usesCleartextTraffic=false`), backups disabled (PIN/limits are not exported).

## Hygiene
- Removed: `DriveEngine.kt` (dead), unused deps (Drive client, Credential Manager, googleid, YouTube IFrame wrapper, ConstraintLayout),
  notification permission + prompt, `app/build/`, `.idea/`, `app.zip` (with APK), duplicate `app_build_gradle.kts`.
- Added: `proguard-rules.pro`, hardened `.gitignore`, 22 JVM unit tests (time rules, PIN hashing, lockout/gate maths, navigation allow-list).

## Phase 1.1 — autoplay wired up (offline videos)
- "Autoplay next video" now works: ON = play once then advance (wraps to the first after the last); OFF = loop the current video.
- Uses a NEW setting key (`autoplay_next_video`, default OFF) so existing devices keep today's looping behaviour until you tick the box in Settings. The old unused `autoplay_next` key is ignored.
- A single video always loops. YouTube mode is unchanged (Shorts loop natively).

## Decisions
- Maths fallback stays as the PIN-recovery path (decided).
- R8/minify stays off until Phase 2 (needs on-device testing alongside the dependency upgrades).

## Device test checklist
- [ ] Fresh launch → sync completes; counts match Drive
- [ ] Temporarily break the key → sync fails, **local files remain**
- [ ] Replace a video in Drive with same name → next sync re-downloads it
- [ ] Set Images limit to 1 min → Images locks after ~1 min of viewing
- [ ] Set bedtime to start a few minutes from now while a video is playing → player closes at bedtime
- [ ] Wrong gate answer ×3 → locked; force-close app → still locked
- [ ] Set PIN, relaunch → PIN still works (hash migration); Settings → Change PIN works
- [ ] YouTube mode: tap a channel/recommendation → nothing happens; swipe still advances
- [ ] Home button during YouTube mode → audio stops
- [ ] Settings → tick Autoplay → offline video advances at the end; untick → loops
