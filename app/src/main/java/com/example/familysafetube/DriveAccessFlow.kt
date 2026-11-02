package com.example.familysafetube

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.example.familysafetube.ui.DriveUi
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The parts of Google Drive set-up that need an Activity: the account/consent screen, and the
 * "check / create folders" action. Create it as a property of the Activity (it registers a result launcher).
 * [onSignedIn] runs after a successful sign-in (MainActivity starts a sync there).
 */
class DriveAccessFlow(
    private val activity: ComponentActivity,
    private val onSignedIn: () -> Unit
) : DriveUi {

    private var pending: ((ActivityResult) -> Unit)? = null

    private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        val cb = pending
        pending = null
        cb?.invoke(res)
    }

    private fun str(id: Int, vararg a: Any) = activity.getString(id, *a)

    /** Gets a usable authorization, showing Google's consent screen when needed. */
    private fun authorize(onReady: (AuthorizationResult) -> Unit, onFail: (String) -> Unit) {
        DriveAuth.authorize(activity) { r, e ->
            if (r == null) {
                onFail(str(R.string.drive_err, e?.message ?: "?"))
                return@authorize
            }
            if (!r.hasResolution()) { onReady(r); return@authorize }

            val pi = r.pendingIntent
            if (pi == null) { onFail(str(R.string.drive_err, "no consent screen")); return@authorize }
            pending = { res ->
                if (res.resultCode != Activity.RESULT_OK) {
                    onFail(str(R.string.drive_err_cancelled))
                } else {
                    try {
                        onReady(DriveAuth.client(activity).getAuthorizationResultFromIntent(res.data))
                    } catch (ex: Exception) {
                        onFail(str(R.string.drive_err, ex.message ?: "?"))
                    }
                }
            }
            try {
                launcher.launch(IntentSenderRequest.Builder(pi).build())
            } catch (ex: Exception) {
                pending = null
                onFail(str(R.string.drive_err, ex.message ?: "?"))
            }
        }
    }

    override fun driveSignIn(done: (String) -> Unit) {
        authorize(
            onReady = { r ->
                if (!DriveAuth.hasReadAccess(r)) {
                    done(str(R.string.drive_err_scope))
                } else {
                    DriveAuth.rememberAccount(r.toGoogleSignInAccount()?.email)
                    done(str(R.string.drive_signed_in_msg, DriveAuth.accountEmail()))
                    onSignedIn()
                }
            },
            onFail = done
        )
    }

    override fun driveSignOut(done: () -> Unit) {
        val finish = { DriveAuth.forgetAccount(); done() }
        DriveAuth.authorize(activity) { r, _ ->
            val token = if (r != null && !r.hasResolution()) r.accessToken else null
            if (token == null) { finish(); return@authorize }
            DriveAuth.client(activity)
                .clearToken(ClearTokenRequest.builder().setToken(token).build())
                .addOnCompleteListener { finish() }
        }
    }

    override fun driveCheck(create: Boolean, done: (String) -> Unit) {
        if (!DriveAuth.isSignedIn()) { done(str(R.string.drive_err_need_signin)); return }
        authorize(
            onReady = { r ->
                val token = r.accessToken
                if (!DriveAuth.hasReadAccess(r) || token == null) {
                    done(str(R.string.drive_err_scope))
                } else {
                    runCheck(token, create, done)
                }
            },
            onFail = done
        )
    }

    private fun runCheck(token: String, create: Boolean, done: (String) -> Unit) {
        activity.lifecycleScope.launch {
            var created = false
            val msg = withContext(Dispatchers.IO) {
                try {
                    val tokens = DriveTokens(activity.applicationContext).also { it.seed(token) }
                    val api = DriveApi(DriveHttp.client, tokens)
                    val missing = api.resolveLayout().missing()
                    when {
                        missing.isEmpty() -> str(R.string.drive_all_ok)
                        create -> {
                            api.ensureLayout()
                            created = true
                            str(R.string.drive_created, missing.joinToString())
                        }
                        else -> str(R.string.drive_missing, missing.joinToString())
                    }
                } catch (e: Exception) {
                    str(R.string.drive_err, e.message ?: "?")
                }
            }
            done(msg)
            if (created) onSignedIn()   // new folders exist now: run a sync so the status refreshes
        }
    }
}
