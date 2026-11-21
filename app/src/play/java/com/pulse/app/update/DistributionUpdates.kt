package com.pulse.app.update

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.pulse.app.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class DistributionUpdates(context: Context) {
    private val manager = AppUpdateManagerFactory.create(context)
    val github = false
    val supportsAutomatic = false
    val available = !BuildConfig.DEBUG
    val downloadLabel = "Continue in Google Play"
    private var selected: AppUpdateInfo? = null

    suspend fun check(metadataOnly: Boolean = false): PulseRelease? {
        val info = suspendCancellableCoroutine { continuation ->
            manager.appUpdateInfo.addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }
        val latest = info.takeIf {
            it.updateAvailability() in setOf(UpdateAvailability.UPDATE_AVAILABLE, UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) &&
                it.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) && it.availableVersionCode() > BuildConfig.VERSION_CODE
        }
        if (!metadataOnly) selected = latest
        return latest?.availableVersionCode()?.let { PulseRelease("Build $it", it.toLong(), "") }
    }

    suspend fun prepare(release: PulseRelease, playLauncher: ActivityResultLauncher<IntentSenderRequest>, progress: (Int) -> Unit) {
        if (selected == null) check()
        val info = selected?.takeIf { it.availableVersionCode().toLong() == release.code }
            ?: throw UpdateFailure("Check for the Google Play update again.")
        selected = null
        if (!manager.startUpdateFlowForResult(info, playLauncher, AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE))) {
            throw UpdateFailure("Couldn't open Google Play's update screen. Check for the update again.")
        }
    }

    fun install(): Intent = error("Google Play manages installation.")
}
