package com.pulse.app.ui

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.app.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.*
import com.pulse.app.BuildConfig
import com.pulse.app.PulseViewModel
import com.pulse.app.core.*
import com.pulse.app.data.ShareExport
import com.pulse.app.integration.HealthConnectRepository
import com.pulse.app.integration.googlehealth.GoogleHealthAvailability
import com.pulse.app.update.PULSE_MAINTAINER
import com.pulse.app.update.PULSE_REPOSITORY
import androidx.compose.ui.platform.LocalUriHandler
import androidx.health.connect.client.HealthConnectClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable fun PrivacyRationale(close:()->Unit) {
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        PulseReadingPage("Privacy Policy", "PRIVACY.md", close)
    }
}

@Composable fun SettingToggle(title: String,checked: Boolean,change:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=56.dp).toggleable(checked, role = Role.Switch, onValueChange = change),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
        Text(title,Modifier.weight(1f).padding(end=16.dp)); Switch(checked, onCheckedChange = null)
    }
}
internal val settingsCategories = listOf("Profile & Goals", "Devices & Data", "Appearance", "Notifications", "Privacy & Data", "About PULSE")

@Composable fun SettingsScreen(vm: PulseViewModel,p: Preferences,snapshot: HealthSnapshot,connect:(Set<String>)->Unit,
    initialCategory: String = "", openCategory: (Int) -> Unit = {}, openPage: (String) -> Unit = {}, back: () -> Unit) {
    val category = initialCategory
    var delete by rememberSaveable {mutableStateOf(false)}
    val context=LocalContext.current;val scope=rememberCoroutineScope();var exportError by remember {mutableStateOf<String?>(null)};var exporting by remember {mutableStateOf(false)}
    val links = LocalUriHandler.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Row(Modifier.reveal().fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
            IconAction("back", "Back", onClick = back)
            Spacer(Modifier.weight(1f))
            LocalPulseMenu.current?.invoke(null)
        }
        Box(Modifier.reveal(1)) { SectionTitle(category.ifEmpty {"Settings"}) }
        if(category.isEmpty()) {
            PulseCard(Modifier.reveal(2, scale = .98f).fillMaxWidth()){DotWordmark();Label(p.mode.displayName())}
            settingsCategories.forEachIndexed {i,title->
                Row(Modifier.reveal(i+3).fillMaxWidth().heightIn(min=48.dp).pulseClickable {openCategory(i)}.padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){Text(title,Modifier.weight(1f));Glyph("next")}

            }
            SmallAction("Check for updates") { openPage("updates") }
        }
        if (category.isNotEmpty()) Column(Modifier.reveal(2), verticalArrangement = Arrangement.spacedBy(14.dp)) { when(category){
            "Profile & Goals"->{DisplayNameEditor(p,vm::preferences);GoalEditor(p,vm::preferences,snapshot);SettingToggle("Use miles for distance",p.imperial){v->vm.preferences{it.copy(imperial=v)}};Text("Goals are preferences. Historical measurements and fixed monthly medal thresholds stay independent of them.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            "Devices & Data"->{
                var selected by rememberSaveable { mutableStateOf(p.mode) }
                val checkedHealth by vm.healthSetup.collectAsStateWithLifecycle()
                val combined = GoogleHealthAvailability.enabled && p.mode == DataMode.GOOGLE_HEALTH && p.combineHealthConnect
                val health = if (combined) snapshot.secondary ?: checkedHealth else checkedHealth
                Text(when {
                    p.mode == DataMode.NONE -> "No health source connected"
                    !GoogleHealthAvailability.canConnect(p.mode) -> "Google Health · sync paused"
                    else -> "Active source · ${p.mode.displayName()}"
                })
                SourceChoices(selected) { selected = it }
                PulseSwap(selected, label = "Provider setup", distance = 12) { shown ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { when (shown) {
                    DataMode.HEALTH_CONNECT -> {
                        HealthConnectSetup(vm.healthConnect.availability(), health, { connect(HealthConnectRepository.permissions) },
                            verified = (p.mode == DataMode.HEALTH_CONNECT || combined) && health.access.verified)
                        if ((p.mode == DataMode.HEALTH_CONNECT || combined) && health.access.verified) {
                            val extra = vm.healthConnect.additionalPermissions() - health.granted
                            if (extra.isNotEmpty()) {
                                Label("Optional access covers active calories, floors, hydration, more history, or background updates when supported.")
                                SmallAction("Grant optional access") { connect(extra) }
                            }
                        }
                        if (combined) {
                            SmallAction("Stop combining Health Connect") { vm.stopCombiningHealthConnect() }
                        }
                    }
                    DataMode.GARMIN -> GarminSetup(vm)
                    DataMode.GOOGLE_HEALTH -> if (GoogleHealthAvailability.enabled) GoogleHealthSetup(vm, p)
                    DataMode.NONE -> Unit
                } } }
                if (selected != DataMode.NONE && selected != DataMode.GOOGLE_HEALTH && selected == p.mode) {
                    snapshot.synced?.let { MetaText("Last sync ${it.atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"))}") }
                    SmallAction("Refresh data", !snapshot.loading) { vm.refresh() }
                    SmallAction("Disconnect active source", !snapshot.loading) {
                        vm.preferences { it.copy(mode = DataMode.NONE, progressNotifications = false) }
                    }
                }
            }
            "Appearance"->{Label("COLOR THEME");listOf("DARK" to "Dark","LIGHT" to "Light","SYSTEM" to "Follow system").forEach{(key,title)->Row(Modifier.fillMaxWidth().heightIn(min=56.dp).clickable{vm.preferences{it.copy(theme=key)}},verticalAlignment=Alignment.CenterVertically){RadioButton(p.theme==key,{vm.preferences{it.copy(theme=key)}});Text(title)}};SettingToggle("Animations",p.animations){v->vm.preferences{it.copy(animations=v)}};SettingToggle("Haptic feedback",p.haptics){v->vm.preferences{it.copy(haptics=v)}};Text("System animation settings take precedence. Charts always include readable numeric summaries.",style=MaterialTheme.typography.bodySmall)}
            "Notifications"->{
                LiveGoalTrackingSettings(p, snapshot, vm::preferences, vm::refreshGoalNotification)
                HorizontalDivider()
                NotificationSetup(p,snapshot,vm::preferences)
            }
            "Privacy & Data"->{
                PulseCard {
                    Label("Private by design", color = Red)
                    Text("Provider access is read-only. Google Health records and Garmin credentials use Android Keystore-backed encryption. PULSE processes health records on your device.")
                }
                SmallAction("Privacy Policy") { openPage("privacy") }
                SmallAction("Terms of Service") { openPage("terms") }
                SmallAction("Connected providers") { openCategory(settingsCategories.indexOf("Devices & Data")) }
                if (vm.healthConnect.availability() == HealthConnectClient.SDK_AVAILABLE) {
                    SmallAction("Manage Health Connect permissions") {
                        runCatching { context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)) }
                            .onFailure { exportError = "Open Health Connect in Android Settings to manage access." }
                    }
                }
                Text("Exports contain your current dataset, cached real records, goals and dashboard. The file may contain personal health information. Credentials are excluded.",style=MaterialTheme.typography.bodySmall)
                Text("The JSON export covers PULSE settings, Health Connect and Garmin records. Google Health keeps its own export controls.", style = MaterialTheme.typography.bodySmall)
                PulseButton(if(exporting)"Preparing export…"else"Export local data",enabled=!exporting){scope.launch{exporting=true;try{val modes=vm.modes.value;ShareExport.data(context,p,modes.active.widgets,snapshot,vm.cachedRecords(),vm.cachedGarminRecords(),modes)}catch(e:CancellationException){throw e}catch(e:Exception){exportError="Could not create the export. Please retry."}finally{exporting=false}}}
                PulseSwap(exportError, label = "Export result") { it?.let { Text(it, color = Red) } }
                OutlinedButton({delete=true},shape=RoundedCornerShape(8.dp)){Text("Delete local PULSE data",color=Red)}
                Text("Deleting local data resets preferences and onboarding. It does not delete original records held by Health Connect, Garmin or another provider. Revoke access through Android settings to prevent importing records again.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                SmallAction("Open Android app settings"){context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,"package:${context.packageName}".toUri()))}
                SmallAction("Third-party licenses") { openPage("licenses") }
            }
            "About PULSE" -> {
                Image(painterResource(R.drawable.pulse_logo), contentDescription = "PULSE logo",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 132.dp), contentScale = ContentScale.Fit)
                Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.titleMedium)
                Text("PULSE brings your health data together in one place. Track your activity, understand your progress, set personal goals, and celebrate your achievements — all in a dashboard designed around you.")
                Label("PULSE is for general wellness and does not provide medical advice, diagnosis, or treatment.")
                SmallAction("Check for updates") { openPage("updates") }
                SmallAction("What's New") { openPage("whats-new") }
                SmallAction(if (BuildConfig.GITHUB_REPOSITORY_VERIFIED) "GitHub repository" else "Maintainer on GitHub") {
                    runCatching { links.openUri(if (BuildConfig.GITHUB_REPOSITORY_VERIFIED) PULSE_REPOSITORY else PULSE_MAINTAINER) }
                }
                SmallAction("Privacy Policy") { openPage("privacy") }
                SmallAction("Terms of Service") { openPage("terms") }
                SmallAction("Third-party licenses") { openPage("licenses") }
            }
        } }
        Spacer(Modifier.height(20.dp))
    }
    if (delete) PulseConfirmDialog(
        onDismiss = { delete = false }, title = { Text("Delete local PULSE data?") },
        text = { Text("Cached measurements, saved medals, the Google Health connection, the encrypted Garmin key, preferences and your dashboard will be removed. Original provider records will remain.") },
        confirmLabel = "Delete local data", onConfirm = { delete = false; vm.deleteLocal() }
    )
}
