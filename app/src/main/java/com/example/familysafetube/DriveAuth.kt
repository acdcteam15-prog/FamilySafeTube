package com.example.familysafetube

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import java.io.IOException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/** The user has to sign in (again) before Drive can be used. Retrying in the background cannot fix this. */
class DriveAuthException(message: String) : Exception(message)

/**
 * Google sign-in for Drive access (Google Identity Services "AuthorizationClient").
 *
 * No API key and no client secret live in the app: the OAuth client is matched by Google from the
 * package name + signing certificate, and every user signs in with their own Google account.
 * Scopes: drive.readonly (read the Offline folders the parent made by hand) and drive.file (create the
 * folders when they are missing).
 */
object DriveAuth {
    const val SCOPE_READ = "https://www.googleapis.com/auth/drive.readonly"
    const val SCOPE_FILE = "https://www.googleapis.com/auth/drive.file"
    private val SCOPES = listOf(Scope(SCOPE_READ), Scope(SCOPE_FILE))

    fun accountEmail(): String = AppGraph.settings.getOr(Keys.DRIVE_ACCOUNT, "")
    fun isSignedIn(): Boolean = accountEmail().isNotBlank()

    fun client(context: Context): AuthorizationClient = Identity.getAuthorizationClient(context)

    private fun request(email: String): AuthorizationRequest = AuthorizationRequest.builder()
        .setRequestedScopes(SCOPES)
        .apply { if (email.isNotBlank()) setAccount(Account(email, "com.google")) }
        .build()

    /** For the UI. The result may need the consent screen ([AuthorizationResult.hasResolution]). */
    fun authorize(context: Context, onResult: (AuthorizationResult?, Exception?) -> Unit) {
        client(context).authorize(request(accountEmail()))
            .addOnSuccessListener { onResult(it, null) }
            .addOnFailureListener { onResult(null, it) }
    }

    fun hasReadAccess(r: AuthorizationResult): Boolean = SCOPE_READ in r.grantedScopes

    fun rememberAccount(email: String?) {
        if (!email.isNullOrBlank()) AppGraph.settings.edit { it[Keys.DRIVE_ACCOUNT] = email }
    }

    fun forgetAccount() {
        AppGraph.settings.edit { it[Keys.DRIVE_ACCOUNT] = "" }
    }

    /**
     * For the background sync: an access token without any screen. Never call on the main thread.
     * @throws DriveAuthException when the user must sign in again, IOException for network/Play-services trouble (retry).
     */
    fun tokenBlocking(context: Context): String {
        val email = accountEmail()
        if (email.isBlank()) throw DriveAuthException("not signed in")
        val r = try {
            Tasks.await(client(context).authorize(request(email)), 30, TimeUnit.SECONDS)
        } catch (e: ExecutionException) {
            throw IOException("Google authorization failed: ${e.cause?.message ?: e.message}", e)
        } catch (e: Exception) {
            throw IOException("Google authorization failed: ${e.message}", e)
        }
        if (r.hasResolution()) throw DriveAuthException("sign-in needed again")
        if (!hasReadAccess(r)) throw DriveAuthException("Drive read access not granted")
        return r.accessToken ?: throw DriveAuthException("no access token")
    }
}

/** Caches the access token for one sync run; [invalidate] throws a rejected (expired) token away. */
class DriveTokens(private val context: Context) {
    @Volatile private var cached: String? = null

    fun seed(token: String) { cached = token }

    @Synchronized
    fun get(): String = cached ?: DriveAuth.tokenBlocking(context).also { cached = it }

    @Synchronized
    fun invalidate() {
        val old = cached
        cached = null
        if (old == null) return
        try {
            Tasks.await(
                DriveAuth.client(context).clearToken(ClearTokenRequest.builder().setToken(old).build()),
                15, TimeUnit.SECONDS
            )
        } catch (_: Exception) { /* a fresh token is requested next time anyway */ }
    }
}
