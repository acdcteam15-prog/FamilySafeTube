# FamilySafeTube — changelog (modernisation, phases 1–5)

Details per phase are in `docs/changes/`.

| Phase | Theme | Highlights |
|---|---|---|
| 1 | Stabilize | Sync can no longer wipe local files; pagination; change detection; usage/limit bugs fixed (Images limit never worked); PIN hashed; persistent lockout; YouTube navigation allow-list; repo cleaned |
| 2 | Foundation | AGP 8.13 / Gradle 8.13 / Kotlin 2.2 / Java 17 (later moved to Gradle 9.4.1 / AGP 9.2.1, see phase 8); DataStore replaces SharedPreferences; per-day usage history; ViewModel |
| 3 | UI | Jetpack Compose + Material 3; parent dashboard with weekly chart; English + Arabic (RTL); single shared ExoPlayer |
| 4 | Features | Weekend schedule; 5-min / 1-min wind-down warnings; remote control via Drive `settings.json`; quiz mode (rewards for playing were removed again in 5b) |
| 17 | Wizard never merges | Fixes the "TOML syntax error": the wizard's old `git pull --rebase` could leave merge-conflict markers inside files and push them; it now keeps the folder's files exactly and refuses to send broken files |
| 16 | Full-app lock, exact timer, stacked quiz, celebration | The whole app is covered by a padlock when time is up, at bedtime or on "Lock now" (parent PIN opens it and can add minutes); the lock happens exactly at 0; quiz pictures are stacked (2 for Baby and Toddler, 3 for Older); fireworks, confetti, falling stars and a dancing logo end each round; optional quiz rounds per day |
| 15 | Blurred backdrop shows the whole picture | Fixes a regression from the library clean-up: the blurred backdrop of the image viewer showed only a magnified strip from the middle of the picture |
| 14 | Logo everywhere | The FamilyTube logo is the launcher icon (adaptive, themed and legacy), the home header, the PIN and Settings dialogs, the update screen, the quiz and matching "well done" screens, and the update notification |
| 13 | GitHub builds and in-app updates | Gradle 9.8.2, AGP 9.5.0, Kotlin 2.4.21, every library at its newest stable; GitHub Actions builds, tests and publishes the signed APK; the app updates itself from GitHub Releases; START-HERE.bat / Rebuild-APK.bat wizard |
| 12 | Letter-word questions, stickers, matching | "Which picture starts with this letter?" and "which letter does this start with?"; a sticker album (visual reward, no screen time); a Matching game |
| 11 | Pictures shown whole | Quiz frames are sized to each picture's real shape (tall letter cards are no longer cropped); Arabic-named cards need no Arabic name |
| 10 | Quiz decks, voices, family cards, levels | Letter and number cards, your own praise voice, family photo cards, Baby/Toddler/Older levels and "words this week" |
| 9 | Bigger quiz pictures | Answer pictures are laid out to be as large as the screen allows (stacked on phones, grid on portrait tablets, row in landscape): about 2.5-4x the area; sharper decoding |
| 8 | Build tooling | **Gradle 9.4.1**, **AGP 9.2.1**, built-in Kotlin (no kotlin-android plugin), Compose plugin 2.3.10; audit now enforces Gradle 9.4.x and the AGP/Kotlin pairing |
| 7 | Quiz extras | Sound-only questions, parent progress view (words learned / needing practice), calm end-of-round screen |
| 6 | Quiz v2 | Hear-then-pick quiz (spoken name + animal sound), Arabic/English, in-app card editor with microphone recording, 10 bundled CC0 animal sounds, adaptive difficulty |
| 5 | Polish | Dark-mode ActionBar bug fixed; tablet two-column layout; TalkBack support; R8 release build; CI; static audit tool; README |

Tests: 86 JVM unit tests (time rules, schedule, PIN, lockout, navigation policy, usage repository, settings form,
remote settings, JSON parser, quiz rules, wind-down).
