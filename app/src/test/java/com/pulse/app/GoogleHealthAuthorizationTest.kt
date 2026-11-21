package com.pulse.app

import android.app.Activity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import com.pulse.app.integration.googlehealth.GoogleHealthFailure
import com.pulse.app.integration.googlehealth.GoogleHealthProblem
import com.pulse.app.integration.googlehealth.googleAuthorizationFailure
import com.pulse.app.integration.googlehealth.readGoogleConsentResult
import com.pulse.app.integration.googlehealth.selectedGoogleAccount
import org.junit.Assert.*
import org.junit.Test

class GoogleHealthAuthorizationTest {
    @Test fun confirmedGoogleAccountIsPassedToAuthorization() {
        assertEquals("tester@example.test", selectedGoogleAccount(Activity.RESULT_OK, "tester@example.test", "com.google"))
        assertEquals(GoogleHealthProblem.ACCOUNT_SELECTION_FAILED,
            failure { selectedGoogleAccount(Activity.RESULT_OK, null, "com.google") }.problem)
        assertEquals(GoogleHealthProblem.ACCOUNT_SELECTION_FAILED,
            failure { selectedGoogleAccount(Activity.RESULT_OK, "tester@example.test", "other") }.problem)
        assertEquals(GoogleHealthProblem.ACCOUNT_SELECTION_CANCELLED,
            failure { selectedGoogleAccount(Activity.RESULT_CANCELED, null, null) }.problem)
    }

    @Test fun cancelledActivityResultStillDeliversDeveloperError() {
        val failure = failure {
            readGoogleConsentResult(Activity.RESULT_CANCELED, hasPayload = true, senderFailed = false) {
                throw ApiException(Status(CommonStatusCodes.DEVELOPER_ERROR))
            }
        }
        assertEquals(GoogleHealthProblem.CONFIGURATION, failure.problem)
        assertEquals(CommonStatusCodes.DEVELOPER_ERROR, failure.statusCode)
    }

    @Test fun explicitGoogleCancellationAndMissingResultAreDistinct() {
        val explicit = failure {
            readGoogleConsentResult(Activity.RESULT_CANCELED, hasPayload = true, senderFailed = false) {
                throw ApiException(Status(CommonStatusCodes.CANCELED))
            }
        }
        assertEquals(GoogleHealthProblem.AUTHORIZATION_CANCELLED, explicit.problem)
        val missing = failure {
            readGoogleConsentResult(Activity.RESULT_CANCELED, hasPayload = false, senderFailed = false) {
                fail("There is no result to decode")
            }
        }
        assertEquals(GoogleHealthProblem.AUTHORIZATION_INTERRUPTED, missing.problem)
    }

    @Test fun intentSenderFailureIsNotTreatedAsCancellation() {
        val failure = failure {
            readGoogleConsentResult(Activity.RESULT_CANCELED, hasPayload = true, senderFailed = true) {
                fail("The AndroidX error intent is not a Google result")
            }
        }
        assertEquals(GoogleHealthProblem.AUTHORIZATION_LAUNCH_FAILED, failure.problem)
    }

    @Test fun onlySuccessfulResultCanContinueToIdentityVerification() {
        val result = readGoogleConsentResult(Activity.RESULT_OK, hasPayload = true, senderFailed = false) { "decoded" }
        assertEquals("decoded", result)
        assertEquals(GoogleHealthProblem.AUTHORIZATION_INTERRUPTED, failure {
            readGoogleConsentResult(Activity.RESULT_CANCELED, hasPayload = true, senderFailed = false) { "decoded" }
        }.problem)
        assertEquals(GoogleHealthProblem.CONSENT, failure {
            readGoogleConsentResult(Activity.RESULT_OK, hasPayload = true, senderFailed = false) {
                throw GoogleHealthFailure(GoogleHealthProblem.CONSENT)
            }
        }.problem)
    }

    @Test fun authorizationStatusCodesKeepTheirRecoveryCategory() {
        assertEquals(GoogleHealthProblem.AUTHENTICATION,
            googleAuthorizationFailure(CommonStatusCodes.SIGN_IN_REQUIRED, fromConsent = false).problem)
        assertEquals(GoogleHealthProblem.NETWORK,
            googleAuthorizationFailure(CommonStatusCodes.NETWORK_ERROR, fromConsent = true).problem)
        assertEquals(GoogleHealthProblem.PLAY_SERVICES,
            googleAuthorizationFailure(CommonStatusCodes.API_NOT_CONNECTED, fromConsent = false).problem)
    }

    private fun failure(action: () -> Unit) = assertThrows(GoogleHealthFailure::class.java, action)
}
