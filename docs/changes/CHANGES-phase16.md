# Phase 16 - the blurred backdrop shows the whole picture

Reported: the blur behind a picture in image mode covered only the "face" (a magnified middle part), not the full body.

## Cause (a regression from phase 13)
When the unmaintained blur library was removed, the backdrop became a tiny `override(128, 128)` load into a `CENTER_CROP` ImageView.
Glide adds a centre-crop step for `CENTER_CROP` (checked in the Glide 5.0.9 source, `RequestBuilder.into(ImageView)`), so the picture was first
cropped to a 128 x 128 square from its middle, and the ImageView then cropped that square again to the tall phone screen.

## Fix
The backdrop ImageView is `FIT_XY`: Glide then adds no crop (it maps to centre-inside), the tiny bitmap keeps the picture's shape, and the whole
picture is stretched over the whole screen. It is blurred (Android 12+: GPU blur; older phones: the tiny picture scaled up), so the stretch is not visible.
The main picture on top is unchanged.

## Guard
`tools/static_audit.py` check 14 fails if the backdrop is `CENTER_CROP` again, or `CENTER_CROP` together with `override()`.

## Not checked here
The result on a real phone (only a simulation with the logo). If you prefer the backdrop to fill the screen by cropping instead (less of the picture,
no stretching), that is a one-word change (`CENTER_CROP` without `override()`).
