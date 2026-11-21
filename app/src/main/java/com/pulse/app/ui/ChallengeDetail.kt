package com.pulse.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.app.PulseViewModel
import com.pulse.app.core.*
import java.time.LocalDate

@Composable
fun ChallengeDetailScreen(vm: PulseViewModel, id: String, back: () -> Unit) {
    val challenges by vm.challenges.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val challenge = challenges.firstOrNull { it.id == id }
    var edit by rememberSaveable(id) { mutableStateOf(false) }
    var archive by rememberSaveable(id) { mutableStateOf(false) }
    Scaffold(modifier = Modifier.imePadding(), topBar = { ChallengeTopBar("Challenge", back) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).clipToBounds(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (challenge == null) {
                item { Text("Challenge unavailable") }
                return@LazyColumn
            }
            item { ChallengeCard(challenge, vm = vm, modifier = Modifier.reveal(1)) }
            if (challenge.description.isNotBlank()) item { Text(challenge.description, Modifier.reveal(2)) }
            if (challenge.completed != null) item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Glyph("medal", Modifier.size(20.dp), MaterialTheme.colorScheme.primary)
                    Label("Badge earned · View it in Collection")
                }
            }
            if (challenge.status != ChallengeStatus.ARCHIVED) item {
                val canEdit = challenge.completed == null
                val canPause = canEdit && challenge.status != ChallengeStatus.EXPIRED
                val paused = challenge.status == ChallengeStatus.PAUSED
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (canEdit) ManageButton("Edit", "edit") { edit = true }
                    if (canPause) ManageButton(if (paused) "Resume" else "Pause", if (paused) "play" else "pause") {
                        vm.challengeStatus(id, if (paused) ChallengeStatus.ACTIVE else ChallengeStatus.PAUSED)
                    }
                    TextButton({ archive = true }, colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )) {
                        Glyph("archive", Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Archive")
                    }
                }
                if (canPause) {
                    Label(if (paused) "Progress updates are paused. Your end date stays the same."
                        else "Pausing stops progress updates. Your end date stays the same.")
                    if (paused && challenge.tracking.automatic) {
                        Label("When you resume, PULSE checks available measurements from the challenge dates.")
                    }
                }
            }
            item { Text("Progress history", Modifier.reveal(3), style = MaterialTheme.typography.titleLarge) }
            if (challenge.tracking.automatic) {
                val periods = ChallengeEvaluator.periods(challenge, snapshot, LocalDate.now(), preferences)
                val goals = challenge.tracking == ChallengeTracking.PERSONAL_GOALS
                item {
                    Label("${if (goals) "Personal goals" else challenge.measurement?.title ?: challenge.metric.label} · ${challenge.source.sourceName}")
                    if (goals) challenge.linkedGoals(preferences).forEach { goal ->
                        Label("${goal.metric.title} · ${goal.summary(preferences.imperial)}")
                    }
                    if (goals && challenge.linkedGoals(preferences).size < challenge.goalReferences.size) {
                        Label("A linked goal is missing or its period changed. Review it in Profile & Goals.")
                    }
                }
                if (periods.isEmpty()) item { Label("History appears after your challenge starts.") }
                items(periods, key = { it.date.toEpochDay() }) { period ->
                    val reading = when {
                        goals || challenge.measurement?.presence == true || challenge.condition != ChallengeCondition.AT_LEAST ->
                            when (period.met) { true -> "Met"; false -> "Not met"; null -> "Awaiting data" }
                        period.value != null -> "${formatChallengeAmount(period.value)} ${challenge.unit}"
                        else -> "Awaiting data"
                    }
                    HistoryEntry(period.date, reading)
                }
            } else {
                if (challenge.events.isEmpty()) item { Label("No entries yet") }
                items(challenge.events.asReversed(), key = { it.id }) { event ->
                    HistoryEntry(event.date, "+${formatChallengeAmount(event.amount)} ${challenge.unit}")
                }
            }
        }
    }

    if (edit && challenge != null) EditChallengeIdentity(challenge, { edit = false }) { name, description ->
        vm.editChallengeIdentity(id, name, description)
        edit = false
    }
    if (archive && challenge != null) ArchiveChallengeDialog(challenge.name, { archive = false }) {
        vm.challengeStatus(id, ChallengeStatus.ARCHIVED)
        archive = false
    }
}

@Composable
private fun HistoryEntry(date: LocalDate, reading: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Label(date.displayDate(), Modifier.weight(1f))
        Text(reading, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ManageButton(title: String, icon: String, action: () -> Unit) {
    OutlinedButton(
        onClick = action,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.heightIn(min = PulseTokens.touchTarget),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Glyph(icon, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(title)
    }
}

@Composable
internal fun ArchiveChallengeDialog(name: String, dismiss: () -> Unit, archive: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Archive challenge?") },
        text = { Text("This moves $name to Archived and stops progress updates. Its history and any earned badge stay in PULSE.") },
        dismissButton = { TextButton(dismiss) { Text("Cancel") } },
        confirmButton = { TextButton(archive) { Text("Archive", color = MaterialTheme.colorScheme.error) } },
        shape = RoundedCornerShape(PulseTokens.corner),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    )
}

@Composable
internal fun EditChallengeIdentity(challenge: PersonalChallenge, dismiss: () -> Unit, save: (String, String) -> Unit) {
    var name by rememberSaveable(challenge.id) { mutableStateOf(challenge.name) }
    var description by rememberSaveable(challenge.id) { mutableStateOf(challenge.description) }
    var attempts by rememberSaveable(challenge.id) { mutableIntStateOf(0) }
    val error = ChallengeIssue.NAME.message.takeIf { name.isBlank() }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Edit identity") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label("To change how progress is tracked, create a new challenge.")
                ChallengeField("Challenge name", name, { name = it }, error, attempts > 0, attempts)
                ChallengeField("Description (optional)", description, { description = it }, multiline = true, maxLength = 1000)
            }
        },
        dismissButton = { TextButton(dismiss) { Text("Cancel") } },
        confirmButton = {
            TextButton({ if (error == null) save(name.trim(), description.trim()) else attempts++ }) { Text("Save") }
        },
        shape = RoundedCornerShape(PulseTokens.corner),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    )
}
