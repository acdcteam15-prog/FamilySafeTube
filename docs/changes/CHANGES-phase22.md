# Phase 22 - new picture categories from Drive folders

Reported: a folder added inside Offline/Images in Drive did not show in the app.

## Cause
The app only knew four fixed folders (Animals, Letters, Veg&fruits, Other); every other folder was ignored.

## What happens now
- **Any other folder inside Offline/Images is a new category**, named exactly like the folder (Arabic names work). The next sync finds it,
  downloads its pictures, and shows a note: "New picture category: Birds".
- **The picture list** (Images on the home screen) shows every category, **alphabetical with Other last**, with the number of pictures: "Birds (12)".
  An empty category is greyed out.
- **Quiz and matching game** use the pictures of every category except Other (as before), so a new category's pictures become cards at once.
  Spoken names in the picture viewer also work for new categories. Cards still ask for an Arabic name when one is missing (Edit quiz cards).
- Names that make the same id ("Birds" and "birds ") count once (the oldest folder wins). The reserved ids videos and config, and names with
  no letters, are skipped. Folders inside a new category folder are ignored.

## Deleting a folder in Drive
The phone never deletes a whole category by itself any more. Settings (below Drive) > **Folders removed from Drive** lists each such folder with
its picture count and two buttons: **Remove from phone** (deletes its pictures) or **Keep** (keeps them, not asked again; if the folder comes back
it syncs again). It shows nothing when there is nothing to decide. Single pictures deleted inside a folder are still removed by the sync as before.

## Under the hood
`ImageCategories` (ids, order), `CategoryBook` (new categories and the removed-folder decisions, one JSON in `Keys.IMAGE_BOOK`),
`ImageCategoryStore` (Android side), `DriveLayout.customFolders`, `RemovedFoldersSection`. Local file names are still `<id>_<file>`; ids never
contain "_". Audit rule 20. About 26 new tests (ids, order, removed-folder rules, storing, Drive discovery).

## Not checked here (no phone, no Drive)
The real Drive sync and the screens. Please: add a folder with a few pictures, sync, open Images; delete the folder in Drive, sync, and look in Settings.
