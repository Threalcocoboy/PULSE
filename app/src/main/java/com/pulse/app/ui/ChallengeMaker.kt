package com.pulse.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.app.PulseViewModel
import com.pulse.app.core.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

private val challengeCategories = listOf(
    ChallengeOption("Personal", "Personal", icon = "book"),
    ChallengeOption("Steps", "Steps", icon = "foot"),
    ChallengeOption("Sleep", "Sleep", icon = "moon"),
    ChallengeOption("Workouts", "Workouts", icon = "dumbbell"),
    ChallengeOption("Activity", "Activity", icon = "stopwatch"),
    ChallengeOption("Heart", "Heart", icon = "heart"),
    ChallengeOption("Other", "Other", icon = "target"),
    ChallengeOption("Custom", "Custom category", icon = "edit")
)

private val challengeIcons = listOf(
    "moon" to "Moon", "foot" to "Footprints", "heart" to "Heart", "book" to "Book",
    "target" to "Target", "calendar" to "Calendar", "stopwatch" to "Stopwatch", "dumbbell" to "Dumbbell"
)
private val numericUnits = listOf("minutes", "pages", "km", "glasses", "times", "Custom")
private val durationPresets = listOf("7", "14", "30")

@Composable
fun ChallengeMaker(vm: PulseViewModel, back: () -> Unit) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val available = if (snapshot.mode == preferences.mode && snapshot.mode != DataMode.DEMO) {
        ChallengeEvaluator.metrics.intersect(snapshot.available)
    } else emptySet()
    ChallengeMakerForm(available, preferences.mode, back, vm::createChallenge)
}

@Composable
internal fun ChallengeMakerForm(
    available: Set<Metric>,
    source: DataMode,
    back: () -> Unit,
    create: suspend (PersonalChallenge) -> Boolean
) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Personal") }
    var customCategory by rememberSaveable { mutableStateOf("") }
    var icon by rememberSaveable { mutableStateOf("book") }
    var manualIcon by rememberSaveable { mutableStateOf(false) }
    var tracking by rememberSaveable { mutableStateOf(ChallengeTracking.CHECKBOX) }
    var metric by rememberSaveable { mutableStateOf(Metric.STEPS) }
    var rule by rememberSaveable { mutableStateOf(ChallengeRule.CUMULATIVE) }
    var target by rememberSaveable { mutableStateOf("5") }
    var unit by rememberSaveable { mutableStateOf("minutes") }
    var customUnit by rememberSaveable { mutableStateOf("") }
    var durationChoice by rememberSaveable { mutableStateOf("7") }
    var customDuration by rememberSaveable { mutableStateOf("") }
    var requiredDays by rememberSaveable { mutableStateOf("5") }
    val today = LocalDate.now()
    var startDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var dateOpen by rememberSaveable { mutableStateOf(false) }
    var identityOpen by rememberSaveable { mutableStateOf(true) }
    var trackingOpen by rememberSaveable { mutableStateOf(false) }
    var goalOpen by rememberSaveable { mutableStateOf(false) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    var requestedField by remember { mutableStateOf<ChallengeIssue?>(null) }
    var requestNumber by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    val id = rememberSaveable { UUID.randomUUID().toString() }
    val auto = tracking == ChallengeTracking.AUTOMATIC
    val checkbox = tracking == ChallengeTracking.CHECKBOX
    val needsWholeTarget = checkbox || (auto && metric == Metric.STEPS)
    val draft = PersonalChallenge(
        id = id, name = name.trim(), description = description.trim(),
        category = if (category == "Custom") customCategory.trim() else category, icon = icon,
        tracking = tracking, metric = metric, rule = rule,
        target = parseChallengeNumber(target) ?: Double.NaN,
        unit = when {
            auto -> metric.unit
            checkbox -> "times"
            unit == "Custom" -> customUnit.trim()
            else -> unit
        },
        duration = (if (durationChoice == "Custom") customDuration else durationChoice).toIntOrNull() ?: 0,
        requiredDays = requiredDays.toIntOrNull() ?: 0,
        start = LocalDate.ofEpochDay(startDay), source = source
    )
    val errors = draft.validationIssues().associateWith { it.message }.toMutableMap()
    if (auto && metric !in available) errors[ChallengeIssue.METRIC] = "Choose an available health metric."
    if (needsWholeTarget && draft.target.isFinite() && draft.target % 1.0 != 0.0) {
        errors[ChallengeIssue.TARGET] = "Enter a whole number greater than zero."
    }
    if (draft.start < today) errors[ChallengeIssue.START_DATE] = "Choose today or a future date."
    val firstError = ChallengeIssue.entries.firstOrNull { it in errors }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    fun request(issue: ChallengeIssue) {
        when (issue) {
            ChallengeIssue.NAME, ChallengeIssue.CATEGORY -> identityOpen = true
            ChallengeIssue.METRIC, ChallengeIssue.UNIT -> trackingOpen = true
            else -> goalOpen = true
        }
        requestedField = issue
        requestNumber++
    }
    fun focusFor(issue: ChallengeIssue) = if (requestedField == issue) requestNumber else 0

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = { ChallengeTopBar("New Challenge", back) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (saveFailed) ChallengeError("Could not save. Try again.")
                    val submit = {
                        attempted = true
                        saveFailed = false
                        if (firstError != null) {
                            request(firstError)
                        } else {
                            focus.clearFocus()
                            keyboard?.hide()
                            saving = true
                            scope.launch {
                                if (create(draft)) back() else saveFailed = true
                                saving = false
                            }
                        }
                        Unit
                    }
                    if (firstError != null) {
                        FilledTonalButton(
                            onClick = submit,
                            modifier = Modifier.fillMaxWidth().heightIn(min = PulseTokens.touchTarget)
                                .semantics { stateDescription = "Required fields incomplete" },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) { Text("Create Challenge") }
                    } else {
                        PulseButton(if (saving) "Saving…" else "Create Challenge", Modifier.fillMaxWidth(), !saving, submit)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).clipToBounds()
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MakerSection("edit", "Identity", name.ifBlank { "What do you want to achieve?" },
                identityOpen, { identityOpen = !identityOpen }) {
                ChallengeField("Challenge name", name, { name = it }, errors[ChallengeIssue.NAME],
                    attempted, focusFor(ChallengeIssue.NAME))
                ChallengeField("Description (optional)", description, { description = it }, multiline = true, maxLength = 1000)
                ChallengeChoice("Category", category, challengeCategories, change = {
                    category = it
                    if (!manualIcon) icon = challengeCategories.first { option -> option.value == it }.icon
                        ?.takeIf { suggested -> challengeIcons.any { option -> option.first == suggested } } ?: "target"
                })
                if (category == "Custom") {
                    ChallengeField("Custom category name", customCategory, { customCategory = it },
                        errors[ChallengeIssue.CATEGORY], attempted, focusFor(ChallengeIssue.CATEGORY), maxLength = 40)
                }
                IconPicker(icon) { icon = it; manualIcon = true }
            }
            MakerSection("history", "Tracking", tracking.title, trackingOpen, { trackingOpen = !trackingOpen }) {
                ChallengeChoice("Tracking method", tracking, listOf(
                    ChallengeOption(ChallengeTracking.AUTOMATIC, ChallengeTracking.AUTOMATIC.title,
                        "Use daily measurements from ${source.sourceName}.", "heart"),
                    ChallengeOption(ChallengeTracking.CHECKBOX, ChallengeTracking.CHECKBOX.title,
                        "Tap once for each completion.", "check"),
                    ChallengeOption(ChallengeTracking.NUMERIC, ChallengeTracking.NUMERIC.title,
                        "Enter an amount, such as pages read.", "edit")
                ), change = {
                    tracking = it
                    if (it == ChallengeTracking.AUTOMATIC && metric !in available) {
                        available.firstOrNull()?.let { supported -> metric = supported }
                    }
                })
                when (tracking) {
                    ChallengeTracking.CHECKBOX -> Label(
                        if (rule.countsDays) "Each tap adds one completion. A day counts once when you reach its target."
                        else "Each tap adds one completion. You can check off more than once a day."
                    )
                    ChallengeTracking.NUMERIC -> {
                        ChallengeChoice("Unit", unit, numericUnits.map { ChallengeOption(it, if (it == "km") "Kilometers" else it.replaceFirstChar(Char::titlecase)) },
                            change = { unit = it })
                        if (unit == "Custom") ChallengeField("Custom unit", customUnit, { customUnit = it },
                            errors[ChallengeIssue.UNIT], attempted, focusFor(ChallengeIssue.UNIT), maxLength = 24)
                    }
                    ChallengeTracking.AUTOMATIC -> ScrollToError(focusFor(ChallengeIssue.METRIC)) {
                        if (available.isEmpty()) {
                            val guidance = "Connect a health source in Settings and grant access to steps, distance or sleep."
                            if (attempted) ChallengeError(guidance) else Label(guidance)
                        } else {
                            ChallengeChoice("Health metric", metric, available.map { ChallengeOption(it, healthMetricName(it)) },
                                change = { metric = it }, error = errors[ChallengeIssue.METRIC].takeIf { attempted })
                            Label(when (metric) {
                                Metric.SLEEP -> "Sleep duration counts on the day you wake up."
                                Metric.DISTANCE -> "PULSE uses your daily distance in kilometers."
                                else -> "PULSE uses your daily step total."
                            })
                        }
                    }
                }
            }
            MakerSection("target", "Goal & Rules", rule.title, goalOpen, { goalOpen = !goalOpen }) {
                val sleepOnly = auto && metric == Metric.SLEEP
                ScrollToError(focusFor(ChallengeIssue.SLEEP_RULE)) {
                    ChallengeChoice("Goal rule", rule,
                        listOf(ChallengeRule.CUMULATIVE, ChallengeRule.FLEXIBLE, ChallengeRule.STREAK, ChallengeRule.ONE_TIME)
                            .filter { !sleepOnly || it != ChallengeRule.CUMULATIVE }.map { ChallengeOption(it, it.title, it.explanation) },
                        change = { rule = it }, fallback = "Choose a daily sleep goal")
                }
                val daily = rule != ChallengeRule.CUMULATIVE
                val targetLabel = when {
                    checkbox && daily -> "Completions on a goal day"
                    checkbox -> "Times to complete"
                    auto && metric == Metric.STEPS -> if (daily) "Steps on a goal day" else "Total steps to reach"
                    auto && metric == Metric.SLEEP -> "Hours of sleep on a goal day"
                    auto && metric == Metric.DISTANCE -> if (daily) "Kilometers on a goal day" else "Total kilometers to reach"
                    draft.unit.isBlank() -> if (daily) "Daily target" else "Total target"
                    else -> if (daily) "Daily target (${draft.unit})" else "Total target (${draft.unit})"
                }
                ChallengeField(targetLabel, target, { target = it }, errors[ChallengeIssue.TARGET], attempted,
                    focusFor(ChallengeIssue.TARGET), keyboard = if (needsWholeTarget) KeyboardType.Number else KeyboardType.Decimal,
                    maxLength = 16)
                ChallengeChoice("Challenge duration", durationChoice, (durationPresets + "Custom").map {
                    ChallengeOption(it, if (it == "Custom") "Custom duration" else "$it days")
                }, change = { durationChoice = it })
                if (durationChoice == "Custom") {
                    ChallengeField("Duration in days", customDuration, { customDuration = it }, errors[ChallengeIssue.DURATION],
                        attempted, focusFor(ChallengeIssue.DURATION), keyboard = KeyboardType.Number, maxLength = 3)
                }
                if (rule.countsDays) {
                    ChallengeField(if (rule == ChallengeRule.STREAK) "Days in a row" else "Days to complete",
                        requiredDays, { requiredDays = it }, errors[ChallengeIssue.REQUIRED_DAYS], attempted,
                        focusFor(ChallengeIssue.REQUIRED_DAYS), keyboard = KeyboardType.Number, maxLength = 3)
                }
                ScrollToError(focusFor(ChallengeIssue.START_DATE)) {
                    ChallengeSelection("Start date", if (draft.start == today) "Today · ${today.displayDate()}" else draft.start.displayDate(),
                        icon = "calendar") { dateOpen = true }
                    if (attempted) errors[ChallengeIssue.START_DATE]?.let { ChallengeError(it) }
                }
            }
            ChallengePreview(draft, firstError)
        }
    }
    if (dateOpen) ChallengeDatePicker(draft.start, today, { dateOpen = false }) { startDay = it.toEpochDay() }
}

private fun healthMetricName(metric: Metric) = if (metric == Metric.SLEEP) "Sleep duration" else metric.label

@Composable
private fun ScrollToError(request: Int, content: @Composable ColumnScope.() -> Unit) {
    val requester = remember { BringIntoViewRequester() }
    Column(Modifier.bringIntoViewRequester(requester), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
    LaunchedEffect(request) { if (request != 0) requester.bringIntoView() }
}

@Composable
private fun MakerSection(icon: String, title: String, summary: String, expanded: Boolean, toggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit) {
    val motion = LocalAnimations.current
    Surface(shape = RoundedCornerShape(PulseTokens.corner), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().animateContentSize(tween(if (motion) PulseTokens.reflowMillis else 0))) {
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = toggle)
                    .heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 8.dp)
                    .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Glyph(icon, color = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Label(summary)
                }
                Glyph("next", Modifier.size(16.dp).rotate(if (expanded) -90f else 90f))
            }
            if (expanded) Column(
                Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp), content = content
            )
        }
    }
}

@Composable
private fun IconPicker(icon: String, select: (String) -> Unit) {
    val selectedName = challengeIcons.firstOrNull { it.first == icon }?.second.orEmpty()
    Label("Icon · $selectedName")
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 384.dp) 8 else 4
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            challengeIcons.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { (key, title) ->
                        val selected = key == icon
                        Surface(
                            modifier = Modifier.size(PulseTokens.touchTarget).clip(RoundedCornerShape(8.dp))
                                .selectable(selected, role = Role.RadioButton) { select(key) }
                                .semantics { contentDescription = "$title icon" },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                            contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                        ) {
                            Box(contentAlignment = Alignment.Center) { Glyph(key, Modifier.size(20.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChallengePreview(challenge: PersonalChallenge, firstError: ChallengeIssue?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Glyph(challenge.icon, color = MaterialTheme.colorScheme.primary)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Your challenge", style = MaterialTheme.typography.titleMedium)
            if (firstError != null) {
                Label(when (firstError) {
                    ChallengeIssue.NAME -> "Add a name to preview your challenge."
                    ChallengeIssue.CATEGORY -> "Name your custom category to see the summary."
                    ChallengeIssue.METRIC, ChallengeIssue.UNIT -> "Finish the tracking choices to see the summary."
                    else -> "Finish the goal settings to see the summary."
                })
            } else {
                Text(challenge.name, style = MaterialTheme.typography.bodyMedium)
                Text(challenge.summary(), style = MaterialTheme.typography.bodyMedium)
                Label("Starts ${challenge.start.displayDate()} · ${if (challenge.tracking == ChallengeTracking.AUTOMATIC) challenge.source.sourceName else challenge.tracking.title}")
            }
        }
    }
}
