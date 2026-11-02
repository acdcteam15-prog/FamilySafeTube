# FamilySafeTube: build and update with GitHub - no Android Studio

Two files do everything:

| File | What it does |
|---|---|
| **START-HERE.bat** | Menu. Choose **1** the first time. |
| **Rebuild-APK.bat** | After you (or Claude) changed something: sends it to GitHub, builds, downloads the new APK. |

## First time (about 20 minutes, mostly waiting)
1. Unzip the project anywhere and double-click **START-HERE.bat**.
2. Type **1**. The wizard then, by itself:
   - installs **Git** and the **GitHub helper** (Windows asks permission once),
   - opens a web page: sign in to GitHub and type the short code shown in the window (create a free account first if you have none),
   - creates your repository and uploads the project,
   - sets up the **signing key** (see below),
   - explains the one-time **Google sign-in** set-up (see below),
   - builds the signed APK on GitHub (~10 min) and **downloads it into the `APK` folder**.
3. Copy the APK to your phone and open it. Done.

### The signing key (the first question the wizard asks)
Android installs an update only when it is signed with the same key as the app on the phone.
- The app on the phone was installed from Android Studio: pick **1**. The wizard finds that key on this PC; the app and its data stay.
- You have a `.jks` key file: pick **2**.
- Fresh start: pick **3**. A new key is made in `C:\Users\<you>\FamilySafeTube-signing` (**back that folder up**, it is the only copy; uninstall the old app from the phone first).

After saving the key the wizard prints its **SHA-1**. You need it in the next step.

### Google sign-in (the second step; replaces the old Drive key)
The app no longer contains any Google key. Every person signs in with their own Google account and the app reads that account's own Drive.
You set this up **once** in Google Cloud (https://console.cloud.google.com):

1. **Delete the old Drive API key** (Credentials). It was published, treat it as public. Also make the GitHub repository private or accept that the old key stays in its history, which is harmless once the key is deleted.
2. Create a project, then *APIs & Services > Library*: enable **Google Drive API**.
3. *APIs & Services > OAuth consent screen* (or "Google Auth platform"): audience **External**; app name FamilySafeTube; add the scopes
   `.../auth/drive.readonly` (read the Offline folders) and `.../auth/drive.file` (create missing folders).
4. **Publish the app** ("In production"). Do not leave it in *Testing*: sign-ins expire after 7 days there.
   Without Google's verification, up to 100 users can use it and each sees an "unverified app" screen once (**Advanced > Go to FamilySafeTube**). That is fine for family and friends.
5. *Credentials > Create credentials > OAuth client ID > Android*: package name `com.example.familysafetube` and the **SHA-1 of your signing key** (the wizard prints it in step 4).
   Nothing from this client is pasted anywhere: Google matches it by package + signing key.
   If you also run debug builds from Android Studio, add a second Android client with the debug key's SHA-1.

On each phone: *Settings (parent PIN) > Google Drive > Sign in with Google*, then **Create missing folders** (or make them yourself), then put the videos and pictures in them.
The phone needs Google Play services (all normal Android phones have it).

**Folder layout** (in My Drive, names not case-sensitive): `Offline` (videos, `Youtube.txt`, `settings.json`) and `Offline/Images/` with `Animals`, `Letters`, `Other`, `Veg&fruits`.
Google Docs/Sheets files and shortcuts are ignored; only real files are downloaded.

## Every later update
1. Claude gives you a new project zip: unzip it over your folder (same place, replace all files).
2. Double-click **Rebuild-APK.bat**, answer "what changed?", wait ~10 minutes, install the APK it downloads.
3. Or skip the PC for the install: on the phone, the home screen shows an **update banner** (also a notification). Tap it, enter the
   **parent PIN**, read what is new and tap *Download and install*. The first time, Android asks you once to *allow installing from
   FamilySafeTube*; say yes. *Settings > App updates* does the same at any time.

If a build fails, the wizard saves the error as `build-error.txt` and opens it. Copy all of it and send it to Claude.

## The wizard menu
| # | What it does |
|---|---|
| 1 | First time: set everything up and build the APK |
| 2 | Rebuild after changes (same as Rebuild-APK.bat) |
| 3 | Change or re-save the signing key |
| 4 | Show the Google sign-in set-up steps |
| 5 | Show the last build error |
| 6 | Open the Releases page |
| 7 | A build is already running: wait for it and download the APK |

## What gets published
`FamilySafeTube-1_3_0-build102.apk` plus a `.sha256` file on your repository's **Releases** page. The build number only goes up
(100 + GitHub's run number), and the app checks the checksum, the package name and the build number before installing.
To mark a release as **required**, put `[required]` on its own line in the notes.

The visible version number ("1.3.0") is in `version.properties`; Rebuild-APK.bat offers to change it.

## Keeping libraries current
All versions are in `gradle/libs.versions.toml`. GitHub's **Dependabot** opens a weekly pull request when something is outdated.

## Private repository?
Works, and keeps the APK away from strangers. Each phone then needs a read-only token:
*Settings > App updates > Advanced*. A public repository needs nothing.

## Play Protect
Android may show its own "unknown app" warning for any APK that is not from the Play Store: tap **More details > Install anyway**.
