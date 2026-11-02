# Phase 11 — Pictures shown whole

Reported: "Not all images correctly showing" (screenshot: the Drive letters-folder cards had the small object at the top and the bottom of the big letter cut off).

## Cause
Phase 9 made the quiz pictures bigger by giving them wide frames (up to 1.5:1) and **cropping** each picture to fill its frame.
Animal photos are already wide, so they looked right. The letters-folder cards are tall (a small object on top of a huge letter),
so cropping them to a wide frame cut off the banana and the lower half of the letter.

## Fix
- The quiz now **measures each picture's real shape** (reads only the image header, off the main thread) and sizes the frames to
  match, so the whole picture is visible.
- The layout planner uses that shape: tall cards on a phone get a **grid of tall frames** (about 158 x 236 dp), square cards get the
  largest square that fits, wide photos still stack in wide frames (about 248 x 165 dp).
- Per picture: it fills its frame by cropping only when little would be lost (within 25 % of the frame's shape); otherwise it is shown
  whole. A tall letter card is therefore never cut, and a photo that is slightly different from its neighbours is still filled nicely.
- The same rule applies to the small thumbnails in the card editor.

## Also fixed
- A picture file already named in Arabic (like "ميم - موزة" from your letters folder) now counts as having its Arabic name,
  so the editor no longer shows "⚠ Needs an Arabic name" for it, and the quiz speaks it as written.

## Tests: 206 (all pass here)
New: frames have exactly the picture's shape for every screen size; tall cards choose a grid and stay big; nothing exceeds the space;
the crop-or-fit rule; typical-shape averaging; Arabic file names.

## Not verified here
How it looks on your phone. If tall cards still look small, they are limited by the phone's screen height: three tall pictures simply need
room. Two options on a phone get one tall frame each at 170 x 254 dp.

## Test checklist
- [ ] A question from the letters folder: the whole card is visible (object at the top, full letter), for all 3 pictures
- [ ] Animal questions still look big and fill their frames
- [ ] Rotate to landscape: pictures are shown whole in a row
- [ ] Editor: letters-folder cards show without the ⚠ "needs Arabic name" warning
