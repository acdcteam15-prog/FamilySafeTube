# Phase 14 - the logo everywhere

Source: `logo1.jpg` (1024x1024, light-blue background, soft shadow). The background and the shadow were cut away using the logo's own
saturated-blue outline, with anti-aliased edges, so the logo is a clean transparent PNG.

## Where it is used
- **Launcher icon** (adaptive): `drawable-nodpi/ic_launcher_foreground.png` (432 px; the logo is scaled to sit inside the circular safe zone,
  about 82 % of the visible icon) on a sky-blue gradient (`drawable/ic_launcher_background.xml`, the colour of the original artwork).
- **Themed icon** (Android 13+): `ic_launcher_monochrome.png`, a one-colour version in which the letters, eyes and play triangle are cut out so it stays readable.
- **Legacy icons**: `mipmap-*/ic_launcher.png` and `ic_launcher_round.png` (48-192 px).
- **Inside the app** (`ui/AppLogo.kt`, `drawable-nodpi/logo.png`, 320 px): home header, the parental PIN dialog, the Settings title,
  the update screen, and the "well done" screens of the quiz and the matching game.
- **Update notification**: the logo is the large icon (the small status-bar icon must stay single-colour, so it is unchanged).
- **Android 12+ splash screen**: Android builds it from the launcher icon automatically.
- `docs/logo.png` is used at the top of the README.

## Checks added to `tools/static_audit.py`
- Every Compose layout / modifier / material name that a file uses must be imported (`tools/import_check.py`). It flagged two missing imports
  (`size`, `width`) in code added in this phase before they could break a build.
- Launcher layers exist, every `@drawable` they refer to exists, PNGs have the right size and are RGBA, no duplicate resource names in a folder,
  and each launcher layer / the logo exists in exactly one folder.

## Note about the artwork
The text in the logo reads **"FAMLY TUBE"** (the "I" of FAMILY is missing). It is used exactly as supplied. To fix it, send a corrected image and the
icon set and the in-app logo are regenerated from it.

## Not checked here
No Android build was run (no SDK in the sandbox). The icon was previewed through circle and rounded-square masks, not on a real launcher.
