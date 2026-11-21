package com.pulse.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.app.BuildConfig
import com.pulse.app.update.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal val LocalPulseMenu = staticCompositionLocalOf<(@Composable ((() -> Unit)?) -> Unit)?> { null }

@Composable
internal fun PulseOverflow(open: (String) -> Unit, repositoryVerified: Boolean, manageModes: (() -> Unit)? = null) {
    var expanded by remember { mutableStateOf(false) }
    val links = LocalUriHandler.current
    val actions = listOf(
        Triple("settings", "Settings", "settings"),
        Triple("history", "Check for updates", "updates"),
        Triple("book", "What's New", "whats-new"),
        Triple("heart", "Privacy Policy", "privacy"),
        Triple("book", "Terms of Service", "terms"),
        Triple("dots", "About PULSE", "about")
    )
    Box {
        IconAction("overflow", "More options") { expanded = !expanded }
        DropdownMenu(expanded, { expanded = false }, Modifier.heightIn(max = 385.dp),
            containerColor = MaterialTheme.colorScheme.surface) {
            if (manageModes != null) DropdownMenuItem(
                text = { Text("Manage Modes") }, leadingIcon = { Glyph("dots", color = MaterialTheme.colorScheme.primary) },
                onClick = { expanded = false; manageModes() }
            )
            actions.forEach { (icon, label, route) ->
                DropdownMenuItem(text = { Text(label) }, leadingIcon = { Glyph(icon, color = MaterialTheme.colorScheme.primary) },
                    onClick = { expanded = false; open(route) })
            }
            DropdownMenuItem(text = { Text(if (repositoryVerified) "GitHub repository" else "Maintainer on GitHub") },
                leadingIcon = { Glyph("next", color = MaterialTheme.colorScheme.primary) }, onClick = {
                    expanded = false
                    runCatching { links.openUri(if (repositoryVerified) PULSE_REPOSITORY else PULSE_MAINTAINER) }
                })
        }
    }
}

@Composable
internal fun PulseReadingPage(title: String, asset: String, back: () -> Unit, link: Pair<String, String>? = null) {
    val context = LocalContext.current
    val links = LocalUriHandler.current
    var document by remember(asset) { mutableStateOf<String?>(null) }
    LaunchedEffect(asset) {
        document = withContext(Dispatchers.IO) {
            runCatching { context.assets.open(asset).bufferedReader().use { it.readText() } }
                .getOrDefault("This document couldn't be opened.")
        }
    }
    ReleasePage(title, back) {
        if (document == null) CircularProgressIndicator(Modifier.size(28.dp))
        document?.let { contents ->
            if (asset.endsWith(".txt")) Text(contents, style = MaterialTheme.typography.bodyMedium)
            else PulseDocumentText(contents)
        }
        link?.let { (label, url) -> SmallAction(label) { runCatching { links.openUri(url) } } }
        if (asset == "NOTICE.md") {
            var showLicense by remember { mutableStateOf(false) }
            SmallAction(if (showLicense) "Hide Apache 2.0 license" else "Read Apache 2.0 license") { showLicense = !showLicense }
            if (showLicense) {
                val license = remember(context) {
                    runCatching { context.assets.open("licenses/Apache-2.0.txt").bufferedReader().use { it.readText() } }.getOrNull()
                }
                Text(license ?: "Apache 2.0 license text couldn't be opened.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun PulseDocumentText(text: String) {
    text.lineSequence().filter { it.isNotBlank() && !it.startsWith("# ") }.forEach { line ->
        when {
            line.startsWith("## ") -> Text(line.removePrefix("## "), Modifier.semantics { heading() }.padding(top = 8.dp),
                style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            line.startsWith("- ") -> Text("• " + line.removePrefix("- "), style = MaterialTheme.typography.bodyLarge)
            else -> Text(line, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
internal fun UpdateScreen(updates: PulseUpdates, back: () -> Unit) {
    val current by updates.state.collectAsStateWithLifecycle()
    val automatic by updates.automatic.collectAsStateWithLifecycle()
    val message by updates.installationMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val links = LocalUriHandler.current
    val installer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        updates.installerFinished(it.resultCode)
    }
    val playInstaller = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        updates.installerFinished(it.resultCode)
        updates.check()
    }
    ReleasePage("Updates", back) {
        Text("Installed version ${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}",
            style = MaterialTheme.typography.titleMedium)
        if (!updates.available) Text("Update checking is unavailable in this build.")
        else {
            PulseSwap(current, label = "Update status", distance = 10) { state ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (state) {
                        UpdateState.Idle -> PulseButton("Check for updates") { updates.check() }
                        UpdateState.Checking -> {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text("Checking for updates…")
                        }
                        UpdateState.Current -> {
                            Text("You're up to date.")
                            SmallAction("Check again") { updates.check() }
                        }
                        is UpdateState.Available -> {
                            PulseCard {
                                Label("UPDATE AVAILABLE", color = MaterialTheme.colorScheme.primary)
                                Text(state.release.version, style = MaterialTheme.typography.headlineSmall)
                                if (state.release.notes.isNotBlank()) {
                                    PulseDocumentText(state.release.notes)
                                }
                                PulseButton(updates.downloadLabel) { updates.prepare(state.release, playInstaller) }
                                SmallAction("Later") { updates.dismiss(state.release); back() }
                            }
                            state.release.page?.let { url -> SmallAction("View release on GitHub") { runCatching { links.openUri(url) } } }
                        }
                        is UpdateState.Downloading -> {
                            Text(if (state.percent == 100) "Verifying the APK…" else "Downloading ${state.percent}%")
                            val progress by androidx.compose.animation.core.animateFloatAsState(
                                state.percent / 100f, PulseMotion.tween(LocalAnimations.current), label = "Download progress"
                            )
                            LinearProgressIndicator({ progress }, Modifier.fillMaxWidth())
                            SmallAction("Cancel download", onClick = updates::cancelDownload)
                        }
                        is UpdateState.Ready -> {
                            Text("Version ${state.release.version} is verified.")
                            Text("If Android asks, allow installs from PULSE in Settings, return here, and tap Install again.")
                            PulseButton("Install update") {
                                updates.install { intent ->
                                    if (intent.action == Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES) context.startActivity(intent)
                                    else installer.launch(intent)
                                }
                            }
                        }
                        is UpdateState.Problem -> {
                            Text(state.message, color = MaterialTheme.colorScheme.error)
                            state.retryDownload?.let { release ->
                                if (updates.github) PulseButton("Retry download") { updates.prepare(release, playInstaller) }
                            }
                            SmallAction("Check again") { updates.check() }
                        }
                    }
                    message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            if (updates.supportsAutomatic) {
                HorizontalDivider()
                SettingToggle("Check automatically", automatic, updates::setAutomatic)
                Text("Checks GitHub at most once a day when you open PULSE. Downloads require your approval.",
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ReleasePage(title: String, back: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val menu = LocalPulseMenu.current
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconAction("back", "Back", onClick = back)
            Spacer(Modifier.weight(1f))
            menu?.invoke(null)
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
            content()
        }
    }
}
