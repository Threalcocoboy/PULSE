package com.pulse.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pulse.app.core.*
import com.pulse.app.integration.GoalSources
import com.pulse.app.integration.LiveGoalNotifications
import com.pulse.app.integration.LiveGoalNotifications.Permission
import com.pulse.app.integration.googlehealth.GoogleHealthAvailability
import com.pulse.app.integration.liveGoalPeriodLabel
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun LiveGoalTrackingSettings(p: Preferences, snapshot: HealthSnapshot,
    update: ((Preferences) -> Preferences) -> Unit, refresh: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val choice = p.liveGoal
    val selected = LiveGoalNotifications.selected(p)
    val goals = Goals.active(p)
    val supported = goals.filter(LiveGoalNotifications::compatible)
    var permission by remember { mutableStateOf(LiveGoalNotifications.permission(context)) }
    var picker by rememberSaveable { mutableStateOf(false) }
    var enableWhenSelected by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permission = LiveGoalNotifications.permission(context)
        refresh()
    }
    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permission = LiveGoalNotifications.permission(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val zone = ZoneId.systemDefault()
    val reading = selected?.let { LiveGoalNotifications.reading(it, snapshot, p.mode, LocalDate.now(zone), zone) }
    val status = when {
        selected == null -> if (choice.goalId == null) "No goal selected" else "Selected goal unavailable"
        !choice.enabled -> "Off"
        choice.dismissed -> "Paused after dismissal"
        permission != Permission.ALLOWED -> "Waiting for notification permission"
        else -> reading?.status?.label.orEmpty()
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Live Goal Tracking", style = MaterialTheme.typography.titleLarge)
        Label("Keep an eye on one personal goal directly from your notifications.")
        SettingToggle("Enable live goal tracking", choice.enabled) { enabled ->
            if (enabled && selected == null) {
                enableWhenSelected = true
                picker = true
            } else update { it.copy(liveGoal = it.liveGoal.enable(enabled)) }
        }
        PulseCard(Modifier.fillMaxWidth()) {
            Label("Selected goal")
            Text(selected?.metric?.title ?: "None", style = MaterialTheme.typography.titleMedium)
            selected?.let { Label(it.summary(p.imperial)) }
            Label("Status · $status")
            val updated = reading?.updated?.let {
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(zone).format(it)
            }
            Label("Last updated · ${updated ?: "No reading yet"}")
            SmallAction(if (selected == null) "Choose goal" else "Change goal") {
                enableWhenSelected = false
                picker = true
            }
            if (choice.enabled && choice.dismissed && selected != null) {
                SmallAction("Resume tracking") { update { it.copy(liveGoal = it.liveGoal.enable(true)) } }
            }
        }
        Label("Notifications · ${permission.label}")
        if (permission != Permission.ALLOWED) {
            Label("Allow PULSE to show progress for the goal you select.")
            val canRequest = Build.VERSION.SDK_INT >= 33 && permission == Permission.RUNTIME && !choice.permissionRequested
            SmallAction(if (canRequest) "Allow notifications" else "Open Android notification settings") {
                LiveGoalNotifications.prepare(context)
                if (canRequest) {
                    update { it.copy(liveGoal = it.liveGoal.copy(permissionRequested = true)) }
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else context.openLiveGoalNotificationSettings(permission)
            }
        }
        SettingToggle("Hide goal notification on lock screen", choice.hideOnLockScreen) { hidden ->
            update { it.copy(liveGoal = it.liveGoal.copy(hideOnLockScreen = hidden)) }
        }
        Label("Android 16 and later support an enhanced progress display. PULSE refreshes the goal when health data syncs. Android controls background timing.")
    }
    if (picker) {
        LiveGoalPicker(p, snapshot, supported, goals.size - supported.size, dismiss = { picker = false }) { id ->
            val enable = enableWhenSelected
            update {
                val next = it.liveGoal.select(id)
                it.copy(liveGoal = if (enable) next.enable(true) else next)
            }
        }
    }
}

private fun Context.openLiveGoalNotificationSettings(permission: Permission) {
    val channel = permission == Permission.CHANNEL_BLOCKED
    val intent = Intent(if (channel) Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS else Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    if (channel) intent.putExtra(Settings.EXTRA_CHANNEL_ID, LiveGoalNotifications.CHANNEL)
    startActivity(intent)
}

@Composable
private fun LiveGoalPicker(p: Preferences, snapshot: HealthSnapshot, goals: List<PersonalGoal>,
    incompatible: Int, dismiss: () -> Unit, choose: (String) -> Unit) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    PulseSheet(dismiss) { close ->
        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Choose one goal", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconAction("close", "Close goal picker") { close(dismiss) }
            }
            if (goals.isEmpty()) {
                Label(if (incompatible == 0) "Save a personal goal in Profile & Goals first." else "No saved goals have a supported data source.")
            } else {
                LazyColumn(Modifier.weight(1f, fill = false).selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(goals, key = { it.id }) { goal ->
                        val selected = p.liveGoal.goalId == goal.id
                        val reading = LiveGoalNotifications.reading(goal, snapshot, p.mode, today, zone)
                        val source = GoalSources.availability(goal.metric, snapshot, p.mode)
                        PulseCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton) {
                                choose(goal.id)
                                close(dismiss)
                            }, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Glyph(goal.metric.category.icon, color = Red)
                                Column(Modifier.weight(1f)) {
                                    Text(goal.metric.title, style = MaterialTheme.typography.titleMedium)
                                    Label(goal.summary(p.imperial))
                                    Label(goal.liveGoalPeriodLabel(today))
                                    Label(if (reading.progress.value == null) reading.status.label
                                        else goal.progressText(reading.progress, p.imperial))
                                    val names = source.providers.filter(GoogleHealthAvailability::canConnect).map { it.displayName() }
                                    Label((listOf(source.label) + names).joinToString(" · "))
                                }
                                RadioButton(selected, onClick = null)
                            }
                        }
                    }
                }
            }
            if (incompatible > 0) Label("Goals without a supported health source are hidden.")
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
internal fun LiveGoalDetail(id: String, p: Preferences, snapshot: HealthSnapshot, manage: () -> Unit, back: () -> Unit) {
    val goal = Goals.active(p).firstOrNull { it.id == id }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        IconAction("back", "Back", onClick = back)
        SectionTitle(goal?.metric?.title ?: "Personal goal")
        if (goal == null) {
            Label("This goal is no longer available.")
        } else {
            val reading = LiveGoalNotifications.reading(goal, snapshot, p.mode, today, zone)
            PulseCard(Modifier.fillMaxWidth()) {
                Label(goal.liveGoalPeriodLabel(today))
                Text(goal.summary(p.imperial), style = MaterialTheme.typography.titleMedium)
                Label(reading.status.label)
                if (reading.progress.value != null) {
                    Text(goal.progressText(reading.progress, p.imperial), style = MaterialTheme.typography.headlineSmall)
                }
                reading.percent?.let { PulseProgress(it / 100f, Modifier.fillMaxWidth().height(3.dp)) }
            }
        }
        SmallAction("Manage personal goals", onClick = manage)
    }
}
