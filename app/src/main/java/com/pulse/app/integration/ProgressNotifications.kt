package com.pulse.app.integration

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pulse.app.MainActivity
import com.pulse.app.R
import com.pulse.app.core.*
import java.text.NumberFormat
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object ProgressNotifications {
    const val CHANNEL = "pulse_progress"
    const val OPEN_DASHBOARD = "com.pulse.app.OPEN_DAILY_PROGRESS"
    private const val ID = 401
    private val metrics = listOf(Metric.STEPS, Metric.DISTANCE, Metric.ACTIVE_CALORIES, Metric.ACTIVE,
        Metric.CALORIES, Metric.FLOORS, Metric.HYDRATION, Metric.ACTIVITIES)

    fun supported(p: Preferences, s: HealthSnapshot) =
        metrics.filter { p.mode != DataMode.NONE && p.mode == s.mode && s.access.verified && it in s.available }

    fun prepare(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "PULSE daily progress", NotificationManager.IMPORTANCE_LOW))
    }

    fun allowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun cancel(context: Context) = NotificationManagerCompat.from(context).cancel(ID)

    data class Progress(val date: LocalDate, val lines: List<String>, val updated: Instant?)

    fun progress(p: Preferences, s: HealthSnapshot, date: LocalDate, zone: ZoneId): Progress {
        val day = s.days.firstOrNull { it.date == date }
        val chosen = supported(p, s).filter { it in p.notificationMetrics }
        val readings = chosen.mapNotNull { metric ->
            day?.readings?.get(metric)?.takeIf { reading ->
                reading.state == DataState.READY && reading.value?.let { it.isFinite() && it >= 0 } == true &&
                    reading.updated?.atZone(zone)?.toLocalDate() == date
            }?.let { metric to it }
        }.toMap()
        val lines = chosen.map { metric ->
            val value = readings[metric]?.value ?: return@map "${label(metric)}: No data yet"
            val goal = Goals.indicator(metric, p)
            val amount = format(value, metric, p.imperial)
            "${label(metric)}: $amount${goal?.let { " / ${format(it, metric, p.imperial)}" }.orEmpty()}"
        }
        return Progress(date, lines, readings.values.mapNotNull { it.updated }.maxOrNull())
    }

    private fun label(metric: Metric) = if (metric == Metric.CALORIES) "Total calories" else metric.label

    private fun format(value: Double, metric: Metric, imperial: Boolean): String {
        val miles = metric == Metric.DISTANCE && imperial
        val amount = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }
            .format(if (miles) value * 0.621371 else value)
        return if (metric == Metric.STEPS) amount else "$amount ${if (miles) "mi" else metric.unit}"
    }

    fun publish(context: Context, p: Preferences, s: HealthSnapshot) {
        if (!p.onboarded || !p.progressNotifications || p.mode == DataMode.NONE || p.mode != s.mode || !allowed(context)) {
            cancel(context)
            return
        }
        if (s.loading) return
        if (!s.access.verified) {
            if (s.access != ProviderAccess.ERROR || p.mode != DataMode.GARMIN) cancel(context)
            return
        }
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val date = now.atZone(zone).toLocalDate()
        val progress = progress(p, s, date, zone)
        val updated = progress.updated
        if (updated == null) {
            cancel(context)
            return
        }
        prepare(context)
        val intent = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java)
            .setAction(OPEN_DASHBOARD).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val dateLabel = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        val time = updated.atZone(zone).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        val expires = if (p.mode == DataMode.GOOGLE_HEALTH) minOf(end, updated.plus(Duration.ofHours(2))) else end
        if (expires <= now) {
            cancel(context)
            return
        }
        val expiry = Duration.between(now, expires).toMillis().coerceAtLeast(1)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_pulse)
            .setContentTitle("PULSE — Daily progress")
            .setContentText("$dateLabel · ${progress.lines.first()}")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$dateLabel\n${progress.lines.joinToString("\n")}\nLast updated: $time"))
            .setContentIntent(intent).setOnlyAlertOnce(true).setSilent(true).setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setWhen(updated.toEpochMilli())
            .setTimeoutAfter(expiry).build()
        try {
            NotificationManagerCompat.from(context).notify(ID, notification)
        } catch (_: SecurityException) {
            cancel(context)
        }
    }
}
