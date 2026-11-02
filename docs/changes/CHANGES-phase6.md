# Phase 6 — Quiz v2: hear it, then pick it

Requested: audio + visual quiz for a baby; Arabic or English; sounds supplied; parent records from the phone and
attaches to a question; questions edited in the app.

## For the child
- The quiz **speaks the name** (then plays the **animal's sound**), and shows 2–3 big pictures. The name is also shown large.
- Correct: happy chime, the picture grows with a green border, the name and sound play again, next question.
- Wrong: a soft "oh-oh", that picture fades (cannot be tapped again), the name is repeated. Nothing red, no game over, no timer.
- A big 🔊 button repeats it; it also repeats by itself after 10 seconds of no touch.
- Stars show progress through a round of 5 (a small celebration; no rewards, and the quiz never adds time).
- Words the child misses come back more often; the last 3 are not repeated; after 3 misses in a row the question has 2 pictures.
- Distractors come from the same category (animals with animals, fruit with fruit) and never repeat a label.

## For you (Settings → Edit quiz cards)
- A card per picture already synced from Drive. Arabic names for ~90 animals/fruits/vegetables are built in.
  Cards the catalog doesn't know show "⚠ Needs an Arabic name".
- Per card: Arabic name, English name, on/off, built-in sound on/off.
- Three attachments, each with ▶ play, ⏺ record (from the phone mic, 8 s max), 📂 import a file (5 MB max), 🗑 remove:
  the **animal sound**, **your voice saying the Arabic name**, **your voice saying the English name**.
  Your recording always wins over the phone's text-to-speech voice and over the built-in sound.
- Quiz language (Arabic / English) in Settings. Recordings live in their own folder, so sync and "Clear offline storage" never delete them.

## Sounds
10 real recordings, all CC0, from ESC-50 (dog, cat, cow, sheep, pig, rooster, hen, frog, crow, bird): `docs/SOUND-CREDITS.md`.
**Not included: lion, horse, elephant, duck, donkey, camel, goat, monkey** — no freely-licensed source I could verify. Record or import them in the editor.
**Chosen by measurement, not by ear** — please play each once. Weakest measurements: sheep, frog and hen (noisier; frog had only 2 CC0 clips to choose from).

## Removed earlier in this series (5b)
The quiz no longer earns bonus time; it counts as Images time.

## Tests: 132 (all pass here)
New: catalog, cards, audio plan, engine (selection, weighting, difficulty), JSON store, tone synth, audio-slot helpers.
Audit now also verifies that every bundled sound exists, is mapped, and is promised by the catalog.

## Not verified here (no Android SDK / no ears)
Text-to-speech in Arabic (depends on the phone having an Arabic voice; the quiz shows a hint if it doesn't),
MediaRecorder/MediaPlayer/AudioTrack behaviour, the microphone permission flow, file import, Compose screens, and how the sounds actually sound.

## Test checklist
- [ ] Quiz opens; the name is spoken (Arabic), then the animal sound; pictures appear
- [ ] Tap right: chime + name again + next question. Tap wrong: soft sound, picture fades, name repeated
- [ ] Leave it alone 10 s: it repeats
- [ ] Settings → Quiz language English → quiz speaks English
- [ ] Phone without an Arabic voice: hint is shown (Settings → install voice, or record your own)
- [ ] Editor: change an Arabic name, play ▶ it; record your voice for a card (permission prompt) and hear it in the quiz
- [ ] Editor: import an audio file as the lion sound; hear it after the name
- [ ] Editor: switch a card off → it disappears from questions; ⚠ cards are clearly marked
- [ ] Play every built-in sound once (dog, cat, cow, sheep, pig, rooster, hen, frog, crow, bird)
