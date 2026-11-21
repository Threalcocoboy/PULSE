package com.pulse.app.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pulse.app.BuildConfig
import com.pulse.app.MainActivity
import com.pulse.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

internal data class UpdateOffer(val version: String, val code: Long)

internal object UpdateAlerts {
    const val OPEN_UPDATES = "com.pulse.app.OPEN_UPDATES"
    private const val CHANNEL = "pulse_app_updates"
    private const val ID = 403
    private const val WORK = "pulse-check-app-updates"
    private const val HOUR = 3_600_000L
    private val mutex = Mutex()

    fun preferences(context: Context): SharedPreferences = context.getSharedPreferences("pulse_updates", Context.MODE_PRIVATE)
    fun enabled(context: Context) = preferences(context).getBoolean("automatic", true)

    fun schedule(context: Context, active: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (active) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(12, TimeUnit.HOURS)
                .setInitialDelay(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            manager.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        } else {
            manager.cancelUniqueWork(WORK)
            clearNotification(context)
        }
    }

    suspend fun check(context: Context): UpdateOffer? = mutex.withLock {
        if (!enabled(context)) return@withLock null
        val distribution = DistributionUpdates(context)
        if (!distribution.available || !distribution.supportsAutomatic) return@withLock null
        val prefs = preferences(context)
        val now = System.currentTimeMillis()
        val interval = if (prefs.getBoolean("lastAlertCheckSucceeded", false)) 6 * HOUR else HOUR / 2
        if (now - prefs.getLong("lastAlertCheck", 0) !in 0L until interval) {
            try {
                val release = distribution.check(metadataOnly = true)
                if (!enabled(context)) return@withLock null
                prefs.edit().putLong("lastAlertCheck", now).putBoolean("lastAlertCheckSucceeded", true)
                    .putLong("offerCheckedAt", now).putLong("offerCode", release?.code ?: 0L)
                    .putString("offerVersion", release?.version).apply()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                prefs.edit().putLong("lastAlertCheck", now).putBoolean("lastAlertCheckSucceeded", false).apply()
            }
        }
        val offer = pending(context)
        if (offer == null) clearNotification(context) else notify(context, prefs, offer)
        offer
    }

    fun pending(context: Context): UpdateOffer? {
        val prefs = preferences(context)
        val code = prefs.getLong("offerCode", 0)
        val age = System.currentTimeMillis() - prefs.getLong("offerCheckedAt", 0)
        if (!enabled(context) || code <= maxOf(BuildConfig.VERSION_CODE.toLong(), prefs.getLong("dismissedCode", 0)) ||
            age !in 0L..36 * HOUR) return null
        return prefs.getString("offerVersion", null)?.let { UpdateOffer(it, code) }
    }

    fun dismiss(context: Context, code: Long) {
        val prefs = preferences(context)
        if (code > prefs.getLong("dismissedCode", 0)) prefs.edit().putLong("dismissedCode", code).apply()
        if (pending(context) == null) clearNotification(context)
    }

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)

    fun prepareChannel(context: Context) {
        manager(context).createNotificationChannel(NotificationChannel(CHANNEL, "PULSE updates", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "New versions available to install"; setShowBadge(false) })
    }

    fun runtimePermissionMissing(context: Context) = Build.VERSION.SDK_INT >= 33 &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    fun allowed(context: Context) = !runtimePermissionMissing(context) && manager(context).areNotificationsEnabled() &&
        manager(context).getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE

    fun openSettings(context: Context) {
        prepareChannel(context)
        val channel = manager(context).areNotificationsEnabled()
        val intent = Intent(if (channel) Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS else Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        if (channel) intent.putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL)
        context.startActivity(intent)
    }

    private fun clearNotification(context: Context) = manager(context).cancel(ID)

    private fun notify(context: Context, prefs: SharedPreferences, offer: UpdateOffer) {
        if (offer.code <= prefs.getLong("notifiedCode", 0) || !allowed(context) || pending(context) != offer) return
        prepareChannel(context)
        val opening = PendingIntent.getActivity(context, ID, Intent(context, MainActivity::class.java)
            .setAction(OPEN_UPDATES)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_pulse)
            .setContentTitle("PULSE ${offer.version} is available")
            .setContentText("Tap to review and install the update.")
            .setContentIntent(opening)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        try {
            manager(context).notify(ID, notification)
            prefs.edit().putLong("notifiedCode", offer.code).apply()
        } catch (_: SecurityException) {
            // Android permissions can change between the check and posting.
        }
    }
}

class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        UpdateAlerts.check(applicationContext)
        return Result.success()
    }
}
