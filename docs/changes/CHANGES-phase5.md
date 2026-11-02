# Phase 5 — Polish & release readiness

## Fixes
- **Dark-mode ActionBar bug (pre-existing):** `values-night/themes.xml` replaced the app theme with a
  `DarkActionBar` parent, so on any phone in system dark mode the home screen, quiz and YouTube player showed
  an ActionBar. Removed; the base theme already has fixed dark colours.
- Player screens re-enter immersive mode whenever the window regains focus (after dialogs / notification shade).

## Modernisation
- `Immersive.kt` replaces four copies of the deprecated `systemUiVisibility` flags with the insets-controller API
  (same behaviour; layout and display-cutout handling deliberately unchanged).
- Release build: **R8 + resource shrinking on**, debug-signed so it installs directly for personal use
  (`proguard-rules.pro` keeps all app code, shrinks libraries).
- Dead resources removed: 9 unused drawables, 5 unused layouts, 23 unused colours (verified unreferenced, no dynamic lookups).

## Tablet / large screens
- Home: two columns at ≥ 720 dp (status + actions left, mode cards right), content capped at 1100 dp.
- Dialogs capped at 560 dp; quiz pictures capped at 720 dp.

## Accessibility (TalkBack)
- Status pill is a live region (changes are announced); usage card exposes real progress; app title is a heading.
- Decorative emoji / chevrons hidden from screen readers; the weekly chart reads as one summary ("Mon: 12 min, …").
- Quiz options are "Picture 1/2/3" (never the answer). Minimum 48 dp touch targets on buttons.

## Tooling
- `tools/static_audit.py`: 8 checks (manifest→classes, strings en/ar parity & formats, resource references,
  calls on our own objects, interface implementations, version-catalog aliases, build files). Needs only Python.
  Verified with deliberately injected faults. Run it after every change.
- `.github/workflows/android.yml`: audit → unit tests → debug APK → release APK (R8).
- `README.md` and a consolidated `CHANGELOG.md`.

## Deliberately NOT done
- **targetSdk stays at 34.** Raising it to 35/36 changes window-inset and back-navigation behaviour on every
  screen and cannot be validated without a device. Checklist for when you do it: players' HUD vs status bar/cutout,
  predictive back, keyboard insets in dialogs, orientation on tablets. `Immersive.kt` already uses the supported API.
- Light theme still off (kids' screens are designed dark).
