package com.pulse.app.ui

import android.content.Intent
import androidx.core.net.toUri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.app.PulseViewModel
import com.pulse.app.core.*
import com.pulse.app.integration.googlehealth.GoogleHealthStatus
import com.pulse.app.integration.googlehealth.GoogleHealthAvailability

@Composable
fun Onboarding(p: Preferences, snapshot: HealthSnapshot, vm: PulseViewModel, connect: () -> Unit) {
    val step = SetupStep.entries[p.onboardingStep]
    val health by vm.healthSetup.collectAsStateWithLifecycle()
    val keyBusy by vm.keyBusy.collectAsStateWithLifecycle()
    val google by vm.googleSetup.collectAsStateWithLifecycle()
    val error by vm.message.collectAsStateWithLifecycle()
    val busy = step == SetupStep.CONNECT && (keyBusy || health.loading && p.setupSource == DataMode.HEALTH_CONNECT ||
        p.setupSource == DataMode.GOOGLE_HEALTH && google.status in setOf(GoogleHealthStatus.AUTHORIZING,
            GoogleHealthStatus.VERIFYING, GoogleHealthStatus.FIRST_SYNC, GoogleHealthStatus.SYNCING))
    fun back() = vm.preferences { it.copy(onboardingStep = step.ordinal - 1) }
    fun next(change: (Preferences) -> Preferences = { it }) =
        vm.preferences { change(it).copy(onboardingStep = step.ordinal + 1) }
    BackHandler(step != SetupStep.WELCOME) { if (!busy) back() }
    val motion = LocalAnimations.current
    val stepState = rememberSaveableStateHolder()
    Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 540.dp).fillMaxSize().padding(horizontal = 22.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                if (step != SetupStep.WELCOME) IconAction("back", "Back", enabled = !busy) { back() }
                Spacer(Modifier.weight(1f))
                Text("${step.ordinal + 1} / ${SetupStep.entries.size}", style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics { contentDescription = "Step ${step.ordinal + 1} of ${SetupStep.entries.size}" })
            }
            val progress = animateFloatAsState((step.ordinal + 1).toFloat(), PulseMotion.settle(motion), label = "Onboarding progress")
            val accent = MaterialTheme.colorScheme.primary
            val track = MaterialTheme.colorScheme.outline
            Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                SetupStep.entries.forEach { item ->
                    Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).drawBehind {
                        drawRect(track)
                        drawRect(accent, size = Size(size.width * (progress.value - item.ordinal).coerceIn(0f, 1f), size.height))
                    })
                }
            }
            PulseSwap(step, Modifier.weight(1f), label = "Onboarding step", distance = 18,
                direction = { first, second -> second.ordinal.compareTo(first.ordinal) }) { shown ->
                stepState.SaveableStateProvider(shown.name) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (shown) {
                        SetupStep.WELCOME -> {
                            var played by rememberSaveable { mutableStateOf(false) }
                            val animate = remember { motion && !played }
                            BrandLogo(Modifier.fillMaxWidth().height(84.dp), animate) { played = true }
                            SectionTitle("Meet PULSE.", "Your health, all in one place.")
                            Text("Bring your health and activity data together in a dashboard designed around you.")
                            PulseCard(Modifier.fillMaxWidth()) {
                                listOf("heart" to "Track your health", "target" to "Make progress toward your goals",
                                    "medal" to "Celebrate your achievements").forEach { (icon, title) ->
                                    Row(Modifier.padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Glyph(icon, color = MaterialTheme.colorScheme.primary)
                                        Text(title)
                                    }
                                }
                            }
                            PulseButton("Get started", Modifier.fillMaxWidth()) { next() }
                        }
                        SetupStep.SOURCE -> {
                            SectionTitle("Connect your health data", "Choose where PULSE should get your health and activity data.")
                            SourceChoices(p.setupSource) { source ->
                                vm.preferences { it.copy(setupSource = source,
                                    sourceSetup = if (it.setupSource == source) it.sourceSetup else SetupStatus.NOT_STARTED) }
                            }
                            when (p.setupSource) {
                                DataMode.HEALTH_CONNECT -> Label("Next, choose which health records PULSE can read.")
                                DataMode.GARMIN -> Label("Next, paste your own Garmin key. Garmin Personal API is for personal use.")
                                DataMode.GOOGLE_HEALTH -> if (GoogleHealthAvailability.enabled) {
                                    Label("Next, choose which Google Health categories to share.")
                                }
                                DataMode.NONE -> Unit
                            }
                            PulseButton("Continue", Modifier.fillMaxWidth(),
                                p.setupSource != DataMode.NONE && GoogleHealthAvailability.canConnect(p.setupSource)) { next() }
                        }
                        SetupStep.CONNECT -> when (p.setupSource) {
                            DataMode.HEALTH_CONNECT -> HealthConnectSetup(vm.healthConnect.availability(), health, connect,
                                verified = p.mode == DataMode.HEALTH_CONNECT && health.access.verified,
                                advance = { next { it.copy(sourceSetup = SetupStatus.COMPLETE) } })
                            DataMode.GARMIN -> {
                                GarminSetup(vm, onboarding = true, advance = { next { it.copy(sourceSetup = SetupStatus.COMPLETE) } })
                                if (p.mode != DataMode.GARMIN || !snapshot.access.verified) {
                                    SmallAction("Skip for now", !busy) {
                                        next { it.copy(mode = DataMode.NONE, sourceSetup = SetupStatus.SKIPPED, progressNotifications = false) }
                                    }
                                }
                            }
                            DataMode.GOOGLE_HEALTH -> {
                                if (GoogleHealthAvailability.enabled) {
                                    GoogleHealthSetup(vm, p, advance = { next { it.copy(sourceSetup = SetupStatus.COMPLETE) } })
                                } else {
                                    GoogleHealthComingSoon(p.mode == DataMode.GOOGLE_HEALTH)
                                    PulseButton("Choose another source", onClick = ::back)
                                }
                                if (!google.initialSyncFinished) SmallAction("Skip for now", !busy) {
                                    next { it.copy(mode = DataMode.NONE, sourceSetup = SetupStatus.SKIPPED, progressNotifications = false) }
                                }
                            }
                            DataMode.NONE -> PulseButton("Choose a source") { back() }
                        }
                        SetupStep.GOALS -> {
                            SectionTitle("Set your goals", "Choose what you want to work toward. You can change these anytime.")
                            GoalEditor(p, vm::preferences, snapshot, advance = { next() })
                        }
                        SetupStep.NOTIFICATIONS -> {
                            SectionTitle("Stay up to date", "See your daily progress without opening PULSE.")
                            NotificationSetup(p, snapshot, vm::preferences, advance = { next() })
                            if (!p.progressNotifications || !com.pulse.app.integration.ProgressNotifications.allowed(LocalContext.current) ||
                                com.pulse.app.integration.ProgressNotifications.supported(p, snapshot).none { it in p.notificationMetrics }) SmallAction("Not now") {
                                next { it.copy(progressNotifications = false, notificationSetup = SetupStatus.SKIPPED) }
                            }
                        }
                        SetupStep.FINISH -> {
                            var revealed by rememberSaveable { mutableStateOf(!motion) }
                            val animate = remember { motion && !revealed }
                            BrandLogo(Modifier.fillMaxWidth().height(80.dp), animate) { revealed = true }
                            PulseVisibility(revealed || !motion) { SuccessMark() }
                            Box(Modifier.reveal(1)) { SectionTitle("You're all set.", "Your PULSE dashboard is ready. Explore your health, track your goals, and make every day count.") }
                            PulseVisibility(revealed || !motion) {
                                PulseCard(Modifier.reveal(2).fillMaxWidth()) {
                                    val source = when {
                                        p.sourceSetup == SetupStatus.SKIPPED -> "Skipped"
                                        snapshot.mode != p.mode || snapshot.loading -> "Checking connection"
                                        snapshot.access == ProviderAccess.PARTIAL -> "Partial access"
                                        snapshot.access == ProviderAccess.CONNECTED -> "Connected"
                                        else -> "Needs attention"
                                    }
                                    Text("Health data · $source")
                                    Text("Goals · ${if (p.goalsSetup == SetupStatus.COMPLETE && Goals.active(p).isNotEmpty()) "Ready" else "None selected"}")
                                    val notificationsAllowed = com.pulse.app.integration.ProgressNotifications.allowed(LocalContext.current)
                                    Text("Notifications · ${if (p.progressNotifications && notificationsAllowed) "Enabled" else if (p.notificationSetup == SetupStatus.SKIPPED) "Skipped" else "Disabled"}")
                                }
                            }
                            PulseButton("Go to PULSE", Modifier.reveal(3).fillMaxWidth(), onClick = vm::finishOnboarding)
                        }
                    }
                    PulseVisibility(error != null) { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error) }
                }
                }
            }
        }
    }
}

@Composable
internal fun SourceChoices(selected: DataMode, select: (DataMode) -> Unit) {
    val motion = LocalAnimations.current
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Triple(DataMode.HEALTH_CONNECT, "Health Connect", "Connect health data already available on your Android device."),
            Triple(DataMode.GOOGLE_HEALTH, "Google Health", "Connect your Fitbit, Pixel Watch, or other Google Health records."),
            Triple(DataMode.GARMIN, "Garmin Personal API", "Connect your Garmin account using your own personal key.")
        ).filter { GoogleHealthAvailability.canConnect(it.first) }.forEachIndexed { index, (mode, title, description) ->
            val checked = selected == mode
            val source = remember { MutableInteractionSource() }
            val border by animateColorAsState(if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                PulseMotion.tween(motion, PulseMotion.Selection), label = "Provider selection outline")
            val surface by animateColorAsState(if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                PulseMotion.tween(motion, PulseMotion.Selection), label = "Provider selection fill")
            Surface(
                Modifier.reveal(1 + index).fillMaxWidth().pulsePress(source).clip(RoundedCornerShape(PulseTokens.corner))
                    .selectable(checked, role = Role.RadioButton, interactionSource = source) { select(mode) },
                shape = RoundedCornerShape(PulseTokens.corner),
                border = BorderStroke(1.dp, border), color = surface
            ) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Glyph(if (mode == DataMode.HEALTH_CONNECT) "heart" else "stopwatch",
                        color = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Text(description, style = MaterialTheme.typography.bodySmall)
                    }
                    RadioButton(checked, onClick = null)
                }
            }
        }
    }
    if (!GoogleHealthAvailability.enabled) GoogleHealthComingSoon(selected == DataMode.GOOGLE_HEALTH)
}

@Composable
internal fun GoogleHealthComingSoon(previouslyActive: Boolean) {
    PulseCard(Modifier.fillMaxWidth()) {
        Text("Google Health", style = MaterialTheme.typography.titleMedium)
        Label(GoogleHealthAvailability.comingSoon)
        if (previouslyActive) Text(GoogleHealthAvailability.paused, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun SetupFeedback(status: SetupStatus, title: String, detail: String) {
    PulseCard(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        PulseSwap(status to title, label = "Provider status") { (shown, heading) ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (shown == SetupStatus.COMPLETE) Glyph("check", Modifier.reveal(scale = .68f).size(18.dp), color = Red)
                Text(heading, style = MaterialTheme.typography.titleMedium)
            }
        }
        PulseVisibility(status == SetupStatus.IN_PROGRESS) { SyncLogo(running = status == SetupStatus.IN_PROGRESS) }
        PulseSwap(detail, label = "Provider status detail") { Label(it) }
    }
}

@Composable
internal fun HealthConnectSetup(availability: Int, snapshot: HealthSnapshot, grant: () -> Unit, verified: Boolean,
    advance: (() -> Unit)? = null) {
    val context = LocalContext.current
    var pending by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(snapshot) { if (snapshot.loading || snapshot.access != ProviderAccess.DISCONNECTED) pending = false }
    SectionTitle("Connect Health Connect", "Give PULSE permission to read supported data for your dashboard, history, and goals.")
    Text("What you see depends on the records available on your device, such as steps, heart rate, sleep, and workouts.")
    val access = snapshot.access
    when {
        snapshot.loading || pending -> SetupFeedback(SetupStatus.IN_PROGRESS, "Checking access", "This may take a moment.")
        availability != HealthConnectClient.SDK_AVAILABLE -> SetupFeedback(SetupStatus.NEEDS_ATTENTION, "Health Connect unavailable",
            "Health Connect needs a compatible Android device and the latest available version.")
        verified -> SetupFeedback(SetupStatus.COMPLETE,
            if (access == ProviderAccess.PARTIAL) "Partial access" else "Connected",
            if (snapshot.days.any { day -> day.readings.any { (metric, reading) ->
                reading.state == DataState.READY && reading.value != null &&
                    (metric !in setOf(Metric.ACTIVITIES, Metric.WORKOUT_DURATION) || reading.value > 0)
            } }) "PULSE can read your available records." else "Access is verified. No health records have been imported yet.")
        access == ProviderAccess.DENIED -> SetupFeedback(SetupStatus.NEEDS_ATTENTION, "Access needed", "Choose at least one category in Health Connect and try again.")
        access == ProviderAccess.ERROR -> SetupFeedback(SetupStatus.NEEDS_ATTENTION, "Could not check access", snapshot.error.orEmpty())
    }
    when {
        availability == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
            PulseButton("Install or update Health Connect", Modifier.fillMaxWidth()) {
                val url = "https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
            }
        availability != HealthConnectClient.SDK_AVAILABLE -> Unit
        verified && advance != null -> PulseButton("Continue", Modifier.fillMaxWidth(), onClick = advance)
        else -> PulseButton(if (access == ProviderAccess.DENIED) "Try again" else if (verified) "Review access" else "Grant access",
            Modifier.fillMaxWidth(), !snapshot.loading && !pending) { pending = true; grant() }
    }
    if (availability == HealthConnectClient.SDK_AVAILABLE && (access == ProviderAccess.DENIED || verified && advance == null)) {
        SmallAction("Open Health Connect settings") {
            runCatching { context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)) }
        }
    }
}

@Composable
fun DisplayNameEditor(p: Preferences, update: ((Preferences) -> Preferences) -> Unit) {
    var name by rememberSaveable { mutableStateOf(p.displayName) }
    OutlinedTextField(name, { name = it.take(40) }, label = { Text("Display name (optional)") },
        singleLine = true, modifier = Modifier.fillMaxWidth())
    Row {
        SmallAction("Save name", name.trim() != p.displayName) { update { it.copy(displayName = name) } }
        if (p.displayName.isNotBlank() || name.isNotBlank()) SmallAction("Delete name") {
            name = ""
            update { it.copy(displayName = "") }
        }
    }
}
