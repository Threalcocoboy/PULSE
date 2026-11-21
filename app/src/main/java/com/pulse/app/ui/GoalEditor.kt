package com.pulse.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pulse.app.core.*
import com.pulse.app.integration.GoalSources
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

@Composable
fun GoalEditor(p: Preferences, update: ((Preferences) -> Preferences) -> Unit,
    snapshot: HealthSnapshot = HealthSnapshot(), advance: (() -> Unit)? = null) {
    val goals = remember(p) { Goals.configurations(p) }
    val active = goals.filterNot { it.archived }
    val archived = goals.filter { it.archived }
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var archiveId by rememberSaveable { mutableStateOf<String?>(null) }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var requested by remember { mutableStateOf<PersonalGoal?>(null) }
    var savedId by remember { mutableStateOf<String?>(null) }
    val confirmed = requested?.takeIf { it in goals }?.id
    LaunchedEffect(confirmed) {
        if (confirmed != null) {
            savedId = confirmed
            delay(2000)
            savedId = null
        }
    }
    fun save(goal: PersonalGoal) = update { Goals.save(it, goal) }

    val motion = LocalAnimations.current
    Column(Modifier.animateContentSize(PulseMotion.settle(motion)), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (advance == null) Text("Personal goals", style = MaterialTheme.typography.titleLarge)
        PulseVisibility(active.isEmpty()) { Label("No personal goals yet.") }
        active.forEachIndexed { index, goal -> key(goal.id) {
            val available = GoalSources.available(goal.metric, snapshot, p.mode)
            val progress = remember(goal, snapshot, today, zone, available) {
                if (available) Goals.current(goal, snapshot, today, zone) else GoalProgress(null, null, false)
            }
            PulseCard(Modifier.reveal(index + 2, scale = .98f).fillMaxWidth().pulseClickable { sheet = goal.id }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Glyph(goal.metric.category.icon, color = Red)
                    Column(Modifier.weight(1f)) {
                        Text(goal.metric.title, style = MaterialTheme.typography.titleMedium)
                        Label(goal.summary(p.imperial))
                        val status = when {
                            !available -> GoalSources.availability(goal.metric, snapshot, p.mode).label
                            progress.value == null -> "No readings this period"
                            progress.met == true -> "Reached · ${goal.progressText(progress, p.imperial)}"
                            progress.met == false && progress.closed -> "Missed · ${goal.progressText(progress, p.imperial)}"
                            else -> "Recorded · ${goal.progressText(progress, p.imperial)}"
                        }
                        PulseSwap(status, label = "Recorded goal status") { MetaText(it) }
                        PulseVisibility(savedId == goal.id) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Glyph("check", Modifier.size(15.dp), Red)
                                Label("Goal saved", color = Red)
                            }
                        }
                    }
                    PulseSwap(progress.met == true && available, label = "Goal completion", distance = 5) { reached ->
                        Glyph(if (reached) "check" else "next", Modifier.size(16.dp),
                            if (reached) Red else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                goal.progressFraction(progress)?.let { PulseProgress(it.toFloat(), Modifier.fillMaxWidth().height(2.dp)) }
            }
        } }
        OutlinedButton({ sheet = "new" }, Modifier.fillMaxWidth()) { Text("New goal") }
        if (archived.isNotEmpty()) {
            SmallAction(if (showArchived) "Hide deleted goals" else "Deleted goals (${archived.size})") { showArchived = !showArchived }
            PulseVisibility(showArchived) { Column { archived.forEach { goal ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(goal.metric.title); Label(goal.summary(p.imperial)) }
                    SmallAction("Restore") { save(goal.copy(archived = false)) }
                }
            } } }
        }
        if (advance != null) {
            PulseButton("Continue", Modifier.fillMaxWidth()) {
                update { it.copy(goalsSetup = if (Goals.active(it).isEmpty()) SetupStatus.SKIPPED else SetupStatus.COMPLETE) }
                advance()
            }
        }
    }
    sheet?.let { entry ->
        val existing = active.firstOrNull { it.id == entry }
        if (entry == "new" || existing != null) GoalSheet(p, snapshot, existing,
            dismiss = { sheet = null }, onSave = { requested = it; save(it); sheet = null },
            onDelete = { archiveId = existing?.id })
    }
    active.firstOrNull { it.id == archiveId }?.let { goal ->
        PulseConfirmDialog(onDismiss = { archiveId = null }, title = { Text("Delete ${goal.metric.title} goal?") },
            text = { Text("You can restore this goal from Deleted goals. Active challenges that use it may wait for it to be restored.") },
            confirmLabel = "Delete", onConfirm = { save(goal.copy(archived = true)); archiveId = null; sheet = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun GoalSheet(p: Preferences, snapshot: HealthSnapshot, existing: PersonalGoal?, dismiss: () -> Unit,
    onSave: (PersonalGoal) -> Unit, onDelete: () -> Unit) {
    var metricId by rememberSaveable { mutableStateOf(existing?.metricId) }
    var query by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    fun choose(id: String?) { keyboard?.hide(); focus.clearFocus(); metricId = id }
    PulseSheet(dismiss) { close ->
        BackHandler(existing == null && metricId != null) { choose(null) }
        Column(Modifier.fillMaxWidth().heightIn(max = 610.dp).imePadding().padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (existing == null && metricId != null) IconAction("back", "Back to metrics") { choose(null) }
                Text(if (existing != null) "Edit goal" else if (metricId == null) "New goal" else "Configure goal",
                    Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconAction("close", "Close goal editor", onClick = { close(dismiss) })
            }
            PulseSwap(metricId, label = "Goal editor", distance = 14, direction = { _, next -> if (next == null) -1 else 1 }) { id ->
                val metric = id?.let(GoalMetrics::get)
                if (metric == null) GoalMetricPicker(p, snapshot, query, { query = it }) { choose(it.id) }
                else GoalConfiguration(metric, p, snapshot, existing, onSave = {
                    keyboard?.hide(); focus.clearFocus(); close { onSave(it) }
                }, onDelete = onDelete)
            }
        }
    }
}

@Composable private fun GoalMetricPicker(p: Preferences, snapshot: HealthSnapshot, query: String, search: (String) -> Unit,
    choose: (GoalMetric) -> Unit) {
    val entries = remember(snapshot.available, query) {
        GoalSources.metrics(snapshot).filter { it.matches(query) }.groupBy { it.category }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val motion = LocalAnimations.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(query, search, Modifier.fillMaxWidth(), label = { Text("Search metrics") }, singleLine = true,
            trailingIcon = { PulseVisibility(query.isNotEmpty()) { IconAction("close", "Clear metric search") { search("") } } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }))
        PulseVisibility(entries.isEmpty()) { Text("No matching metrics.", Modifier.padding(vertical = 18.dp)) }
        LazyColumn(Modifier.weight(1f, fill = false).testTag("goal-metric-list"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MetricCategory.entries.forEach { category ->
                entries[category]?.let { group ->
                    item(key = "category:${category.name}") { Label(category.title, Modifier.padding(top = 10.dp, bottom = 4.dp)) }
                    items(group, key = { it.id }) { metric ->
                        val ready = GoalSources.available(metric, snapshot, p.mode)
                        val availability = GoalSources.availability(metric, snapshot, p.mode)
                        val provider = availability.providers.joinToString(", ") { it.displayName() }
                        Row(Modifier.fillMaxWidth().then(if (motion) Modifier.animateItem(placementSpec = PulseMotion.settle(true)) else Modifier)
                            .pulseClickable { choose(metric) }.padding(vertical = 9.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Glyph(category.icon, color = if (ready) Red else MaterialTheme.colorScheme.onSurfaceVariant)
                            Column(Modifier.weight(1f)) {
                                Text(metric.title, style = MaterialTheme.typography.titleMedium)
                                Label(listOf(metric.displayUnit(p.imperial), availability.label, provider).filter(String::isNotBlank).joinToString(" · "))
                                if (metric.description.isNotEmpty() && query.isNotBlank()) Label(metric.description)
                            }
                            Glyph("next", Modifier.size(14.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun GoalConfiguration(metric: GoalMetric, p: Preferences, snapshot: HealthSnapshot, existing: PersonalGoal?,
    onSave: (PersonalGoal) -> Unit, onDelete: () -> Unit) {
    val id = rememberSaveable(metric.id, existing?.id) { existing?.id ?: UUID.randomUUID().toString() }
    var cadence by rememberSaveable(metric.id, existing?.id) { mutableStateOf(existing?.cadence ?: GoalCadence.DAILY) }
    var aggregation by rememberSaveable(metric.id, existing?.id) { mutableStateOf(existing?.aggregation ?: metric.aggregations(cadence).first()) }
    var condition by rememberSaveable(metric.id, existing?.id) { mutableStateOf(existing?.condition ?: metric.conditions.first()) }
    var frequency by rememberSaveable(metric.id, existing?.id) { mutableStateOf(existing?.frequency ?: GoalFrequency.EACH_PERIOD) }
    var target by rememberSaveable(metric.id, existing?.id) { mutableStateOf(existing?.let { metric.inputText(it.target, p.imperial) }.orEmpty()) }
    var upper by rememberSaveable(metric.id, existing?.id) { mutableStateOf(existing?.upper?.let { metric.inputText(it, p.imperial) }.orEmpty()) }
    var required by rememberSaveable(metric.id, existing?.id) { mutableStateOf((existing?.requiredDays ?: 3).toString()) }
    var tolerance by rememberSaveable(metric.id, existing?.id) { mutableStateOf((existing?.toleranceMinutes ?: 30).toString()) }
    fun numberFrom(input: String) = input.trim().replace(',', '.').toDoubleOrNull()?.div(metric.scale(p.imperial))
    val amount = if (metric.sleepTiming) parseChallengeTime(target)?.toDouble() else numberFrom(target)
    val goal = amount?.let {
        PersonalGoal(id, metric.id, it, numberFrom(upper).takeIf { condition == GoalCondition.RANGE }, cadence, aggregation, condition,
            frequency, required.toIntOrNull() ?: 0, tolerance.toIntOrNull() ?: 0).takeIf(PersonalGoal::valid)
    }

    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Glyph(metric.category.icon, color = Red)
            Text(metric.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        }
        if (metric.description.isNotEmpty()) Label(metric.description)
        if (!GoalSources.available(metric, snapshot, p.mode)) Label("${GoalSources.availability(metric, snapshot, p.mode).label} to track progress.")
        if (metric.cadences.size > 1) GoalChoices("Period", metric.cadences, cadence, GoalCadence::title) {
            if (cadence != it) {
                cadence = it
                aggregation = metric.aggregations(it).first()
                frequency = GoalFrequency.EACH_PERIOD
            }
        }
        if (metric.aggregations(cadence).size > 1) GoalChoices("Aggregation", metric.aggregations(cadence), aggregation, GoalAggregation::title) { aggregation = it }
        if (metric.conditions.size > 1) GoalChoices("Target rule", metric.conditions, condition, GoalCondition::title) { condition = it }
        val unit = metric.displayUnit(p.imperial)
        val targetLabel = when (condition) {
            GoalCondition.TIME -> "Time (24-hour HH:mm)"
            GoalCondition.RANGE -> "Lower limit ($unit)"
            else -> "Target ($unit)"
        }
        GoalInput(target, { target = it }, targetLabel, if (metric.sleepTiming) KeyboardType.Text else KeyboardType.Decimal)
        PulseVisibility(condition == GoalCondition.RANGE) { GoalInput(upper, { upper = it }, "Upper limit ($unit)", KeyboardType.Decimal) }
        PulseVisibility(condition == GoalCondition.TIME) { GoalInput(tolerance, { tolerance = it }, "Tolerance (minutes, 1–180)", KeyboardType.Number) }
        if (cadence == GoalCadence.DAILY) {
            GoalChoices("Frequency", GoalFrequency.entries, frequency, GoalFrequency::title) { frequency = it }
            PulseVisibility(frequency != GoalFrequency.EACH_PERIOD) { GoalInput(required, { required = it }, "Successful days (1–7)", KeyboardType.Number) }
        }
        PulseVisibility(goal != null) {
            PulseSwap(goal?.summary(p.imperial).orEmpty(), label = "Goal preview", distance = 5) {
                PulseCard(Modifier.fillMaxWidth()) { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
        PulseButton("Save goal", Modifier.fillMaxWidth(), enabled = goal != null) { goal?.let(onSave) }
        if (existing != null) TextButton(onDelete) { Text("Delete goal", color = Red) }
    }
}

@Composable private fun GoalInput(value: String, change: (String) -> Unit, label: String, type: KeyboardType) {
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(value, { change(it.take(24)) }, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun <T> GoalChoices(title: String, options: List<T>, current: T, label: (T) -> String, change: (T) -> Unit) {
    Column {
        Label(title)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { option -> FilterChip(option == current, { change(option) }, label = { Text(label(option)) }) }
        }
    }
}

private fun GoalMetric.scale(imperial: Boolean) = if (source == Metric.DISTANCE && imperial) .621371 else 1.0
private fun GoalMetric.displayUnit(imperial: Boolean) = if (source == Metric.DISTANCE && imperial) "mi" else unit
private fun GoalMetric.inputText(value: Double, imperial: Boolean): String = if (sleepTiming) localGoalTime(value)
    else NumberFormat.getNumberInstance().apply { maximumFractionDigits = 6; isGroupingUsed = false }.format(value * scale(imperial))

private fun localGoalTime(value: Double) = LocalTime.ofSecondOfDay(value.toLong() * 60).toString()

private fun GoalMetric.amount(value: Double, imperial: Boolean) = if (sleepTiming) localGoalTime(value)
    else "${NumberFormat.getNumberInstance().apply { maximumFractionDigits = 3 }.format(value * scale(imperial))} ${displayUnit(imperial)}"

internal fun PersonalGoal.summary(imperial: Boolean): String {
    val scope = when {
        condition == GoalCondition.TIME -> "Within $toleranceMinutes min of ${localGoalTime(target)}"
        condition == GoalCondition.RANGE -> "${condition.title} ${metric.amount(target, imperial)} and ${metric.amount(requireNotNull(upper), imperial)}"
        else -> "${condition.title} ${metric.amount(target, imperial)}"
    }
    val period = when (frequency) {
        GoalFrequency.DAYS_PER_WEEK -> "$requiredDays ${if (requiredDays == 1) "day" else "days"} per week"
        GoalFrequency.STREAK_PER_WEEK -> "$requiredDays consecutive ${if (requiredDays == 1) "day" else "days"} per week"
        GoalFrequency.EACH_PERIOD -> if (cadence == GoalCadence.DAILY) "Daily" else "Weekly ${aggregation.title.lowercase()}"
    }
    return "$scope · $period"
}

internal fun PersonalGoal.progressText(progress: GoalProgress, imperial: Boolean): String = progress.value?.let { value ->
    if (frequency == GoalFrequency.EACH_PERIOD) metric.amount(value, imperial) else "${value.toInt()} / $requiredDays days"
} ?: "No readings"

internal fun PersonalGoal.progressFraction(progress: GoalProgress): Double? = when {
    progress.met == true -> 1.0
    progress.value == null -> null
    frequency != GoalFrequency.EACH_PERIOD -> progress.value / requiredDays
    condition == GoalCondition.MINIMUM && target > 0 -> progress.value / target
    else -> null
}
