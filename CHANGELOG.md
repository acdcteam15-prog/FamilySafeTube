# FamilySafeTube — changelog (phases 1–22)

Details per phase are in `docs/changes/`.

| Phase | Theme | Highlights |
|---|---|---|
| 1 | Stabilize | Sync can no longer wipe local files; pagination; change detection; usage/limit bugs fixed (Images limit never worked); PIN hashed; persistent lockout; YouTube navigation allow-list; repo cleaned |
| 2 | Foundation | AGP 8.13 / Gradle 8.13 / Kotlin 2.2 / Java 17 (later moved to Gradle 9.4.1 / AGP 9.2.1, see phase 8); DataStore replaces SharedPreferences; per-day usage history; ViewModel |
| 3 | UI | Jetpack Compose + Material 3; parent dashboard with weekly chart; English + Arabic (RTL); single shared ExoPlayer |
| 4 | Features | Weekend schedule; 5-min / 1-min wind-down warnings; remote control via Drive `settings.json`; quiz mode (rewards for playing were removed again in 5b) |
| 5 | Polish | Dark-mode ActionBar bug fixed; tablet two-column layout; TalkBack support; R8 release build; CI; static audit tool; README |
| 6 | Quiz v2 | Hear-then-pick quiz (spoken name + animal sound), Arabic/English, in-app card editor with microphone recording, 10 bundled CC0 animal sounds, adaptive difficulty |
| 7 | Quiz extras | Sound-only questions, parent progress view (words learned / needing practice), calm end-of-round screen |
| 8 | Build tooling | **Gradle 9.4.1**, **AGP 9.2.1**, built-in Kotlin (no kotlin-android plugin), Compose plugin 2.3.10; audit now enforces Gradle 9.4.x and the AGP/Kotlin pairing |
| 9 | Bigger quiz pictures | Answer pictures are laid out to be as large as the screen allows (stacked on phones, grid on portrait tablets, row in landscape): about 2.5-4x the area; sharper decoding |
| 10 | Quiz decks, voices, family cards, levels | Letter and number cards, your own praise voice, family photo cards, Baby/Toddler/Older levels and "words this week" |
| 11 | Pictures shown whole | Quiz frames are sized to each picture's real shape (tall letter cards are no longer cropped); Arabic-named cards need no Arabic name |
| 12 | Letter-word questions, stickers, matching | "Which picture starts with this letter?" and "which letter does this start with?"; a sticker album (visual reward, no screen time); a Matching game |
| 13 | GitHub builds and in-app updates | Gradle 9.8.2, AGP 9.5.0, Kotlin 2.4.21, every library at its newest stable; GitHub Actions builds, tests and publishes the signed APK; the app updates itself from GitHub Releases; START-HERE.bat / Rebuild-APK.bat wizard |
| 14 | Logo everywhere | The FamilyTube logo is the launcher icon (adaptive, themed and legacy), the home header, the PIN and Settings dialogs, the update screen, the quiz and matching "well done" screens, and the update notification |
| 15 | Google sign-in instead of an API key | The Drive API key is gone from the app, the workflows and the wizard. Each person signs in with their own Google account; the app finds `Offline/` and `Offline/Images/{Animals,Letters,Other,Veg&fruits}` by name in their My Drive and can create missing folders. Downloads use the authorised Drive API (no cookie/virus-page workaround) |
| 16 | Blurred backdrop shows the whole picture | Fixes a regression from the library clean-up: the blurred backdrop of the image viewer showed only a magnified strip from the middle of the picture |
| 17 | Full-app lock, exact timer, stacked quiz, celebration | The whole app is covered by a padlock when time is up, at bedtime or on "Lock now" (parent PIN opens it and can add minutes); the lock happens exactly at 0; quiz pictures are stacked (2 for Baby and Toddler, 3 for Older); fireworks, confetti, falling stars and a dancing logo end each round; optional quiz rounds per day |
| 18 | Wizard never merges; old files removed | Fixes the "TOML syntax error" (the wizard's old `git pull --rebase` could push conflict markers); every zip lists its files and the wizard offers to delete old leftovers; the lock/quiz work merged into the Google sign-in version |
| 19 | Celebration voice, configurable | After every right answer and at the end of every round: a sound (cheer, fanfare, applause, sparkle) then a voice (your recordings or the phone voice with several phrases, optionally the child's name); phrases, voice style (pitch, speed), the recording to use and Test buttons in Settings > Celebration |
| 20 | Phone lock for parents, easier maths, timed lock | The parent check (Settings, unlock, reset timer, updates) uses the phone's own lock: face, fingerprint or screen lock (optionally face / fingerprint only); the maths fallback is now one easy step ("7 + 5", "14 − 6") with an "Another question" button; "Lock now" can lock for 15 / 30 / 60 minutes and opens by itself; quick-pick buttons for the daily limit |
| 21 | Arabic celebration with tashkeel | The phone voice now says the Arabic praise phrases with their vowel marks (أَحْسَنْتَ, بَرَافُو ...) so they are pronounced correctly; a switch in Settings > Celebration, a Boy / Girl choice for the Arabic wording, a Hear button beside every phrase, a tip for typing the child's name with marks, and a warning with a shortcut when the phone has no voice for the language |
| 22 | New picture categories from Drive folders | Any folder you add inside Offline/Images is now a category, named like the folder, listed alphabetically (Other last) with its picture count, and its pictures are used by the quiz and the matching game. If you delete a folder in Drive the phone does not delete its pictures: Settings asks, per folder, to remove them or keep them |

Tests: 86 JVM unit tests (time rules, schedule, PIN, lockout, navigation policy, usage repository, settings form,
remote settings, JSON parser, quiz rules, wind-down).
