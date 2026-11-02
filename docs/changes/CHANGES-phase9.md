# Phase 9 — Bigger quiz pictures

Requested: the quiz images need to be bigger.

## Why they were small
The 2-3 answer pictures were always placed side by side in one row, so on a phone each was only about 100 dp wide.

## What changed
- A layout planner (`QuizImageLayout`, pure and unit-tested) picks whichever arrangement gives the **largest** pictures for the free space:
  - **stacked** (one above the other) on a phone held upright,
  - **two per row** on a portrait tablet with 3+ pictures,
  - **one row** in landscape / on wide tablets.
- Pictures may be a little wider than tall (up to 1.5:1) or a little taller (down to 0.8:1) so they fill the space; photos are cropped, never stretched.
- The speaker button now sits **beside** the name (72 dp) instead of above it, giving the pictures about 80 dp more height.
- Pictures are decoded sharper (1280 px) because they are shown larger; the editor thumbnails still use small, cheap versions.

## Size, in dp per picture (old row layout -> now)
| Screen | Options | Before | Now |
|---|---|---|---|
| Phone, upright | 2 | 158 x 158 | **328 x 254** |
| Phone, upright | 3 | 101 x 101 | **248 x 165** |
| Big phone, upright | 3 | 119 x 119 | **308 x 205** |
| Tablet, portrait | 3 | 192 x 192 | **294 x 344** (grid) |
| Tablet, landscape | 3 | 325 x 325 | 325 x 380 (row) |

## Tests: 160 (all pass here)
10 new layout tests: correct arrangement per screen, never exceeds the space, aspect ratio limits, floor and cap, more height never shrinks pictures.

## Not verified here
How it looks on your phone (no Android SDK in my sandbox). If a picture feels too tall or too wide, the limits are two constants in `QuizImageLayout.kt` (MAX_ASPECT, MIN_ASPECT).

## Test checklist
- [ ] Quiz on your phone held upright: pictures are large; 2 and 3 option questions both fit without scrolling
- [ ] Rotate to landscape: pictures sit in one row and are large
- [ ] Right tap: the picture grows slightly with a green border and is not clipped
- [ ] Picture quality looks sharp (not blurry) at the bigger size
