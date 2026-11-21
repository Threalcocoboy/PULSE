package com.pulse.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pulse.app.core.*
import com.pulse.app.integration.ProgressNotifications
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun NotificationSetup(p: Preferences, s: HealthSnapshot, update: ((Preferences) -> Preferences) -> Unit,
    advance: (() -> Unit)? = null) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(ProgressNotifications.allowed(context)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) allowed = ProgressNotifications.allowed(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted && ProgressNotifications.allowed(context)
        val enabled = allowed
        update { it.copy(progressNotifications = enabled,
            notificationSetup = if (enabled) SetupStatus.COMPLETE else SetupStatus.NEEDS_ATTENTION) }
    }
    val available = ProgressNotifications.supported(p, s)
    val selected = p.notificationMetrics.intersect(available.toSet())
    val enabled = p.progressNotifications && allowed && selected.isNotEmpty()
    if (advance == null) Text("Show daily progress in a quiet notification.")
    if (available.isEmpty()) {
        SetupFeedback(SetupStatus.NOT_STARTED, "Progress notifications unavailable",
            "Connect a source with supported metrics in Devices & Data.")
        if (p.progressNotifications && advance == null) SmallAction("Disable progress notifications") {
            update { it.copy(progressNotifications = false, notificationSetup = SetupStatus.SKIPPED) }
        }
        return
    }
    available.forEach { metric ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(metric in selected, { checked ->
                update { current ->
                    val next = if (checked) current.notificationMetrics + metric else current.notificationMetrics - metric
                    current.copy(notificationMetrics = next, progressNotifications = current.progressNotifications &&
                        next.any { it in available })
                }
            })
            Text(if (metric == Metric.CALORIES) "Total calories" else metric.label)
        }
    }
    val preview = ProgressNotifications.progress(p, s, LocalDate.now(), ZoneId.systemDefault())
    PulseCard(Modifier.fillMaxWidth()) {
        Label("Preview")
        Text("PULSE — Daily progress", style = MaterialTheme.typography.titleMedium)
        if (preview.lines.isEmpty()) Label("Select a metric to preview your notification.")
        else preview.lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
    if (enabled) {
        val background = p.mode == DataMode.GARMIN ||
            androidx.health.connect.client.permission.HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND in s.granted
        SetupFeedback(SetupStatus.COMPLETE, "Progress notifications enabled",
            if (background) "Updates after PULSE syncs. Android controls background timing."
            else "Updates when PULSE syncs while the app is open. Background access is optional in Devices & Data.")
        if (advance != null) PulseButton("Continue", Modifier.fillMaxWidth(), onClick = advance)
        else PulseButton("Disable progress notifications", Modifier.fillMaxWidth()) {
            update { it.copy(progressNotifications = false, notificationSetup = SetupStatus.SKIPPED) }
        }
    } else {
        if ((p.notificationSetup == SetupStatus.NEEDS_ATTENTION || p.progressNotifications) && !allowed) {
            SetupFeedback(SetupStatus.NEEDS_ATTENTION, "Notifications disabled", "Allow PULSE notifications in Android to enable daily progress.")
            SmallAction("Open notification settings") {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .putExtra(Settings.EXTRA_CHANNEL_ID, ProgressNotifications.CHANNEL))
                }
            }
        }
        PulseButton("Enable progress notifications", Modifier.fillMaxWidth(), selected.isNotEmpty()) {
            ProgressNotifications.prepare(context)
            allowed = ProgressNotifications.allowed(context)
            if (allowed) update { it.copy(progressNotifications = true, notificationSetup = SetupStatus.COMPLETE) }
            else if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else update { it.copy(progressNotifications = false, notificationSetup = SetupStatus.NEEDS_ATTENTION) }
        }
    }
}
