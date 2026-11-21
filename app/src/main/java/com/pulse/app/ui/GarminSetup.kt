package com.pulse.app.ui

import android.view.View
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.app.PulseViewModel
import com.pulse.app.core.*

@Composable
fun GarminSetup(vm: PulseViewModel, onboarding: Boolean = false, advance: (() -> Unit)? = null) {
    val operationError by vm.keyError.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val p by vm.preferences.collectAsStateWithLifecycle()
    val configured by vm.garminConfigured.collectAsStateWithLifecycle()
    val busy by vm.keyBusy.collectAsStateWithLifecycle()
    var replacing by rememberSaveable { mutableStateOf(false) }
    var guide by rememberSaveable { mutableStateOf(false) }
    var remove by rememberSaveable { mutableStateOf(false) }
    var raw by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val verified = snapshot.mode == DataMode.GARMIN && snapshot.access.verified && p.mode == DataMode.GARMIN
    val editing = (!configured || replacing) && (!verified || !onboarding)
    val secure = editing || busy
    LaunchedEffect(busy, verified, operationError) {
        if (!busy && verified && operationError == null) replacing = false
    }
    val window = LocalActivity.current?.window
    val view = LocalView.current
    DisposableEffect(window, view, secure) {
        val alreadySecure = (window?.attributes?.flags ?: 0) and WindowManager.LayoutParams.FLAG_SECURE != 0
        val priorAutofill = view.importantForAutofill
        if (secure) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            view.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        onDispose {
            if (secure && !alreadySecure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            if (secure) view.importantForAutofill = priorAutofill
        }
    }
    if (onboarding) SectionTitle("Connect Garmin", "Use your personal Garmin API key to securely access your Garmin Connect data.")
    else Text("Garmin Personal API", style = MaterialTheme.typography.titleMedium)
    if (busy) SetupFeedback(SetupStatus.IN_PROGRESS, "Verifying your key", "PULSE is checking access with Garmin.")
    else if (verified) SetupFeedback(SetupStatus.COMPLETE, "Garmin connected",
        if (snapshot.synced == null) "API key verified. PULSE is ready to import your records."
        else if (snapshot.access == ProviderAccess.PARTIAL) "Connected. Some Garmin measurements could not be refreshed."
        else if (snapshot.days.any { day -> day.readings.any { (metric, reading) ->
            reading.state == DataState.READY && reading.value != null &&
                (metric !in setOf(Metric.ACTIVITIES, Metric.WORKOUT_DURATION) || reading.value > 0)
        } }) "API key verified. Your available records are ready."
        else "API key verified. No Garmin measurements have been imported yet.")
    if (editing) {
        OutlinedTextField(
            value = raw,
            onValueChange = { raw = it.take(4096) },
            label = { Text("Personal API key") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy, singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            trailingIcon = { TextButton({ visible = !visible }, enabled = !busy) { Text(if (visible) "Hide key" else "Show key") } }
        )
        PulseButton(if (busy) "Connecting…" else "Connect", Modifier.fillMaxWidth(), !busy) {
            focus.clearFocus()
            keyboard?.hide()
            vm.saveGarminKey(raw)
            raw = ""
            visible = false
        }
    } else if (verified && advance != null) {
        PulseButton("Continue", Modifier.fillMaxWidth(), !busy, advance)
    } else if (!verified && configured) {
        PulseButton("Connect with saved key", Modifier.fillMaxWidth(), !busy, vm::useSavedGarminKey)
    }
    operationError?.let { SetupFeedback(SetupStatus.NEEDS_ATTENTION, "Garmin setup needs attention", it) }
    if (operationError == null && snapshot.mode == DataMode.GARMIN && !snapshot.access.verified) {
        snapshot.error?.let { SetupFeedback(SetupStatus.NEEDS_ATTENTION, "Garmin setup needs attention", it) }
    }
    if (configured && !busy && (!verified || !onboarding)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallAction(if (replacing) "Cancel change" else "Change key") {
                replacing = !replacing
                raw = ""
                visible = false
            }
            if (!onboarding) SmallAction("Remove key") { remove = true }
        }
    }
    if (!verified || replacing) {
        SmallAction(if (guide) "Hide setup guide" else "How do I get a key?") { guide = !guide }
        if (guide) PulseCard(Modifier.fillMaxWidth()) {
            Text("Create a personal key", style = MaterialTheme.typography.titleMedium)
            listOf(
                "Sign in to Garmin Connect in your browser and open Personal API Key settings.",
                "Choose Create Key, accept the terms, and choose an expiration period.",
                "Create the key, copy it, and paste it here. Garmin displays it once."
            ).forEachIndexed { i, line -> Text("${i + 1}. $line", style = MaterialTheme.typography.bodySmall) }
            Label("Use your own key for personal access to your Garmin data.")
            SmallAction("Open Garmin’s official guide") {
                runCatching { uri.openUri("https://developer.garmin.com/gc-developer-program/personal/personal-api/") }
            }
        }
    }
    if (remove) AlertDialog(onDismissRequest = { remove = false }, title = { Text("Remove Garmin key?") },
        containerColor = MaterialTheme.colorScheme.surface,
        text = { Text("PULSE will remove the saved key and local Garmin measurements.") },
        confirmButton = { TextButton({ remove = false; replacing = false; vm.removeGarminKey() }) { Text("Remove key") } },
        dismissButton = { TextButton({ remove = false }) { Text("Cancel") } })
}
