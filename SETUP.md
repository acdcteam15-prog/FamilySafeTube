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
   - asks for your **Google Drive key** (see below),
   - builds the signed APK on GitHub (~10 min) and **downloads it into the `APK` folder**.
3. Copy the APK to your phone and open it. Done.

### The signing key (the first question the wizard asks)
Android installs an update only when it is signed with the same key as the app on the phone.
- The app on the phone was installed from Android Studio: pick **1**. The wizard finds that key on this PC; the app and its data stay.
- You have a `.jks` key file: pick **2**.
- Fresh start: pick **3**. A new key is made in `C:\Users\<you>\FamilySafeTube-signing` (**back that folder up**, it is the only copy; uninstall the old app from the phone first).

After saving the key the wizard prints its **SHA-1**. You need it in the next step.

### The Google Drive key (the second question)
The app reads your Drive folders with an API key. It is built into the APK, so **anyone who can download the APK can read it**.
Make a NEW key (the key that was in the old project's source must be considered public, delete it) and restrict it in Google Cloud:
*Credentials > your key > API restrictions: Google Drive API only; Application restrictions: Android apps, package
`com.example.familysafetube` and the SHA-1 the wizard printed.*
Paste the key into the wizard (typing is hidden). You can press Enter to skip, and add it later with menu **4**.
A private repository keeps the APK itself private as well (see the end of this file).

## Every later update
1. Claude gives you a new project zip: unzip it over your folder (same place, replace all files).
2. Double-click **Rebuild-APK.bat**, answer "what changed?", wait ~10 minutes, install the APK it downloads.
3. Or skip the PC for the install: on the phone, the home screen shows an **update banner** (also a notification). Tap it, enter the
   **parent PIN**, read what is new and tap *Download and install*. The first time, Android asks you once to *allow installing from
   FamilySafeTube*; say yes. *Settings > App updates* does the same at any time.

If a build fails, the wizard saves the error as `build-error.txt` and opens it. Copy all of it and send it to Claude.

## If the wizard says "This folder contains broken files"
It found leftover merge-conflict markers (lines starting with `<<<<<<<`) in some files and stopped on purpose, because they would break the build.
Unzip the newest `FamilySafeTube.zip` **into the project folder, replacing all files**, and run **Rebuild-APK.bat** again.

## The wizard menu
| # | What it does |
|---|---|
| 1 | First time: set everything up and build the APK |
| 2 | Rebuild after changes (same as Rebuild-APK.bat) |
| 3 | Change or re-save the signing key |
| 4 | Add or change the Google Drive key |
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
Works, and keeps the APK (and the Drive key inside it) away from strangers. Each phone then needs a read-only token:
*Settings > App updates > Advanced*. A public repository needs nothing.

## Play Protect
Android may show its own "unknown app" warning for any APK that is not from the Play Store: tap **More details > Install anyway**.
