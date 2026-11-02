package com.example.familysafetube.ui

/** What the Settings screen needs from the Activity for Google Drive (sign-in needs an Activity). Callbacks run on the main thread. */
interface DriveUi {
    /** Opens the Google account/consent screen. [done] gets a message to show (success or error). */
    fun driveSignIn(done: (String) -> Unit)
    fun driveSignOut(done: () -> Unit)
    /** Looks for the Drive folders; with [create] also creates the missing ones. [done] gets the result text. */
    fun driveCheck(create: Boolean, done: (String) -> Unit)
}
