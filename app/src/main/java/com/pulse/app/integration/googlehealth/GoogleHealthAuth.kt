package com.pulse.app.integration.googlehealth

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.AccountPicker
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal sealed interface GoogleAuthorization {
    class Granted(val token: GoogleHealthToken) : GoogleAuthorization
    class Consent(val intent: PendingIntent) : GoogleAuthorization
}

internal sealed interface GoogleHealthPrompt {
    class ChooseAccount(val intent: Intent) : GoogleHealthPrompt
    class Consent(val intent: PendingIntent) : GoogleHealthPrompt
}

internal enum class GoogleHealthAuthStage { ACCOUNT_PICKER, AUTHORIZE, CONSENT, IDENTITY }

internal interface GoogleHealthAuthorization {
    suspend fun request(account: String, scopes: Set<String>): GoogleAuthorization
    suspend fun clear(token: GoogleHealthToken)
    suspend fun revoke(account: String, scopes: Set<String>)
}

internal class GoogleHealthAuth(private val context: Context) : GoogleHealthAuthorization {
    private val client get() = Identity.getAuthorizationClient(context)

    fun accountPicker(): Intent {
        checkPlayServices()
        return AccountPicker.newChooseAccountIntent(AccountPicker.AccountChooserOptions.Builder()
            .setAllowableAccountsTypes(listOf("com.google"))
            .setAlwaysShowAccountPicker(true)
            .build())
    }

    fun accountFrom(result: ActivityResult): String {
        val account = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        val type = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE)
        return selectedGoogleAccount(result.resultCode, account, type)
    }

    override suspend fun request(account: String, scopes: Set<String>): GoogleAuthorization = checked {
        checkPlayServices()
        if (scopes.isEmpty()) throw GoogleHealthFailure(GoogleHealthProblem.CONSENT)
        val request = AuthorizationRequest.builder()
            .setAccount(Account(account, "com.google"))
            .setRequestedScopes(scopes.map(::Scope))
            .build()
        val result = client.authorize(request).awaitResult()
        val pending = result.pendingIntent
        if (result.hasResolution()) GoogleAuthorization.Consent(
            pending ?: throw GoogleHealthFailure(GoogleHealthProblem.AUTHORIZATION_LAUNCH_FAILED))
        else GoogleAuthorization.Granted(result.toToken(account))
    }

    fun finish(result: ActivityResult, expected: String): GoogleHealthToken {
        val data = result.data
        val senderFailed = data?.action == StartIntentSenderForResult.ACTION_INTENT_SENDER_REQUEST &&
            data.hasExtra(StartIntentSenderForResult.EXTRA_SEND_INTENT_EXCEPTION)
        return readGoogleConsentResult(result.resultCode, data != null, senderFailed) {
            client.getAuthorizationResultFromIntent(data).toToken(expected)
        }
    }

    override suspend fun clear(token: GoogleHealthToken) {
        checked { client.clearToken(ClearTokenRequest.builder().setToken(token.value).build()).awaitResult() }
    }

    override suspend fun revoke(account: String, scopes: Set<String>) {
        if (scopes.isEmpty()) return
        checked {
            client.revokeAccess(RevokeAccessRequest.builder().setAccount(Account(account, "com.google"))
                .setScopes(scopes.map(::Scope)).build()).awaitResult()
        }
    }

    @Suppress("DEPRECATION")
    private fun AuthorizationResult.toToken(expected: String): GoogleHealthToken {
        val returnedAccount = toGoogleSignInAccount()?.email
        if (returnedAccount != null && !returnedAccount.equals(expected, ignoreCase = true)) {
            throw GoogleHealthFailure(GoogleHealthProblem.ACCOUNT_CHANGED)
        }
        val scopes = grantedScopes.orEmpty().toSet().intersect(GoogleHealthMetrics.scopes)
        if (scopes.isEmpty()) throw GoogleHealthFailure(GoogleHealthProblem.CONSENT)
        return GoogleHealthToken(accessToken?.takeIf(String::isNotBlank)
            ?: throw GoogleHealthFailure(GoogleHealthProblem.AUTHENTICATION), scopes)
    }

    private fun checkPlayServices() {
        val status = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        if (status != ConnectionResult.SUCCESS) {
            throw GoogleHealthFailure(GoogleHealthProblem.PLAY_SERVICES, status)
        }
    }

    private suspend fun <T> checked(action: suspend () -> T): T {
        try {
            return withTimeoutOrNull(25_000) { Completed(action()) }?.value
                ?: throw GoogleHealthFailure(GoogleHealthProblem.NETWORK, CommonStatusCodes.TIMEOUT)
        } catch (e: ApiException) {
            throw googleAuthorizationFailure(e.statusCode, fromConsent = false)
        }
    }

    private class Completed<T>(val value: T)

}

internal fun selectedGoogleAccount(resultCode: Int, account: String?, type: String?): String {
    if (resultCode == Activity.RESULT_CANCELED && account.isNullOrBlank()) {
        throw GoogleHealthFailure(GoogleHealthProblem.ACCOUNT_SELECTION_CANCELLED)
    }
    if (resultCode != Activity.RESULT_OK || account.isNullOrBlank() || (type != null && type != "com.google")) {
        throw GoogleHealthFailure(GoogleHealthProblem.ACCOUNT_SELECTION_FAILED)
    }
    return account
}

internal fun <T> readGoogleConsentResult(
    resultCode: Int, hasPayload: Boolean, senderFailed: Boolean, decode: () -> T
): T {
    if (senderFailed) throw GoogleHealthFailure(GoogleHealthProblem.AUTHORIZATION_LAUNCH_FAILED)
    if (!hasPayload) throw GoogleHealthFailure(GoogleHealthProblem.AUTHORIZATION_INTERRUPTED)
    // A non-OK activity result can still carry a Google Status with the reason for failure.
    val result = try {
        decode()
    } catch (e: ApiException) {
        throw googleAuthorizationFailure(e.statusCode, fromConsent = true)
    } catch (_: android.os.BadParcelableException) {
        throw GoogleHealthFailure(GoogleHealthProblem.AUTHORIZATION_INTERRUPTED)
    }
    if (resultCode != Activity.RESULT_OK) throw GoogleHealthFailure(GoogleHealthProblem.AUTHORIZATION_INTERRUPTED)
    return result
}

internal fun googleAuthorizationFailure(status: Int, fromConsent: Boolean) = GoogleHealthFailure(when (status) {
    CommonStatusCodes.DEVELOPER_ERROR -> GoogleHealthProblem.CONFIGURATION
    CommonStatusCodes.NETWORK_ERROR, CommonStatusCodes.TIMEOUT -> GoogleHealthProblem.NETWORK
    CommonStatusCodes.API_NOT_CONNECTED,
    ConnectionResult.SERVICE_MISSING, ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED,
    ConnectionResult.SERVICE_DISABLED, ConnectionResult.SERVICE_INVALID -> GoogleHealthProblem.PLAY_SERVICES
    CommonStatusCodes.SIGN_IN_REQUIRED, CommonStatusCodes.INVALID_ACCOUNT -> GoogleHealthProblem.AUTHENTICATION
    CommonStatusCodes.CANCELED ->
        if (fromConsent) GoogleHealthProblem.AUTHORIZATION_CANCELLED else GoogleHealthProblem.AUTHORIZATION_INTERRUPTED
    else -> if (fromConsent) GoogleHealthProblem.AUTHORIZATION_INTERRUPTED else GoogleHealthProblem.REAUTHORIZE
}, status)

internal fun logGoogleAuthorization(stage: GoogleHealthAuthStage, failure: GoogleHealthFailure?, resultCode: Int?) {
    val outcome = failure?.problem?.name ?: "OK"
    Log.i("PulseGoogleHealth", "stage=$stage outcome=$outcome status=${failure?.statusCode ?: "-"} resultCode=${resultCode ?: "-"}")
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        when {
            task.isCanceled -> continuation.resumeWithException(GoogleHealthFailure(GoogleHealthProblem.REAUTHORIZE))
            task.isSuccessful -> continuation.resume(task.result)
            else -> continuation.resumeWithException(task.exception ?: GoogleHealthFailure(GoogleHealthProblem.REAUTHORIZE))
        }
    }
}
