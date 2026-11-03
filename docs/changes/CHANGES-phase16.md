# Phase 16 - full-app lock, exact timer, stacked quiz, celebration

Requested: congratulations with animations at the end of the quiz; quiz pictures one above the other, never side by side; a timer at 0 must
lock (the viewer kept working and scrolling); a lock that covers the whole app and shows nothing inside.
Chosen in the questions: the parent PIN unlocks and can add minutes; a plain dark screen with just a padlock; 2 pictures for Baby and Toddler,
3 (stacked) for Older; fireworks + confetti/falling stars + a jumping logo; extras: a parent "Lock now" button, the screen turns off by itself
while locked, and a daily limit on quiz rounds.

## Why "0m" kept working (found in the code)
1. The label rounded DOWN, so "0m" meant "under one minute left", not zero.
2. Every screen re-checked the limits only every 10 seconds, so time ran on, and scrolling kept working, for up to 10 seconds after the limit.
3. When the limit hit, the viewer only closed itself; the home screen stayed fully open. Nothing was locked.

## What changed
- **Exact timing**: each screen now schedules its next check for the moment the time runs out (`LockPolicy.nextCheckDelayMs`, at the latest every
  10 s as a safety net): image viewer, offline videos, YouTube, quiz, matching game, and the home screen (for bedtime starting, or a bedtime
  extension ending). Bedtime is measured to the second.
- **Label**: rounded UP (`TimeRules.minutesLabel`), so it never says 0m while time remains.
- **Full-app lock** (`LockOverlay`): when time is up, at bedtime, or after "Lock now", the app shows ONLY a padlock on a black screen; the home
  screen is not even drawn behind it. The player screens close silently (no toast) when the lock takes over. The lock is known instantly on
  start/resume, so a locked app never flashes its home screen. The screen is not kept awake, so it turns off by itself.
- **Opening it**: tap the padlock, enter the parent PIN. A manual lock simply opens. On "time is up" or bedtime a dialog offers +5/+10/+15/+30
  minutes, "Settings" (so limits can be changed) or "Not now". Time up: the minutes are added to today's limit. Bedtime: bedtime is lifted for
  that long (plus the minutes if today's limit is also used up, otherwise the child would be locked again at once).
- **"Lock now"** button next to Settings and Reset timer on the home screen (no PIN needed to lock; the PIN is needed to open).
- **Quiz pictures**: always stacked one above the other (`QuizImageLayout.plan(..., only = COLUMN)`). Baby 2, Toddler 2 (was 3), Older 3.
- **Celebration**: fireworks bursts, confetti and falling five-pointed stars, the logo hopping and wiggling (squashing when it lands), then the
  stars, the new sticker and the buttons. Used by the quiz and the matching game. The maths is pure and tested (`Celebration`).
- **Quiz rounds per day** (Settings > Quiz, default no limit; 3, 5, 10 or 20): after the last allowed round the end screen has no "Again" button
  and says see you tomorrow; the home card says "Done for today" and a tap explains. Matching boards are not counted.

## Tests
301 unit tests (was 263): lock kinds and priority, bedtime extensions, exact check delays (never before the deadline, never a busy loop),
grants, round limit, label rounding, per-day round counter, forced stacking, and the celebration maths.
The audit has a new check (lock screen shows only a padlock, the home is not drawn while locked, every screen schedules exactly, no label rounds
down, level picture counts, stacking, celebration pieces, round limit wiring, no keep-screen-on, instant lock); each rule was proven by breaking it.

## Not checked here (no Android SDK, no phone)
The look and smoothness of the animations, the padlock screen on a real device, and the whole Compose code compile (the GitHub build is the first
real compile). Note: the lock covers the app, not the phone; a child can still leave the app and use other apps.
