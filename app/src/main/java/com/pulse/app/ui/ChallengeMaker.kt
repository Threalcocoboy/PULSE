package com.pulse.app.ui

import androidx.compose.animation.core.animateFloatAsState
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
    val available = if (snapshot.mode == preferences.mode && snapshot.mode != DataMode.NONE && snapshot.access.verified) {
        ChallengeEvaluator.metrics.intersect(snapshot.available)
    } else emptySet()
    ChallengeMakerForm(available, preferences.mode, back, vm::createChallenge, preferences, snapshot)
}

@Composable
internal fun ChallengeMakerForm(
    available: Set<Metric>,
    source: DataMode,
    back: () -> Unit,
    create: suspend (PersonalChallenge) -> Boolean,
    preferences: Preferences = Preferences(),
    snapshot: HealthSnapshot = HealthSnapshot()
) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Personal") }
    var customCategory by rememberSaveable { mutableStateOf("") }
    var icon by rememberSaveable { mutableStateOf("book") }
    var manualIcon by rememberSaveable { mutableStateOf(false) }
    var tracking by rememberSaveable { mutableStateOf(ChallengeTracking.CHECKBOX) }
    var measure by rememberSaveable { mutableStateOf(ChallengeMeasure.STEPS) }
    var condition by rememberSaveable { mutableStateOf(ChallengeCondition.AT_LEAST) }
    var qualifier by rememberSaveable { mutableStateOf("") }
    var time by rememberSaveable { mutableStateOf("07:30") }
    var tolerance by rememberSaveable { mutableStateOf("30") }
    var selectedGoals by rememberSaveable { mutableStateOf(emptyArray<String>()) }
    var goalMatch by rememberSaveable { mutableStateOf(GoalMatch.ALL) }
    val metric = measure.metric
    val allGoals = remember(preferences) { Goals.active(preferences) }
    val goalSelection = selectedGoals.toSet()
    val goals = allGoals.filter { it.id in goalSelection }
    val goalMetrics = if (snapshot.mode == source && snapshot.access.verified) snapshot.available else available
    val goalsMode = tracking == ChallengeTracking.PERSONAL_GOALS
    val weekly = goalsMode && goals.isNotEmpty() && goals.all { it.completionCadence == GoalCadence.WEEKLY }
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
    val needsWholeTarget = checkbox || (auto && metric in setOf(Metric.STEPS, Metric.ACTIVITIES))
    val numericTarget = !goalsMode && (!auto || condition in setOf(ChallengeCondition.AT_LEAST, ChallengeCondition.BELOW))
    val range = if (auto && condition == ChallengeCondition.PERSONAL_RANGE) {
        remember(snapshot, measure, today) { ChallengeEvaluator.personalRange(snapshot, measure, today, java.time.ZoneId.systemDefault()) }
    } else null
    fun chooseMeasure(item: ChallengeMeasure) {
        measure = item
        condition = item.conditions.first()
        qualifier = when (item) {
            ChallengeMeasure.WORKOUT_TYPE -> ChallengeMeasure.workoutTypes.first()
            ChallengeMeasure.SLEEP_STAGE -> ChallengeMeasure.stages.first()
            else -> ""
        }
        if (rule !in item.rules(condition)) rule = ChallengeRule.FLEXIBLE
    }
    val draft = PersonalChallenge(
        id = id, name = name.trim(), description = description.trim(),
        category = if (category == "Custom") customCategory.trim() else category, icon = icon,
        tracking = tracking, metric = metric, rule = rule,
        target = if (numericTarget) parseChallengeNumber(target) ?: Double.NaN else 1.0,
        unit = when {
            goalsMode -> if (weekly) "weeks" else "days"
            auto -> metric.unit
            checkbox -> "times"
            unit == "Custom" -> customUnit.trim()
            else -> unit
        },
        duration = (if (durationChoice == "Custom") customDuration else durationChoice).toIntOrNull() ?: 0,
        requiredDays = requiredDays.toIntOrNull() ?: 0,
        start = LocalDate.ofEpochDay(startDay), source = source, measure = measure.takeIf { auto },
        condition = if (auto) condition else ChallengeCondition.AT_LEAST,
        qualifier = qualifier.takeIf { auto && it.isNotBlank() },
        timeMinutes = if (auto && measure.timeBased) parseChallengeTime(time) else null,
        toleranceMinutes = if (auto && measure == ChallengeMeasure.SLEEP_SCHEDULE) tolerance.toIntOrNull() ?: 0 else 30,
        rangeMin = range?.first, rangeMax = range?.second, goalMatch = goalMatch, sourceScope = snapshot.sourceScope,
        goalIds = if (goalsMode) goalSelection else emptySet(),
        goalCadence = if (!goalsMode || goals.isEmpty()) null else if (weekly) GoalCadence.WEEKLY else GoalCadence.DAILY,
        goalTitles = if (goalsMode) goals.map { it.metric.title } else emptyList()
    )
    val errors = draft.validationIssues().associateWith { it.message }.toMutableMap()
    if (auto && metric !in available) errors[ChallengeIssue.METRIC] = "Choose an available health metric."
    if (goalsMode && (goals.size != goalSelection.size || goals.any { it.metric.source !in goalMetrics } ||
            goals.map { it.completionCadence }.distinct().size > 1)) {
        errors[ChallengeIssue.GOALS] = "Choose goals that this source can measure."
    }
    if (weekly && ChallengeIssue.REQUIRED_DAYS in errors) {
        errors[ChallengeIssue.REQUIRED_DAYS] = "Choose a challenge window with at least ${draft.requiredDays.coerceAtLeast(1)} full weeks."
    }
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
            ChallengeIssue.METRIC, ChallengeIssue.UNIT, ChallengeIssue.GOALS, ChallengeIssue.QUALIFIER -> trackingOpen = true
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
                    ChallengeOption(ChallengeTracking.PERSONAL_GOALS, ChallengeTracking.PERSONAL_GOALS.title,
                        "Complete challenges by reaching the goals you've already set in PULSE.", "target"),
                    ChallengeOption(ChallengeTracking.CHECKBOX, ChallengeTracking.CHECKBOX.title,
                        "Tap once for each completion.", "check"),
                    ChallengeOption(ChallengeTracking.NUMERIC, ChallengeTracking.NUMERIC.title,
                        "Enter an amount, such as pages read.", "edit")
                ), change = {
                    tracking = it
                    if (it == ChallengeTracking.AUTOMATIC) {
                        val supported = measure.takeIf { item -> item.metric in available }
                            ?: ChallengeMeasure.entries.firstOrNull { item -> item.metric in available }
                        if (supported != null) chooseMeasure(supported)
                    }
                    if (it == ChallengeTracking.PERSONAL_GOALS) {
                        rule = ChallengeRule.FLEXIBLE
                        requiredDays = if (weekly) "1" else "5"
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
                        ChallengeChoice("Health metric", measure, ChallengeMeasure.entries.map { item ->
                            val supported = item.metric in available
                            val explanation = when {
                                supported -> item.detail
                                item in setOf(ChallengeMeasure.BATTERY, ChallengeMeasure.STRESS) -> "Requires a compatible Garmin connection."
                                else -> "Unavailable with your current source or permissions."
                            }
                            ChallengeOption(item, item.title, explanation, enabled = supported)
                        }, change = ::chooseMeasure, error = errors[ChallengeIssue.METRIC].takeIf { attempted })
                        if (metric in available) Label(measure.detail)
                        if (measure == ChallengeMeasure.WORKOUT_TYPE) {
                            val types = (ChallengeMeasure.workoutTypes + snapshot.days.flatMap { it.activities }
                                .map { workoutTypeName(it.type) }).distinct()
                            ChallengeChoice("Workout type", qualifier, types.map { ChallengeOption(it, it) }, change = { qualifier = it })
                        }
                        if (measure == ChallengeMeasure.SLEEP_STAGE) {
                            ChallengeChoice("Sleep stage", qualifier, ChallengeMeasure.stages.map { ChallengeOption(it, it) }, change = { qualifier = it })
                        }
                    }
                    ChallengeTracking.PERSONAL_GOALS -> ScrollToError(focusFor(ChallengeIssue.GOALS)) {
                        if (allGoals.isEmpty()) Label("Set a personal goal in Profile & Goals.")
                        allGoals.forEach { goal ->
                            val weeklyGoal = goal.completionCadence == GoalCadence.WEEKLY
                            val selectable = goal.metric.source in goalMetrics && (goals.isEmpty() || weeklyGoal == weekly)
                            val checked = goal.id in goalSelection
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked, { checkedNow ->
                                    val next = if (checkedNow) goalSelection + goal.id else goalSelection - goal.id
                                    selectedGoals = next.toTypedArray()
                                    if (checkedNow && weeklyGoal && goals.isEmpty()) {
                                        requiredDays = "1"
                                        startDay = LocalDate.ofEpochDay(startDay).with(
                                            java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.MONDAY)).toEpochDay()
                                    }
                                }, enabled = selectable || checked)
                                Column(Modifier.weight(1f)) {
                                    Text(goal.metric.title)
                                    Label(goal.summary(preferences.imperial))
                                    if (!selectable && !checked) Label(if (goal.metric.source !in goalMetrics) "Unavailable from this source" else "Choose goals with the same period")
                                }
                            }
                        }
                        if (goals.size > 1) ChallengeChoice("Goals to reach", goalMatch,
                            listOf(ChallengeOption(GoalMatch.ANY, "Any selected goal"), ChallengeOption(GoalMatch.ALL, "All selected goals")),
                            change = { goalMatch = it })
                        if (attempted) errors[ChallengeIssue.GOALS]?.let { ChallengeError(it) }
                    }
                }
            }
            MakerSection("target", "Goal & Rules", rule.title, goalOpen, { goalOpen = !goalOpen }) {
                if (auto && measure.conditions.size > 1) {
                    ChallengeChoice("Condition", condition, measure.conditions.map { ChallengeOption(it, it.title) },
                        change = {
                            condition = it
                            if (rule !in measure.rules(it)) rule = ChallengeRule.FLEXIBLE
                        })
                }
                if (condition == ChallengeCondition.PERSONAL_RANGE && auto) {
                    Label(if (range == null) ChallengeIssue.RANGE.message
                        else "Recent range: ${formatChallengeAmount(range.first)}–${formatChallengeAmount(range.second)} ${metric.unit}. Based on your recorded readings.")
                }
                if (auto && measure.timeBased) {
                    ChallengeField(if (measure == ChallengeMeasure.WORKOUT_START) "Latest workout start (HH:mm)" else "Target wake time (HH:mm)",
                        time, { time = it }, errors[ChallengeIssue.TIME], attempted, focusFor(ChallengeIssue.TIME), maxLength = 5)
                    if (measure == ChallengeMeasure.SLEEP_SCHEDULE) {
                        ChallengeField("Time window in minutes", tolerance, { tolerance = it }, errors[ChallengeIssue.TIME],
                            attempted, keyboard = KeyboardType.Number, maxLength = 3)
                    }
                }
                val rules = when {
                    goalsMode -> listOf(ChallengeRule.FLEXIBLE, ChallengeRule.STREAK)
                    auto -> measure.rules(condition)
                    else -> ChallengeRule.entries
                }
                ScrollToError(focusFor(ChallengeIssue.SLEEP_RULE)) {
                    ChallengeChoice("Goal rule", rule, rules.map {
                        ChallengeOption(it, if (weekly && it == ChallengeRule.FLEXIBLE) "Weeks completed"
                            else if (weekly && it == ChallengeRule.STREAK) "Consecutive weeks" else it.title,
                            if (weekly) "Count full Monday–Sunday weeks inside the challenge." else it.explanation)
                    }, change = { rule = it })
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
                if (numericTarget) ChallengeField(targetLabel, target, { target = it }, errors[ChallengeIssue.TARGET], attempted,
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
                    ChallengeField(if (weekly) {
                        if (rule == ChallengeRule.STREAK) "Weeks in a row" else "Weeks to complete"
                    } else if (rule == ChallengeRule.STREAK) "Days in a row" else "Days to complete",
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
    val chevron by animateFloatAsState(if (expanded) -90f else 90f, PulseMotion.settle(motion), label = "Challenge section chevron")
    Surface(shape = RoundedCornerShape(PulseTokens.corner), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth()) {
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
                Glyph("next", Modifier.size(16.dp).rotate(chevron))
            }
            PulseVisibility(expanded) {
                Column(Modifier.foregroundSemantics(expanded).reveal(1).padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
            }
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
                    ChallengeIssue.METRIC, ChallengeIssue.UNIT, ChallengeIssue.GOALS, ChallengeIssue.QUALIFIER -> "Finish the tracking choices to see the summary."
                    else -> "Finish the goal settings to see the summary."
                })
            } else {
                Text(challenge.name, style = MaterialTheme.typography.bodyMedium)
                PulseSwap(challenge.summary(), label = "Challenge preview", distance = 5) {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                Label("Starts ${challenge.start.displayDate()} · ${if (challenge.tracking.automatic) challenge.source.sourceName else challenge.tracking.title}")
            }
        }
    }
}
