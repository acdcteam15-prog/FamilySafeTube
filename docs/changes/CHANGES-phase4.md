# Phase 4 — Features

> **Superseded in part:** the quiz no longer earns bonus minutes (see CHANGES-phase5b.md). The remote `bonus` from `settings.json` is unchanged.

Builds on Phase 3. Everything new is parent-controlled and, where it changes limits, opt-in.

## 1. Weekend schedule
- Settings → "Use a different schedule on weekend days": separate daily limit and bedtime for chosen days
  (default Sat+Sun; pick any days with the chips).
- A bedtime window belongs to the day it **starts** on. Example: weekday 20:00–07:00 and weekend 22:00–09:00:
  Saturday 08:00 is free (Friday night's window ended 07:00); Saturday night locks at 22:00 until 09:00 Sunday.
- With the weekend schedule off, behaviour is identical to before (verified minute-by-minute in a test).
- The lock message now shows the end time of the window that is actually locking.

## 2. Wind-down warnings
- A toast at **5 minutes** and at **under 1 minute** left, in YouTube, Offline Videos, Images and the Quiz.
- Counts the tightest of: daily limit, that category's limit, and time until bedtime.
- Each warning appears once per session; starting a session with 3 minutes left warns once, not twice.

## 3. Remote control via Drive (opt-in)
- Put a `settings.json` in the same Drive folder as your offline videos. On every sync it is applied
  (manual, or the daily scheduled one). Enable it in Settings → "Apply settings from Drive".
- Only fields present in the file are applied. Removing the file does NOT revert anything.
- **The whole file is rejected if any value is invalid** (nothing half-applies). Settings shows the last result.
- Limits: daily limit 1–600, category limits 0–600, bonus 1–120 minutes, valid HH:mm, known day names.
- `bonus` grants extra minutes for today, once per `id` (change the id to grant again), optionally with `expires`.
- Security note: the file is only as private as the Drive folder. Anyone who can *edit* the folder can change
  the child's limits; sharing the folder as view-only is fine.

```json
{
  "version": 1,
  "time_limit_mins": 45,
  "bedtime_start": "20:00", "bedtime_end": "07:00",
  "category_limits": { "youtube": 20, "offline_videos": 0, "images": 30 },
  "weekend": { "enabled": true, "time_limit_mins": 90,
               "bedtime_start": "21:30", "bedtime_end": "08:00", "days": ["SAT", "SUN"] },
  "bonus": { "id": "2026-10-07-a", "minutes": 15, "expires": "2026-10-07" }
}
```
Every key is optional. A one-line "grant 15 minutes today" file is just:
`{ "bonus": { "id": "extra-1", "minutes": 15 } }`

## 4. Quiz mode
- New "QUIZ MODE" card: "Tap the lion" with three pictures built from the synced animal and fruit/veg images
  (never two pictures with the same label). Needs 3 different pictures; otherwise the card says to sync.
- Parent settings: show/hide the quiz, bonus minutes per 5 correct answers (0 = off), daily cap (default 15).
- **Screen-time rule (no loophole):**
  - Rewards active (reward > 0 and cap not reached): quiz time is free and earns bonus minutes; only bedtime blocks it.
  - Otherwise: it counts as Images time and obeys limits and bedtime.
- Dashboard shows today's bonus minutes. Bonus is added to the day's global limit and resets daily.

## Tests: 86 total (run here)
New: Schedule (incl. all 10,080 minutes vs old behaviour), WindDown, MiniJson, RemoteSettings, QuizRules, plus
additions to UsageRepository, UsageKeys, SettingsForm.

## Not verified here / deferred
- Not run: Android code (Compose screens, QuizActivity, applier, toasts), Gradle build, a real Drive sync of settings.json.
- Deferred (bigger or riskier): multiple child profiles, playlists/favourites, kiosk (screen pinning), audio-only mode,
  resumable downloads / storage cap, eye-rest breaks.

## Test checklist
- [ ] Weekend: enable, set Saturday limit/bedtime, check the status pill and "locked until" on a weekend day
- [ ] Wind-down: set a 7-minute limit; watch for the 5-minute and 1-minute toasts in a player
- [ ] Remote: tick the option, put `{"time_limit_mins": 12}` in Drive as settings.json, sync → limit shows 12
- [ ] Remote bonus: add `{"bonus":{"id":"a","minutes":10}}`, sync → limit +10; sync again → no second grant
- [ ] Remote invalid: break the JSON, sync → Settings says the file was invalid, nothing changed
- [ ] Quiz: opens, wrong tap shows red border, 5 correct completes a set; with reward 5 → "+5 bonus minutes"
- [ ] Quiz with rewards off at the daily limit → blocked like Images
