package com.pulse.app.integration

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.work.*
import com.pulse.app.core.*
import com.pulse.app.data.*
import com.pulse.app.integration.garmin.*
import com.pulse.app.integration.googlehealth.GoogleHealthProvider
import com.pulse.app.integration.googlehealth.GoogleHealthAvailability
import com.pulse.app.integration.googlehealth.GoogleHealthResult
import com.pulse.app.integration.googlehealth.GoogleHealthProblem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.TimeUnit

internal class ProviderSync(private val context: Context, private val dao: HealthDao) {
    companion object {
        val mutex = Mutex()
        private var garminApi: Pair<String, GarminApi>? = null
        fun clearGarminSession() { garminApi = null }
    }

    suspend fun load(mode: DataMode, today: LocalDate, background: Boolean = false,
        onCached: ((HealthSnapshot) -> Unit)? = null, preferences: Preferences = Preferences(),
        force: Boolean = false, onGoogle: ((GoogleHealthResult) -> Unit)? = null): HealthSnapshot = when (mode) {
        DataMode.NONE -> HealthSnapshot(status = "Connect a health source in Settings.", mode = mode)
        DataMode.HEALTH_CONNECT -> HealthConnectRepository(context, dao).load(today, background)
        DataMode.GOOGLE_HEALTH -> {
            val provider = GoogleHealthProvider(context)
            if (GoogleHealthAvailability.enabled) {
                val google = provider.load(today, background, force)
                onGoogle?.invoke(google)
                combineGoogle(google.snapshot, today, preferences, background)
            } else {
                combineGoogle(provider.pausedSnapshot(today), today, preferences, background)
            }
        }
        DataMode.GARMIN -> withContext(Dispatchers.IO) {
            val credential = try {
                GarminKeyStore(context).credential()
            } catch (_: GarminFailure) {
                null
            }
            if (credential == null) {
                HealthSnapshot(status = "Enter a Garmin key to connect.", mode = mode)
            } else {
                val api = garminApi?.takeIf { it.first == credential.scope }?.second
                    ?: GarminApi(OfficialGarminTransport(), { GarminKeyStore(context).credential()?.key }).also {
                        garminApi = credential.scope to it
                    }
                val repository = GarminRepository(api, dao, credential.scope, bindAccount = if (credential.account == null) {
                    { account -> runCatching { GarminKeyStore(context).save(credential.key, credential.scope, account) } }
                } else null)
                if (onCached != null) repository.cached().takeIf { it.days.isNotEmpty() }?.let(onCached)
                repository.load(today, background)
            }
        }
    }

    suspend fun combineGoogle(google: HealthSnapshot, today: LocalDate, p: Preferences, background: Boolean): HealthSnapshot {
        if (!p.combineHealthConnect) return google
        val local = HealthConnectRepository(context, dao)
        val secondary = if (background && !canReadHealthInBackground(context)) local.cachedForBackground() else local.load(today, background)
        return withContext(Dispatchers.Default) {
            ProviderPriority.merge(google, secondary, p.providerPriority, java.time.ZoneId.systemDefault())
        }
    }
}

class HealthSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        ProviderSync.mutex.withLock { synchronize() }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        if (runAttemptCount < 2) Result.retry() else Result.success()
    }

    private suspend fun synchronize(): Result {
        val prefs = PreferenceStore(applicationContext)
        val initial = prefs.preferences.first()
        if (!initial.onboarded || initial.mode == DataMode.NONE ||
            !GoogleHealthAvailability.canConnect(initial.mode)) return Result.success()
        if (initial.mode == DataMode.HEALTH_CONNECT && !canReadHealthInBackground(applicationContext)) {
            ProgressNotifications.cancel(applicationContext)
            return Result.success()
        }
        val challenges = ChallengeRepository(applicationContext)
        if (initial.mode == DataMode.GARMIN && !wantsGarminWork(applicationContext, initial, challenges.challenges.first())) {
            return Result.success()
        }
        val db = PulseDatabase.open(applicationContext)
        return try {
            val today = LocalDate.now()
            var google: GoogleHealthResult? = null
            val snapshot = ProviderSync(applicationContext, db.health()).load(initial.mode, today, background = true,
                preferences = initial, onGoogle = { google = it })
            val current = prefs.preferences.first()
            if (current.onboarded && current.mode == initial.mode && LocalDate.now() == today) {
                challenges.evaluate(snapshot, today, current)
                ProgressNotifications.publish(applicationContext, current, snapshot)
            }
            if (google?.problem in setOf(GoogleHealthProblem.NETWORK, GoogleHealthProblem.RETRY) && runAttemptCount < 3)
                Result.retry() else Result.success()
        } finally {
            db.close()
        }
    }

    companion object {
        private const val WORK_NAME = "pulse-health-refresh"

        private fun wantsGarminWork(context: Context, p: Preferences, items: List<PersonalChallenge>) =
            p.progressNotifications && ProgressNotifications.allowed(context) || items.any {
                it.tracking.automatic && it.source == DataMode.GARMIN && it.completed == null &&
                    it.status !in setOf(ChallengeStatus.ARCHIVED, ChallengeStatus.PAUSED, ChallengeStatus.EXPIRED)
            }

        fun schedule(context: Context, p: Preferences, snapshot: HealthSnapshot, items: List<PersonalChallenge>) {
            val manager = WorkManager.getInstance(context)
            if (!GoogleHealthAvailability.canConnect(p.mode)) {
                manager.cancelUniqueWork(WORK_NAME)
                return
            }
            if (p.onboarded && snapshot.mode == p.mode && snapshot.loading) return
            val enabled = p.onboarded && snapshot.mode == p.mode && (snapshot.access.verified || snapshot.access == ProviderAccess.ERROR) && when (p.mode) {
                DataMode.NONE -> false
                DataMode.HEALTH_CONNECT -> HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND in snapshot.granted
                DataMode.GARMIN -> wantsGarminWork(context, p, items)
                DataMode.GOOGLE_HEALTH -> snapshot.sourceScope != null || snapshot.access.verified
            }
            if (!enabled) {
                manager.cancelUniqueWork(WORK_NAME)
                return
            }
            val constraints = Constraints.Builder().setRequiresBatteryNotLow(true)
                .setRequiredNetworkType(if (p.mode in setOf(DataMode.GARMIN, DataMode.GOOGLE_HEALTH)) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED)
                .build()
            val request = PeriodicWorkRequestBuilder<HealthSyncWorker>(1, TimeUnit.HOURS)
                .setInitialDelay(if (p.mode == DataMode.GOOGLE_HEALTH) 0 else 1, TimeUnit.HOURS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setConstraints(constraints).build()
            manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}

private suspend fun canReadHealthInBackground(context: Context): Boolean {
    if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) return false
    val client = HealthConnectClient.getOrCreate(context)
    return client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) ==
        HealthConnectFeatures.FEATURE_STATUS_AVAILABLE &&
        HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND in client.permissionController.getGrantedPermissions()
}
