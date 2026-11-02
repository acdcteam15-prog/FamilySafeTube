# Phase 3 — UI rebuild (Compose + Material 3, localization, shared player)

Builds on Phase 2. Look and feel intentionally match the original (same dark palette, gradients and layout).

## Jetpack Compose + Material 3
- Home screen, parental gate, settings, PIN management, dashboard and the image-category picker are now Compose
  (`ui/` package). `MainActivity` is a thin host; all state still comes from `MainViewModel`.
- Edge-to-edge on the home screen (system bars transparent, content padded by safe-drawing insets).
- `FamilySafeTubeTheme`: dark scheme = the original palette. A light scheme is included but OFF by default
  (the kids' screens are designed dark); flip `darkTheme = isSystemInDarkTheme()` when the player screens are ready.
- Deleted: `activity_main.xml`, `dialog_dashboard.xml`, `dialog_settings.xml`.
- Compose BOM 2026.08.00 (all Compose versions come from it).

## New parent dashboard
- **Last 7 days stacked bar chart** by category (from the per-day usage history built in Phase 2), legend,
  today's breakdown with limits, total, tamper attempts, storage and stored-file list.

## Localization (English + Arabic, RTL)
- All user-facing text moved to string resources: `values/strings.xml` and `values-ar/strings.xml`
  (138 strings, generated from one table so both languages always match).
- Covers the UI, bedtime/limit messages, sync status/progress (worker, engine, view model) and player toasts/hints.
- Chevrons mirror in RTL; Android 13+ "per-app language" is enabled (`locales_config.xml`).
- **The Arabic is a first-pass translation. Please have a native speaker review it** (especially the gate and limit messages).
- Not translated: file names / image captions (they come from your Drive files).

## Shared video player (offline mode)
- ONE ExoPlayer for the whole session; pages only host the video surface. Fixes a latent bug: swiping *back* briefly
  held two hardware decoders (the condition behind the earlier MediaCodec SIGABRT).
- Player is released in `onPause` (frees the decoder in the background) and recreated in `onResume`.
- Tap-to-pause overlay, loop / autoplay-next, keep-screen-on while playing, FIT resize mode: all kept.

## Pure logic + tests (43 total, run here)
- New `SettingsForm` validator (was inline in the Activity, untestable) with 8 tests.

## Not changed / deferred
- targetSdk stays 34 (35+ enforces edge-to-edge on the player screens too) → Phase 5.
- YouTube / Image player screens are still view-based; only their strings were localized.
- Unused leftovers: `bg_btn_*`, `bg_card`, `bg_status_pill`, `bg_progress_*` drawables (safe to delete later).

## What was NOT verified here (no Android SDK)
Compose compilation, Gradle resolution of the BOM/plugins, the shared-player behaviour on a real device,
and how the Arabic layout looks. These are the likely places for any first-build errors.

## Test checklist
- [ ] Gradle sync + build succeed (if a Compose artifact fails to resolve, tell me the message)
- [ ] Home looks like before; status pill, usage bar and mode counts update; sync button shows progress
- [ ] Settings → change values → Save → reopen: values persisted; invalid time shows the right error
- [ ] Gate: wrong answer ×3 locks; PIN create / change / remove works; maths fallback works with a PIN set
- [ ] Dashboard: weekly chart shows bars after using the app; today's list matches
- [ ] Phone language = Arabic (or per-app language): text is Arabic, layout mirrors, nothing overlaps
- [ ] Offline videos: swipe DOWN and UP repeatedly (both directions) — no crash, video starts each time
- [ ] Offline videos: tap to pause/resume; lock button; rotate; Home button and return; autoplay on/off
- [ ] Images + YouTube modes still open and obey limits/bedtime
