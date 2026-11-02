# Phase 12 — Letter <-> word questions, sticker album, matching game

## 1. Letter <-> word questions (Settings → Quiz → "Include letter ↔ word questions", off by default, for older toddlers)
- **Hear a letter, pick the picture:** the phone says "ميم" and shows the letter; the child taps the picture of something that starts with م (the banana).
- **See an object, pick the letter:** the banana is shown and spoken ("موزة"); the child taps the letter م among 2-3 letter tiles.
- A right answer says both together ("ميم، موزة").
- About 1 question in 4 when switched on. Works for every animal, fruit, vegetable and family card (by the first letter of its Arabic name, or its English name when the quiz language is English), not only the Drive letters folder.
- First-letter rules: vowel marks ignored, the article "ال" skipped ("الفيل" → ف), all alef forms (أ إ آ ا) count as alif, ى counts as ي.
- Why not use the Drive letters-folder cards for this: each is ONE image with the letter and the object together, so the app cannot separate them. They still appear as ordinary picture cards. The new questions use the app's own letter tiles plus separate object pictures.

## 2. Sticker album (visual reward)
- When a round of 5 is finished (or a matching board is finished) the child may win a **sticker**: the picture of something they just got right that is not in the album yet. It is shown big on the "well done" screen.
- 🏆 button in both games opens the **album**: collected stickers newest first (gold frame), then "❓" slots for the rest. Tapping a sticker says its name (and plays its sound).
- **It never gives screen time.** Only things you have switched on in the editor can become stickers. The dashboard shows "Stickers collected: n / total".

## 3. Matching game (new "MATCHING MODE" card on the home screen; hide it in Settings)
- Two columns. Tap one tile, then its partner. A match cheers (or plays your praise clip) and says both names; a miss just clears the choice.
- Three kinds, chosen at random: **letter ↔ object that starts with it**, **digit ↔ that many stars** (needs nothing synced), **animal sound ↔ its picture** (tap the 🔊 tile to hear it).
- Pairs per board follow the quiz level: Baby 2, Toddler 3, Older 4.
- Counted as Images time with the usual limits, bedtime and warnings (same as the quiz).

## Tests: 243 (all pass here)
New: first-letter rules (all 28 deck letters reachable), letter/word question building (no second right answer, distinct letters, English), audio plans, sticker award/store/order, matching board building and the full game rules.

## Not verified here (no Android SDK, no phone)
The new screens and animations, how the phone's Arabic voice reads the letter names, and the feel of the matching game with a baby.
Hamza-on-yaa (ئ) and hamza-on-waw (ؤ) at the start of a word are not recognised as a letter, so such words are simply skipped for letter questions.

## Test checklist
- [ ] Settings: switch on "letter ↔ word questions" → in the quiz you sometimes see a big letter and are asked for the picture, or an object and are asked for the letter
- [ ] Right answer on those says both ("ميم، موزة"); wrong answer repeats only what was asked
- [ ] Finish a round of 5 → a sticker appears on the "well done" screen; 🏆 opens the album with it in a gold frame
- [ ] Tap a collected sticker → hear its name; uncollected ones show ❓
- [ ] Home → MATCHING MODE: tap a left tile then its partner → cheer + names; a wrong pair clears the choice
- [ ] Number ↔ stars board works even with nothing synced; the sound ↔ picture board plays the animal sound when the speaker tile is tapped
- [ ] Finishing a board can win a sticker; Again starts a new board
- [ ] Settings → untick "Show the Matching game" → the card disappears
