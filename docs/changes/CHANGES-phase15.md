# Phase 15 - Google sign-in instead of a Drive API key

**Why:** the Drive API key was published (it sits in the APK's BuildConfig and in GitHub history). Anyone could use it, and the app only worked with one person's public folders.

## What changed
- No `DRIVE_API_KEY` anywhere: removed from `app/build.gradle.kts`, both workflows, the wizard (menu 4 now prints the Google set-up steps), `local.properties.example`, docs, and `tools/static_audit.py` now fails the build if it comes back.
- New `DriveAuth` / `DriveTokens` (Google Identity `AuthorizationClient`, scopes `drive.readonly` + `drive.file`), `DriveApi` (Drive v3 over OkHttp with `Authorization: Bearer`), `DriveAccessFlow` (the screens that need an Activity), `DriveLayout` (pure folder-tree logic, unit-tested in `DriveLayoutTest`).
- `SyncWorker` no longer has hardcoded folder IDs. It finds, case-insensitively, in the signed-in user's My Drive:
  `Offline/` (videos, `Youtube.txt`, `settings.json`) and `Offline/Images/{Animals, Letters, Other, Veg&fruits}`.
  Missing folders are reported (never auto-created in the background) and are not treated as "deleted", so nothing local is wiped.
- Downloads use `files/{id}?alt=media` with the user's token: the virus-scan page / cookie jar code is gone. Google Docs/Sheets and shortcuts are skipped.
- Settings > **Google Drive**: Sign in with Google, Check folders, Create missing folders, Sign out (all behind the parent PIN).
- Not signed in / sign-in expired: the sync stops with a clear message instead of retrying forever.

## One-time set-up
See `SETUP.md`, "Google sign-in". Nothing secret is stored in the app or on GitHub for this.
