package com.pulse.app.integration

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pulse.app.MainActivity
import com.pulse.app.R
import com.pulse.app.core.*
import com.pulse.app.data.PreferenceStore
import com.pulse.app.integration.googlehealth.GoogleHealthAvailability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.TimeUnit

internal object LiveGoalNotifications {
    const val CHANNEL = "pulse_live_goal"
    const val OPEN_GOAL = "com.pulse.app.OPEN_TRACKED_GOAL"
    const val GOAL_ID = "goal_id"
    private const val ID = 402
    private const val DISMISS = "com.pulse.app.DISMISS_TRACKED_GOAL"
    private const val STOP = "com.pulse.app.STOP_TRACKED_GOAL"
    private const val PERIOD_WORK = "pulse-goal-period"
    private const val KEY = "pulse_goal_period"
    private const val CONTENT = "pulse_goal_content"
    private const val SYNC = "pulse_goal_sync"
    private val mutex = Mutex()

    enum class Permission(val label: String) {
        ALLOWED("Allowed"), RUNTIME("Notification permission needed"),
        APP_BLOCKED("PULSE notifications disabled"), CHANNEL_BLOCKED("Goal tracking channel disabled")
    }

    fun prepare(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "PULSE · Goal Tracking", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progress for your selected personal goal"
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        )
    }

    fun permission(context: Context): Permission {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return Permission.RUNTIME
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return Permission.APP_BLOCKED
        if (manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) {
            return Permission.CHANNEL_BLOCKED
        }
        return Permission.ALLOWED
    }

    fun compatible(goal: PersonalGoal) = goal.valid() &&
        GoalSources.providers(goal.metric).any(GoogleHealthAvailability::canConnect)

    fun selected(p: Preferences): PersonalGoal? = Goals.active(p).firstOrNull {
        it.id == p.liveGoal.goalId && compatible(it)
    }

    fun reading(goal: PersonalGoal, snapshot: HealthSnapshot?, mode: DataMode, today: LocalDate, zone: ZoneId): LiveGoalReading {
        if (snapshot?.mode == DataMode.HEALTH_CONNECT &&
            snapshot.sourceScope?.let { it.startsWith("HEALTH_CONNECT:") && it != "HEALTH_CONNECT:${zone.id}" } == true) {
            return goal.liveGoalReading(null, mode, today, zone)
        }
        val permissionMissing = snapshot != null && snapshot.access.verified &&
            goal.metric.source !in snapshot.available && GoalSources.missingReadPermission(goal.metric, snapshot, mode)
        return goal.liveGoalReading(snapshot, mode, today, zone, permissionMissing)
    }

    fun canRefresh(context: Context, p: Preferences) = p.liveGoal.enabled && !p.liveGoal.dismissed &&
        selected(p) != null && permission(context) == Permission.ALLOWED

    private fun scheduleBoundary(context: Context, now: ZonedDateTime, boundary: ZonedDateTime) {
        val delay = Duration.between(now, boundary).toMillis().coerceAtLeast(1)
        val work = OneTimeWorkRequestBuilder<LiveGoalPeriodWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "$PERIOD_WORK-${boundary.toInstant().toEpochMilli()}", ExistingWorkPolicy.KEEP, work)
    }

    suspend fun publish(context: Context, snapshot: HealthSnapshot? = null, expectedGoalId: String? = null) = mutex.withLock {
        val p = PreferenceStore(context).preferences.first()
        if (expectedGoalId == null || p.liveGoal.goalId == expectedGoalId) reconcile(context, p, snapshot)
    }

    private fun reconcile(context: Context, p: Preferences, snapshot: HealthSnapshot?) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val choice = p.liveGoal
        val goal = selected(p)
        if (!p.onboarded || !choice.enabled || choice.dismissed || goal == null ||
            permission(context) != Permission.ALLOWED) {
            manager.cancel(ID)
            return
        }

        prepare(context)
        val now = ZonedDateTime.now()
        val today = now.toLocalDate()
        val period = goal.liveGoalPeriod(today)
        val boundary = goal.nextLiveGoalPeriod(now)
        val remainingTime = Duration.between(now, boundary).toMillis().coerceAtLeast(1)
        val key = listOf(choice.token, p.mode, period, now.zone, p.imperial, choice.hideOnLockScreen, goal.hashCode())
            .joinToString("|")
        val old = manager.activeNotifications.firstOrNull { it.id == ID }?.notification?.extras
        if (old?.getString(KEY) != key) scheduleBoundary(context, now, boundary)

        val reading = reading(goal, snapshot, p.mode, today, now.zone)
        val sync = snapshot?.synced?.toEpochMilli() ?: 0L
        if (old?.getString(KEY) == key) {
            if (reading.status == LiveGoalStatus.SYNCING) return
            if (reading.progress.value != null && sync != 0L && old.getLong(SYNC) > sync) return
        }

        val value = reading.progress.value
        val amount = when {
            value == null -> reading.status.label
            goal.frequency != GoalFrequency.EACH_PERIOD -> goal.progressText(reading.progress, p.imperial)
            goal.condition == GoalCondition.MINIMUM ->
                "${goal.metric.amount(value, p.imperial)} / ${goal.metric.amount(goal.target, p.imperial)}"
            else -> goal.metric.amount(value, p.imperial)
        }
        val status = when {
            reading.status == LiveGoalStatus.COMPLETED -> reading.status.label
            reading.percent != null && reading.remaining != null && reading.remaining > 0 -> {
                val remaining = if (goal.frequency == GoalFrequency.EACH_PERIOD) goal.metric.amount(reading.remaining, p.imperial)
                    else "${reading.remaining.toInt()} ${if (reading.remaining == 1.0) "day" else "days"}"
                "${reading.percent}% · $remaining remaining"
            }
            reading.percent != null -> "${reading.percent}% of target recorded"
            reading.status == LiveGoalStatus.TRACKING -> goal.summary(p.imperial)
            value != null -> reading.status.label
            else -> ""
        }
        val periodText = goal.liveGoalPeriodLabel(today)
        val dateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(now.zone)
        val updated = reading.updated?.let { "Last updated ${dateTime.format(it)}" }
        val header = listOfNotNull(status.takeIf { it.isNotEmpty() }, periodText, updated).joinToString(" · ")
        val content = listOf(amount, header, reading.percent).joinToString("|")
        if (old?.getString(KEY) == key && old.getString(CONTENT) == content) return

        val opening = PendingIntent.getActivity(context, ID, Intent(context, MainActivity::class.java)
            .setAction(OPEN_GOAL).setData(Uri.fromParts("pulse-goal", goal.id, null)).putExtra(GOAL_ID, goal.id)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        fun actionIntent(action: String) = PendingIntent.getBroadcast(context, ID,
            Intent(context, LiveGoalReceiver::class.java).setAction(action)
                .setData(Uri.fromParts("pulse-tracking", choice.token, null)), PendingIntent.FLAG_IMMUTABLE)
        val public = Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_pulse)
            .setContentTitle("PULSE · Goal tracking").setContentText("Unlock to see goal progress")
            .setShowWhen(false).setContentIntent(opening).build()
        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_pulse).setColor(Color.rgb(200, 16, 46))
            .setContentTitle("PULSE · ${goal.metric.title}").setContentText(amount).setSubText(header)
            .setShowWhen(false).setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(if (choice.hideOnLockScreen) Notification.VISIBILITY_SECRET else Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(public).setContentIntent(opening).setDeleteIntent(actionIntent(DISMISS))
            .addAction(Notification.Action.Builder(null, "Stop tracking", actionIntent(STOP)).build())
            .setTimeoutAfter(remainingTime).setExtras(Bundle().apply {
                putString(KEY, key)
                putString(CONTENT, content)
                putLong(SYNC, sync)
            })
        when {
            Build.VERSION.SDK_INT >= 36 && reading.percent != null -> builder.setStyle(progressStyle(reading.percent))
            reading.percent != null -> builder.setProgress(100, reading.percent, false)
            Build.VERSION.SDK_INT >= 37 && value != null -> builder.setStyle(metricStyle(amount, reading.status, goal))
        }
        try {
            manager.notify(ID, builder.build())
        } catch (_: SecurityException) {
            // Permission may have changed since the earlier check.
            manager.cancel(ID)
        }
    }

    @RequiresApi(36)
    private fun progressStyle(percent: Int) = Notification.ProgressStyle()
        .addProgressSegment(Notification.ProgressStyle.Segment(100))
        .setProgress(percent)

    @RequiresApi(37)
    private fun metricStyle(amount: String, status: LiveGoalStatus, goal: PersonalGoal) = Notification.MetricStyle()
        .addMetric(Notification.Metric(Notification.Metric.FixedText(amount), "Recorded"))
        .addMetric(Notification.Metric(Notification.Metric.FixedText(
            if (status == LiveGoalStatus.TRACKING) goal.condition.title else status.label), "Goal status"))

    suspend fun receive(context: Context, intent: Intent) = mutex.withLock {
        val store = PreferenceStore(context)
        when (intent.action) {
            DISMISS, STOP -> {
                val token = intent.data?.schemeSpecificPart ?: return@withLock
                store.update { it.copy(liveGoal = it.liveGoal.act(token, stop = intent.action == STOP)) }
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> Unit
            else -> return@withLock
        }
        reconcile(context, store.preferences.first(), null)
    }
}

internal fun PersonalGoal.liveGoalPeriodLabel(today: LocalDate): String {
    if (completionCadence == GoalCadence.DAILY) return "Today"
    val start = liveGoalPeriod(today)
    val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    return "${date.format(start)} – ${date.format(start.plusDays(6))}"
}

class LiveGoalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                LiveGoalNotifications.receive(context.applicationContext, intent)
            } finally {
                result.finish()
            }
        }
    }
}

class LiveGoalPeriodWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        LiveGoalNotifications.publish(applicationContext)
        return Result.success()
    }
}
