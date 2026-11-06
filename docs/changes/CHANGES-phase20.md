# Phase 20 - the phone lock for parents, easier maths, timed lock

Requested: open (and lock) the app with the phone lock (face, fingerprint); make the maths lock much simpler; add useful things.

## The phone lock
Every parent action (Settings, opening the padlock screen, Reset timer, App updates) now asks the phone itself first: face, fingerprint,
or the phone's own PIN / pattern / password. The phone does the checking; the app never sees a face, a fingerprint or the phone PIN.
- Built with the stable `androidx.biometric` 1.1.0 (the 1.4 line is still alpha) and `BIOMETRIC_WEAK | DEVICE_CREDENTIAL`, so face unlock
  (which many phones class as "weak") works too.
- **On by default**, but only when the phone really has a screen lock. A phone with no lock behaves exactly as before (PIN / maths).
- Settings > Phone lock: a switch, and **Face / fingerprint only (not the phone PIN or pattern)**. Turn that on if your child knows the
  phone's PIN or pattern; then only an enrolled face or fingerprint is accepted. "Other ways" on the prompt opens the app PIN / maths choice.
- A wrong face or finger is handled by the phone (it locks itself out after a few tries). Wrong app-PIN / maths answers still lock *those two*
  with the escalating delay, but a child's wrong guesses can no longer shut the parent out of the phone lock.
- **Locking stays one tap** (it only takes things away). **Opening** always asks the parent.

## Easier maths
The old question was `8 × 7 + 20`. Now it is one easy step: `7 + 5` (two single digits) or `14 − 6` (10..19 minus a single digit), never a
negative answer, answer box of up to 3 digits, and an **Another question** button. The sum is always drawn left-to-right, also in Arabic.
To change the difficulty, edit the ranges in `GateRules.mathChallenge`.

## Useful extras
- **Lock now** asks how long: *until a parent opens it*, 15, 30 or 60 minutes. A timed lock lifts by itself, exactly when it ends.
- **Quick picks** (15 / 30 / 45 / 60 / 90 / 120) under the daily limit in Settings.

## Under the hood
- `PhoneLock.kt` (new): availability check, the prompt, the on/off + biometric-only settings. `GateRules`: which ways the gate offers, the lockout
  rule for the phone lock, the new maths. `LockPolicy`: `LOCK_CHOICES`, `manualActive`, `timedLockEnd`, `delayWithTimedLock`. `Policy.lockNow(minutes)`.
- New settings keys: `phone_lock_enabled`, `phone_lock_bio_only`, `manual_lock_until_ms`. New permission: `USE_BIOMETRIC`.
- Audit rule 18 checks all of this is wired; the audit's old "lockNow()" check now accepts `lockNow(minutes)`.

## Tests
`GateRulesTest` (easy maths over 500 seeds, both kinds appear, no multiplication, which ways are offered, lockout vs phone lock) and
`LockPolicyTest` (timed lock end, still running / over, the "look again" delay) were rewritten / extended; the 30 tests in those two files were
compiled and run here with a plain Kotlin compiler and all pass.

## Not checked here (no Android SDK, no phone)
The full Gradle build and the real prompt on a device. If the build fails, paste `build-error.txt` as usual. On a real phone, please try:
the prompt appears when you tap Settings; "Cancel" shows the PIN / maths choice; face/fingerprint-only mode; Lock now > 15 minutes.
