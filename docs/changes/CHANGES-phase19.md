# Phase 19 - the celebration voice, configurable

Requested: a celebration voice for the quizzes, with several options, configurable in Settings.
Chosen: voices = phone voice with several phrases, your own recordings (choose which plays), applause/fanfare sounds made by the app, and the
child's name; moments = after every right answer and at the end of every round; settings = which phrases, voice style (pitch and speed), a Test button.

## What plays
For each moment (a right answer; the end of a round) the app plays, in this order: **a sound**, then **a voice**.
- **Sound** (made in code, no audio files): Cheer (the original), Fanfare (brassy "ta-ta-TAAA"), Applause (clapping noise that builds and fades),
  Sparkle (bell-like chimes) or None. The end-of-round version is longer. They were levelled to be about as loud as the cheer (never louder), and the
  tests keep them balanced.
- **Voice**: *My recordings* (the praise clips from Edit quiz cards > Praise voices; you can pick one clip or leave it random; if there is no recording
  for that moment the phone voice is used, so the old behaviour is kept), *Phone voice* (several phrases, in the quiz language) or *No voice*.
- **Phone voice phrases**: Well done / Bravo / Great job / Excellent / Amazing / You did it, in English and Arabic ("أحسنت", "برافو", ...).
  Tick the ones you want (at least one stays ticked). The same phrase is never said twice in a row.
- **The child's name** ("Well done, Sara!" / "أحسنت يا سارة!"): switch on and type the name. Only the phone voice says it (recordings cannot).
- **Voice style**: Normal, Playful, Gentle or Excited, plus sliders for pitch and speed.

## Settings > Celebration
Two switches (right answer / end of round), the sound, the voice, the name, the phrases (shown exactly as the phone will say them), the style and
sliders, which recording to use, and two **Test** buttons that play what the form shows right now, before saving.

## Where it is used
The quiz and the matching game (a matched pair = a right answer; a finished board = the end of a round). The hard-coded cheer and "Well done!" are gone.
The wrong-answer reaction is unchanged.

## Under the hood
- `CelebrationVoice.kt` (pure, tested): the settings, phrases, voice-style presets, and `CelebrationPlan`, which decides what plays for every combination.
- `ToneSynth`: fanfare, sparkle and applause. `QuizAudio` now has a step type for sounds that the next step WAITS for, and applies pitch and speed.
- `CelebrationStore` reads and writes 11 settings; `ui/CelebrationSection.kt` is the Settings screen.

## Tests: 352 (was 308)
Phrases in both languages, name cleaning and use, every combination of moment / sound / voice / recordings / name, no repeats back to back,
voice-style limits, and the shape, length, loudness and determinism of every sound. Every new audit rule was proven by breaking it.

## Tip
Each right answer now has a sound and a short voice, then the animal's name and sound. For a very young child you can switch off *After every right answer*
(keeping the end-of-round celebration), or choose *No voice* and keep only the sound.

## Not checked here (no Android SDK, no phone, no ears)
How the sounds and the phone voice actually sound, the Arabic voice reading the phrases and names, and the Settings screen on a real device.
The whole code is first compiled by the GitHub build.
