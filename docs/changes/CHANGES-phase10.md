# Phase 10 — Letters & numbers, your praise voice, family cards, levels

## 1. Letters and numbers cards
- **Four built-in decks**, no picture files needed, each switched on in Settings → Quiz (all OFF by default):
  Arabic letters (28), English letters (26), Numbers 1-10 (big digit), Counting 1-5 (that many stars).
- The card is drawn by the app: a big letter / digit / stars on a coloured tile. The quiz speaks the name (e.g. "ألف", "ثلاثة", "Three").
- Each deck is its own group, so a question about a letter only offers other letters.
- Your existing Drive **letters** folder ("A is for Apple" pictures) is now part of the quiz too, as ordinary picture cards.
- You can switch any card off, add your own voice to it, or star it, like any other card.
- English letters are best with the quiz language set to English (in Arabic mode the phone says the Arabic way of reading them, e.g. "بي").

## 2. Your own praise voice
- Editor → **🎙 Praise voices**: record (or import) up to 3 short clips for each moment: answer **right**, answer **wrong**, **round finished**.
- A recorded clip plays **instead of** the synthesised beep / the phone's "Well done"; one is picked at random and the same clip is never played twice in a row.
- With no recording the quiz behaves exactly as before.

## 3. Family cards (photo + your voice)
- Editor → **➕ Family card**: take a photo with the camera app or choose one, type the name (Arabic and/or English), save. Then tap the card to record your voice saying the name.
- Photos are straightened (camera rotation), shrunk to 1280 px and stored privately inside the app. Delete a card (✏️ → "Delete this card") and its photo and recordings are removed.
- No camera permission is requested: the phone's own camera app takes the picture.

## 4. Levels and "words this week"
- **Quiz level** (Settings): **Baby**: always 2 pictures, no sound-only questions, repeats after 14 s.
  **Toddler** (default, same as before): 3 pictures, 30 % sound-only, 10 s. **Older**: 3 pictures, 50 % sound-only, 8 s, forgiving for 4 misses.
- The dashboard gently **suggests** a level change (never automatic): harder after 20+ answers at 85 %+ with 5+ words learned; easier after 15+ answers under 40 %.
- **Focus words**: tap ☆ on a card in the editor. A starred word is asked about 3x as often for **7 days**, then returns to normal. "Clear focus words" removes all.

## Tests: 198 (all pass here)
New: decks (sizes, unique ids/labels, art, overrides), levels and advisor, praise set/store/picker, family store and cards,
photo maths (rotation, sampling, scaling), focus weighting and expiry. The audit also checks the new string and resource wiring.

## Not verified here (no Android SDK, no camera, no ears)
The camera / gallery flow and the FileProvider, EXIF rotation on real photos, MediaRecorder for praise clips, the Compose screens,
how the phone's Arabic voice pronounces the letter and number names, and how the star tiles look.

## Things to know
- Praise clips and family photos live in the app's private storage; "Clear offline storage" does not delete them. Changing phones loses them (backup/restore is not built yet).
- A sync cannot overwrite them; family cards are never touched by the Drive deletion pass.

## Test checklist
- [ ] Settings → switch on "Numbers" and "Arabic letters" → the quiz now shows digit / letter tiles and speaks their names
- [ ] Counting deck: "ثلاثة" with 3 stars among 1-5 star tiles
- [ ] Editor → Praise voices: record "bravo!" for right, "no no" for wrong → the quiz plays your voice instead of the beeps
- [ ] Editor → Family card: take a photo, name it, save → the card appears (👪) and appears in questions
- [ ] Tap the family card → record your voice for the name → hear it in the quiz
- [ ] A rotated phone photo is saved upright; deleting the card removes it
- [ ] Settings → Level Baby: always 2 pictures, no sound-only; Older: 3 pictures
- [ ] Star a card (☆ → ★): it appears more often; "Clear focus words" resets
- [ ] After a few rounds the dashboard shows a level suggestion when results warrant it
