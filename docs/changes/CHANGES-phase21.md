# Phase 21 - Arabic celebration words with tashkeel

Reported: after the quiz, the phone voice says the Arabic praise words wrongly because they have no tashkeel (vowel marks).

## Cause
The phrases were stored and spoken as plain letters (أحسنت). A text-to-speech voice has to guess the vowels, and for short words it often guesses wrong.

## Fix
- The Arabic phrases are now spoken with every vowel mark: أَحْسَنْتَ، بَرَافُو، عَمَل رَائِع، مُمْتَاز، رَائِع، لَقَدْ نَجَحْتَ، and "يَا" before the name.
  They are written the way a child hears them (no grammatical case endings), so the voice does not add "-un" sounds.
- **Settings > Celebration > Say Arabic with tashkeel** (on by default; shown when the quiz language is Arabic). Turn it off only if your phone
  voice reads the marks out loud; then the plain words are used, and the marks are taken off the name as well.
- The phrase list in Settings shows exactly what will be said (with marks).

## Helpful things added
- **Hear** button beside every phrase: plays that phrase now with the current form values (name, voice style), before saving.
- **The child's name**: type Arabic names with their marks (سَارَة) so they are pronounced right; the field is longer (40) because marks count as letters. A tip is shown under the field.
- **No voice installed?** If the phone has no text-to-speech voice for the language, Settings says so (it used to stay silently quiet) and has
  an *Open voice settings* button that goes to the phone's text-to-speech settings.

## Under the hood
`ArabicMarks` (strip / hasMarks), `CelebrationPhrases.text(..., diacritics)`, `CelebrationConfig.diacritics`, key `celeb_diacritics`
(read and written by `CelebrationStore`), audit rule 19.

## Tests
New: marks on by default, marked and plain are the same words, switching off strips the name too, English unchanged, the plan follows the switch, names keep their marks.

## Not checked here (no phone, no ears)
How the phone voice sounds. Please press **Hear** on each phrase. If one is still wrong, tell me which word and which phone voice; the marks for that word can be adjusted.
