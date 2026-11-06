package com.example.familysafetube

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * The parent check with the phone's own lock: face, fingerprint, or (unless "biometric only" is on) the phone's
 * PIN / pattern / password. The phone does the checking; the app never sees any face, fingerprint or phone PIN.
 */
object PhoneLock {
    private val store get() = AppGraph.settings

    /** Parent's choice: do not accept the phone PIN / pattern, only a face or fingerprint. */
    fun biometricOnly(): Boolean = store.getOr(Keys.PHONE_LOCK_BIO_ONLY, false)

    private fun authenticators(biometricOnly: Boolean): Int =
        if (biometricOnly) BIOMETRIC_WEAK else BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** Can this phone do the check right now (a screen lock is set; for biometric-only, a face or fingerprint is enrolled)? */
    fun isAvailable(c: Context, biometricOnly: Boolean): Boolean = try {
        BiometricManager.from(c).canAuthenticate(authenticators(biometricOnly)) == BiometricManager.BIOMETRIC_SUCCESS
    } catch (_: Exception) {
        false
    }

    /** The parent switched it on (it is on by default) AND the phone can do it. Otherwise the gate uses the PIN / maths as before. */
    fun isOn(c: Context): Boolean =
        store.getOr(Keys.PHONE_LOCK, true) && isAvailable(c, biometricOnly())

    /** The Activity behind a Compose [Context], or null. */
    fun activityOf(c: Context): FragmentActivity? {
        var ctx: Context? = c
        while (ctx is ContextWrapper) {
            if (ctx is FragmentActivity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    /**
     * Shows the phone's own prompt. [onDone] gets (true, null) when the parent passed; (false, null) when they
     * cancelled or pressed "Other ways"; (false, message) when the phone refused (for example too many wrong tries).
     */
    fun prompt(activity: FragmentActivity, onDone: (ok: Boolean, error: String?) -> Unit) {
        val bioOnly = biometricOnly()
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onDone(true, null)

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val quiet = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                onDone(false, if (quiet) null else errString.toString())
            }
            // onAuthenticationFailed (a face / finger that did not match): the phone keeps the prompt open, nothing to do.
        }
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.gate_phone_title))
            .setSubtitle(activity.getString(R.string.gate_phone_subtitle))
            .setAllowedAuthenticators(authenticators(bioOnly))
        // With the phone PIN allowed the system supplies its own "use PIN" button; without it we must name the button ourselves.
        if (bioOnly) builder.setNegativeButtonText(activity.getString(R.string.gate_phone_other))
        try {
            BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(builder.build())
        } catch (_: Exception) {
            onDone(false, null)
        }
    }
}
