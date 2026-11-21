package com.pulse.app.update

import android.app.Application
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class PulseRelease(
    val version: String,
    val code: Long,
    val notes: String,
    val page: String? = null
)

internal sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object Current : UpdateState
    data class Available(val release: PulseRelease) : UpdateState
    data class Downloading(val release: PulseRelease, val percent: Int) : UpdateState
    data class Ready(val release: PulseRelease) : UpdateState
    data class Problem(val message: String, val retryDownload: PulseRelease? = null) : UpdateState
}

internal const val PULSE_REPOSITORY = "https://github.com/THREALCOCOBOY/PULSE"
internal const val PULSE_MAINTAINER = "https://github.com/Threalcocoboy"

internal class PulseUpdates(application: Application) : AndroidViewModel(application) {
    private val distribution = DistributionUpdates(application)
    private val preferences = application.getSharedPreferences("pulse_updates", 0)
    private val status = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state = status.asStateFlow()
    private val auto = MutableStateFlow(preferences.getBoolean("automatic", false))
    val automatic = auto.asStateFlow()
    val supportsAutomatic = distribution.supportsAutomatic
    val available = distribution.available
    val downloadLabel = distribution.downloadLabel
    val github = distribution.github
    private val installNotice = MutableStateFlow<String?>(null)
    val installationMessage = installNotice.asStateFlow()
    private var task: Job? = null
    private var lastManualAttempt: Long? = null

    fun setAutomatic(enabled: Boolean) {
        auto.value = enabled
        preferences.edit().putBoolean("automatic", enabled).apply()
    }

    fun check(automatically: Boolean = false, found: (PulseRelease) -> Unit = {}) {
        if (!available || task?.isActive == true || status.value is UpdateState.Ready) return
        if (automatically && (!supportsAutomatic || !auto.value)) return
        val now = System.currentTimeMillis()
        val lastAutomaticAttempt = preferences.getLong("lastAutomaticAttempt", 0)
        if (automatically && now >= lastAutomaticAttempt && now - lastAutomaticAttempt < 24 * 60 * 60 * 1000L) return
        if (!automatically && lastManualAttempt?.let { now - it in 0..29_999 } == true) return
        lastManualAttempt = now
        if (supportsAutomatic) preferences.edit().putLong("lastAutomaticAttempt", now).apply()
        task = viewModelScope.launch {
            status.value = UpdateState.Checking
            try {
                val release = distribution.check()
                status.value = if (release == null) UpdateState.Current else UpdateState.Available(release)
                if (automatically && release != null && release.code > preferences.getLong("dismissedCode", 0)) found(release)
            } catch (e: CancellationException) {
                throw e
            } catch (e: UpdateFailure) {
                status.value = UpdateState.Problem(e.message.orEmpty())
            } catch (_: Exception) {
                status.value = UpdateState.Problem("Couldn't check for updates. Check your connection and try again.")
            }
        }
    }

    fun prepare(release: PulseRelease, playLauncher: ActivityResultLauncher<IntentSenderRequest>) {
        if (task?.isActive == true) return
        installNotice.value = null
        task = viewModelScope.launch {
            try {
                distribution.prepare(release, playLauncher) { status.value = UpdateState.Downloading(release, it) }
                status.value = if (github) UpdateState.Ready(release) else UpdateState.Available(release)
            } catch (e: CancellationException) {
                throw e
            } catch (e: UpdateFailure) {
                status.value = UpdateState.Problem(e.message.orEmpty(), release)
            } catch (_: Exception) {
                status.value = UpdateState.Problem("Couldn't prepare the update. Try again.", release)
            }
        }
    }

    fun cancelDownload() {
        val download = status.value as? UpdateState.Downloading ?: return
        task?.cancel()
        status.value = UpdateState.Available(download.release)
    }

    fun dismiss(release: PulseRelease) {
        preferences.edit().putLong("dismissedCode", release.code).apply()
    }

    fun install(launch: (Intent) -> Unit) {
        val ready = status.value as? UpdateState.Ready ?: return
        installNotice.value = null
        runCatching { launch(distribution.install()) }.onFailure {
            status.value = UpdateState.Problem("Couldn't open the installer. Download the update again.", ready.release)
        }
    }

    fun installerFinished(result: Int) {
        installNotice.value = when (result) {
            android.app.Activity.RESULT_OK -> "Android accepted the installation."
            android.app.Activity.RESULT_CANCELED -> "Installation was cancelled or permission was declined."
            else -> "The installer couldn't complete the update. You can try again."
        }
    }
}

internal class UpdateFailure(message: String) : Exception(message)
