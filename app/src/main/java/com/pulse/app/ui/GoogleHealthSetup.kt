package com.pulse.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.app.PulseViewModel
import com.pulse.app.core.DataMode
import com.pulse.app.core.Preferences
import com.pulse.app.integration.HealthConnectRepository
import com.pulse.app.integration.ProviderPriority
import com.pulse.app.integration.googlehealth.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun GoogleHealthSetup(vm: PulseViewModel, preferences: Preferences, advance: (() -> Unit)? = null) {
    val state by vm.googleSetup.collectAsStateWithLifecycle()
    val granted = state.connection?.scopes.orEmpty()
    var selected by rememberSaveable { mutableStateOf(listOf(GoogleHealthCategory.ACTIVITY.name)) }
    var disconnect by rememberSaveable { mutableStateOf(false) }
    var showPriority by rememberSaveable { mutableStateOf(false) }
    val busy = state.status in setOf(GoogleHealthStatus.AUTHORIZING, GoogleHealthStatus.VERIFYING,
        GoogleHealthStatus.FIRST_SYNC, GoogleHealthStatus.SYNCING)
    val requested = GoogleHealthCategory.entries.filter { it.name in selected }.map { it.scope }.toSet()
    LaunchedEffect(Unit) { vm.showGoogleHealth() }

    PulseCard(Modifier.fillMaxWidth()) {
        Label("Google Health", color = Red)
        PulseSwap(state.status, label = "Google Health status") { status ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!busy && state.initialSyncFinished && state.problem == null) {
                    Glyph("check", Modifier.reveal(scale = .65f).size(18.dp), Red)
                }
                Text(status.title, style = MaterialTheme.typography.titleMedium)
            }
        }
        PulseVisibility(busy) { SyncLogo(running = busy) }
        if (state.status == GoogleHealthStatus.DISCONNECTED) {
            Text("Choose the data PULSE can read from Google Health. PULSE uses it on this device for your dashboard, history and progress.")
            MetaText("Set up your Google Health profile with the Google account you plan to connect.")
        }
        state.connection?.let { connection ->
            Text(connection.account)
            connection.synced?.let {
                MetaText("Last successful sync · ${it.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"))}")
            }
        }
        PulseSwap(state.problem?.description, label = "Google Health connection message") { message ->
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        if (state.status == GoogleHealthStatus.NO_DATA) {
            Text("Google Health returned no measurements for the dates checked. Older dates will import in the background.")
        }
    }
    PulseCard(Modifier.fillMaxWidth()) {
        Label("Read permissions")
        GoogleHealthCategory.entries.forEach { category ->
            val allowed = category.scope in granted && state.status !in setOf(GoogleHealthStatus.REAUTHORIZE, GoogleHealthStatus.UNLINKED)
            if (allowed) {
                Label("${category.title} · Granted")
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(category.name in selected, onCheckedChange = { checked ->
                        selected = if (checked) selected + category.name else selected - category.name
                    }, enabled = !busy)
                    Text(category.title, Modifier.weight(1f))
                }
            }
            MetaText(category.description)
        }
    }
    val ready = state.connection != null
    val extra = requested - granted
    if (!ready) {
        if (GoogleHealthCategory.ACTIVITY.scope in requested && requested.size > 1) {
            MetaText("Connect with Activity & fitness first. You can grant the other selected permissions once connected.")
        }
        PulseButton(if (busy) state.status.title else "Connect with Google", Modifier.fillMaxWidth(), !busy && requested.isNotEmpty()) {
            val activityScope = GoogleHealthCategory.ACTIVITY.scope
            vm.beginGoogleAuthorization(if (activityScope in requested) setOf(activityScope) else requested, false)
        }
    } else {
        if (extra.isNotEmpty()) {
            PulseButton("Grant selected permissions", Modifier.fillMaxWidth(), !busy) {
                vm.beginGoogleAuthorization(granted + extra, false)
            }
        }
        if (state.status !in setOf(GoogleHealthStatus.REAUTHORIZE, GoogleHealthStatus.UNLINKED) &&
            (advance == null || !state.initialSyncFinished)) SmallAction("Sync now", !busy) { vm.syncGoogleNow() }
        SmallAction("Reconnect", !busy) { vm.beginGoogleAuthorization(granted.ifEmpty { requested }, false) }
        if (advance == null) {
            SmallAction("Switch account", !busy) {
                val activityScope = GoogleHealthCategory.ACTIVITY.scope
                val scopes = requested.ifEmpty { granted }
                vm.beginGoogleAuthorization(if (activityScope in scopes) setOf(activityScope) else scopes, true)
            }
            SmallAction("Disconnect Google Health", !busy) { disconnect = true }
        }
    }
    if (advance != null && state.initialSyncFinished && preferences.mode == DataMode.GOOGLE_HEALTH) {
        PulseButton("Continue", Modifier.fillMaxWidth(), !busy, advance)
    }
    if (advance == null && preferences.mode == DataMode.GOOGLE_HEALTH) {
        if (preferences.combineHealthConnect) {
            SmallAction(if (showPriority) "Hide provider priority" else "Provider priority") { showPriority = !showPriority }
            if (showPriority) PulseCard(Modifier.fillMaxWidth()) {
                Text("Choose a preferred provider when both can supply the same metric. PULSE uses one provider per metric per day.")
                HealthConnectRepository.supported.intersect(GoogleHealthMetrics.metrics).map(ProviderPriority::key).distinct()
                    .sortedBy { it.label }.forEach { metric ->
                        Label(metric.label)
                        var expanded by remember { mutableStateOf(false) }
                        val current = preferences.providerPriority[metric]
                        Box {
                            SmallAction(current?.displayName() ?: "Automatic (Google Health first)") { expanded = true }
                            DropdownMenu(expanded, { expanded = false }) {
                                listOf(null, DataMode.GOOGLE_HEALTH, DataMode.HEALTH_CONNECT).forEach { option ->
                                    DropdownMenuItem(text = { Text(option?.displayName() ?: "Automatic") }, onClick = {
                                        expanded = false
                                        vm.googlePriority(metric, option)
                                    })
                                }
                            }
                        }
                    }
            }
        } else MetaText("To use both sources, connect Health Connect from Devices & Data.")
    }
    if (disconnect) {
        AlertDialog(onDismissRequest = { disconnect = false }, title = { Text("Disconnect Google Health?") },
            text = { Text("PULSE will stop syncing. You can keep this account’s encrypted cache or remove its imported records and measurement-based badges. Your goals and earned challenge rewards stay in PULSE.") },
            confirmButton = { TextButton({ disconnect = false; vm.disconnectGoogle(true) }) { Text("Disconnect and remove", color = Red) } },
            dismissButton = {
                Column {
                    TextButton({ disconnect = false; vm.disconnectGoogle(false) }) { Text("Disconnect and keep") }
                    TextButton({ disconnect = false }) { Text("Cancel") }
                }
            })
    }
}
