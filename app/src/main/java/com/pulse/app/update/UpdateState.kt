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
    private val status = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state = status.asStateFlow()
    private val auto = MutableStateFlow(UpdateAlerts.enabled(application))
    val automatic = auto.asStateFlow()
    val supportsAutomatic = distribution.supportsAutomatic
    val available = distribution.available
    val downloadLabel = distribution.downloadLabel
    val github = distribution.github
    private val installNotice = MutableStateFlow<String?>(null)
    val installationMessage = installNotice.asStateFlow()
    private var task: Job? = null
    private var lastManualAttempt: Long? = null
    private var automaticCheck: Job? = null
    private val offer = MutableStateFlow<UpdateOffer?>(null)
    val popup = offer.asStateFlow()
    private val open = MutableStateFlow(false)
    val openRequested = open.asStateFlow()

    init {
        if (supportsAutomatic) UpdateAlerts.schedule(application, available && auto.value)
    }

    fun setAutomatic(enabled: Boolean) {
        auto.value = enabled
        val context = getApplication<Application>()
        UpdateAlerts.preferences(context).edit().putBoolean("automatic", enabled).apply()
        UpdateAlerts.schedule(context, supportsAutomatic && available && enabled)
        if (enabled) checkOnOpen() else offer.value = null
    }

    fun checkOnOpen() {
        if (!available || !supportsAutomatic || !auto.value || automaticCheck?.isActive == true) return
        val context = getApplication<Application>()
        if (!open.value) offer.value = UpdateAlerts.pending(context)
        automaticCheck = viewModelScope.launch {
            UpdateAlerts.check(context)
            if (!open.value) offer.value = UpdateAlerts.pending(context)
        }
    }

    fun dismissOffer(code: Long) {
        UpdateAlerts.dismiss(getApplication(), code)
        if (offer.value?.code == code) offer.value = null
    }

    fun openFromNotification() {
        UpdateAlerts.pending(getApplication())?.let { dismissOffer(it.code) }
        open.value = true
    }

    fun consumeOpenRequest() { open.value = false }

    fun check() {
        if (!available || task?.isActive == true || status.value is UpdateState.Ready) return
        val now = System.currentTimeMillis()
        if (lastManualAttempt?.let { now - it in 0..29_999 } == true) return
        lastManualAttempt = now
        task = viewModelScope.launch {
            status.value = UpdateState.Checking
            try {
                val release = distribution.check()
                status.value = if (release == null) UpdateState.Current else UpdateState.Available(release)
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
        dismissOffer(release.code)
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
