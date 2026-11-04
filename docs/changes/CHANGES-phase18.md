# Phase 18 - the wizard never merges (fixes "TOML syntax error ... Unexpected '<'")

## What happened
The GitHub build stopped at once with `TOML syntax error: Unexpected '<'` at line 18 of `gradle/libs.versions.toml`. The file I delivered has no `<`
at all (checked in the tree and inside the zip), so the copy on GitHub was different: it contained a git merge-conflict marker.

## Cause (in the wizard, not the app)
`Sync-Remote` handled "this folder's history is not on top of GitHub's" with `git pull --rebase --autostash origin main`, with all errors hidden.
When GitHub had a newer version of a file that the new unzip also changed, git left conflict markers inside the file and still exited with code 0.
The wizard then ran `git add -A`, committed the broken file and pushed it.
Reproduced with real git (a file changed on GitHub and also in the folder): the file ended up with `<<<<<<< Updated upstream ... >>>>>>> Stashed changes`
and that is exactly what was pushed.

## Fix
- `Sync-Remote` no longer merges. If GitHub's latest commit is not underneath, the branch is moved to it with `git reset --soft origin/main`, so every
  file in the folder stays exactly as it is; the next commit holds exactly the difference and the push is a normal fast-forward. GitHub's own history
  is kept. (Anything changed directly on GitHub, for example by merging a Dependabot update, is replaced by this folder's version: the zip is the truth.)
- New safety net `Assert-NoConflictMarkers`: before anything is committed or sent, the folder is searched for lines starting with `<<<<<<<` or `>>>>>>>`;
  if any are found the wizard stops and says which files and what to do.
- The audit fails if the wizard ever contains `pull`, `merge`, `rebase`, `stash` or `autostash`, if the safety net is missing, or if any file in the
  project contains conflict markers.

## Checked
The patched PowerShell functions were run with real PowerShell 7.6.9 and real git: the failing situation now pushes clean files (fast-forward); a folder
with markers is refused with a clear message; a brand-new unzip with unrelated history attaches cleanly.
Not checked: the wizard on Windows PowerShell 5.1 itself (these are the same git commands as before, minus the merge).

## Recovering
Unzip this `FamilySafeTube.zip` into the project folder (replace all files) and run Rebuild-APK.bat. The broken copy on GitHub is replaced by the new commit.

## Why GitHub was "ahead" (the likely trigger)
The Google sign-in work (phase 15, no Drive API key) was built in another conversation and pushed to GitHub, while the zips from this
conversation were still built on the older code. Unzipping them over the same folder overwrote the files the sign-in code depends on
(`Keys.DRIVE_ACCOUNT`, the sign-in library in the build files) and then `DriveAuth.kt` / `DriveAccessFlow.kt` no longer compiled.

## What this version is
The sign-in version (GitHub build 109, version 1.4.1) with everything that came after it re-applied on top:
the blurred-backdrop fix (16), the lock / exact timer / stacked quiz / celebration / rounds limit (17) and the wizard fix (18).
No Drive API key anywhere (the audit enforces it). Two stale files that the wizard had committed from older folders were removed
(`.github/workflows/android.yml`, `gradle/gradle-daemon-jvm.properties`).

## Old files are now removed by the wizard
Each zip carries `setup/files.txt` (every file of the project). Before sending anything, Rebuild-APK.bat lists files in `app/src`, `.github`, `gradle`,
`setup`, `tools` and `docs` that are NOT on the list (leftovers of older versions) and removes them after a yes. A missing or damaged list removes nothing.
The audit checks that the list matches the project exactly.
