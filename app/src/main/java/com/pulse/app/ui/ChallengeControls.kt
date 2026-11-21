package com.pulse.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pulse.app.core.DataMode
import com.pulse.app.core.ChallengeRule
import com.pulse.app.core.ChallengeTracking
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

internal val ChallengeTracking.title: String get() = when (this) {
    ChallengeTracking.AUTOMATIC -> "Track automatically"
    ChallengeTracking.CHECKBOX -> "Check off manually"
    ChallengeTracking.NUMERIC -> "Enter a number"
    ChallengeTracking.PERSONAL_GOALS -> "Personal goals"
}

internal val ChallengeRule.title: String get() = when (this) {
    ChallengeRule.CUMULATIVE -> "Total progress"
    ChallengeRule.FLEXIBLE -> "Days completed"
    ChallengeRule.STREAK -> "Consecutive days"
    ChallengeRule.ONE_TIME -> "Single-day milestone"
}

internal val ChallengeRule.explanation: String get() = when (this) {
    ChallengeRule.CUMULATIVE -> "Add up progress across the whole challenge."
    ChallengeRule.FLEXIBLE -> "Reach your daily goal on a set number of days, in any order."
    ChallengeRule.STREAK -> "Reach your daily goal on several days in a row."
    ChallengeRule.ONE_TIME -> "Reach the target within a single day."
}

internal val DataMode.sourceName: String get() = when (this) {
    DataMode.HEALTH_CONNECT -> "Health Connect"
    DataMode.GARMIN -> "Garmin"
    DataMode.GOOGLE_HEALTH -> "Google Health"
    DataMode.NONE -> "Connected health source"
}

internal fun parseChallengeNumber(text: String, locale: Locale = Locale.getDefault()): Double? {
    val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
    val normalized = text.trim().map { char ->
        when {
            char == separator -> '.'
            char.digitToIntOrNull() != null -> '0' + char.digitToInt()
            else -> char
        }
    }.joinToString("")
    return normalized.takeIf { it.matches(Regex("[0-9]*[.]?[0-9]+")) }
        ?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
}

internal fun LocalDate.displayDate() = format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
internal fun calendarMonthDays(month: YearMonth, firstWeekday: DayOfWeek): List<LocalDate?> {
    val first = month.atDay(1)
    val leading = (first.dayOfWeek.value - firstWeekday.value + 7) % 7
    val cells = ((leading + month.lengthOfMonth() + 6) / 7) * 7
    return List(cells) { index ->
        val day = index - leading + 1
        if (day in 1..month.lengthOfMonth()) month.atDay(day) else null
    }
}

@Composable
internal fun ChallengeTopBar(title: String, back: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.reveal().fillMaxWidth().heightIn(min = 52.dp).padding(end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconAction("back", "Back", onClick = back)
            Text(title, Modifier.padding(vertical = 8.dp).semantics { heading() },
                style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
internal fun ChallengeError(message: String) {
    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
internal fun ChallengeField(
    label: String,
    value: String,
    change: (String) -> Unit,
    error: String? = null,
    submitted: Boolean = false,
    focusRequest: Int = 0,
    multiline: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    maxLength: Int = 80
) {
    var visited by rememberSaveable { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val bringIntoView = remember { BringIntoViewRequester() }
    val showError = error != null && (submitted || (visited && !focused))
    val colors = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = { change(it.take(maxLength)) },
        modifier = Modifier.fillMaxWidth().bringIntoViewRequester(bringIntoView).focusRequester(focus)
            .onFocusChanged {
                if (focused && !it.isFocused) visited = true
                focused = it.isFocused
            },
        label = { Text(label) },
        singleLine = !multiline,
        maxLines = if (multiline) 3 else 1,
        shape = RoundedCornerShape(8.dp),
        isError = showError,
        supportingText = if (showError) ({ ChallengeError(error.orEmpty()) }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.onSurfaceVariant,
            focusedLabelColor = colors.onSurface,
            unfocusedBorderColor = colors.outline
        )
    )
    LaunchedEffect(focusRequest) {
        if (focusRequest != 0) {
            focus.requestFocus()
            bringIntoView.bringIntoView()
        }
    }
}

internal data class ChallengeOption<T>(val value: T, val title: String, val description: String? = null, val icon: String? = null, val enabled: Boolean = true)

@Composable
internal fun ChallengeSelection(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: String? = null,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = shape
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
                .heightIn(min = PulseTokens.touchTarget).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) Glyph(icon)
            Column(Modifier.weight(1f)) {
                Label(label)
                Text(value, style = MaterialTheme.typography.bodyMedium)
            }
            Glyph("next", Modifier.size(16.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun <T> ChallengeChoice(
    label: String,
    value: T,
    options: List<ChallengeOption<T>>,
    change: (T) -> Unit,
    modifier: Modifier = Modifier,
    fallback: String = "Choose an option",
    error: String? = null
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val current = options.firstOrNull { it.value == value }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ChallengeSelection(label, current?.title ?: fallback, icon = current?.icon) { open = true }
        if (error != null) ChallengeError(error)
    }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(label) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).selectableGroup()) {
                options.forEach { option ->
                    val selected = option.value == value
                    val shape = RoundedCornerShape(8.dp)
                    Row(
                        Modifier.fillMaxWidth().clip(shape)
                            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                            .selectable(selected, enabled = option.enabled, role = Role.RadioButton) { change(option.value); open = false }
                            .heightIn(min = PulseTokens.touchTarget).padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RadioButton(selected, onClick = null, enabled = option.enabled)
                        if (option.icon != null) Glyph(option.icon, color = MaterialTheme.colorScheme.onSurface)
                        Column(Modifier.weight(1f)) {
                            Text(option.title, color = if (option.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.titleMedium)
                            if (option.description != null) Label(option.description)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton({ open = false }) { Text("Cancel") } },
        shape = RoundedCornerShape(PulseTokens.corner),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    )
}

@Composable
internal fun ChallengeDatePicker(value: LocalDate, today: LocalDate, dismiss: () -> Unit, change: (LocalDate) -> Unit) {
    var selectedDay by rememberSaveable { mutableLongStateOf(maxOf(value, today).toEpochDay()) }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(maxOf(value, today)).toString()) }
    val selected = LocalDate.ofEpochDay(selectedDay)
    val month = YearMonth.parse(monthText)
    val locale = LocalConfiguration.current.locales[0]
    val firstWeekday = WeekFields.of(locale).firstDayOfWeek
    val days = remember(month, firstWeekday) { calendarMonthDays(month, firstWeekday) }
    val colors = MaterialTheme.colorScheme
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
            Surface(
                Modifier.widthIn(max = 400.dp).fillMaxWidth().heightIn(max = maxHeight),
                shape = RoundedCornerShape(PulseTokens.corner),
                color = colors.surface
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Start date", Modifier.padding(horizontal = 8.dp, vertical = 4.dp).semantics { heading() },
                        style = MaterialTheme.typography.titleMedium)
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconAction("back", "Previous month", enabled = month > YearMonth.from(today)) {
                                monthText = month.minusMonths(1).toString()
                            }
                            Text(month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)),
                                Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            IconAction("next", "Next month") { monthText = month.plusMonths(1).toString() }
                        }
                        Row(Modifier.fillMaxWidth()) {
                            repeat(7) { offset ->
                                val weekday = firstWeekday.plus(offset.toLong())
                                Box(Modifier.weight(1f).padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                                    Label(weekday.getDisplayName(TextStyle.NARROW, locale),
                                        Modifier.clearAndSetSemantics {
                                            contentDescription = weekday.getDisplayName(TextStyle.FULL, locale)
                                        })
                                }
                            }
                        }
                        days.chunked(7).forEach { week ->
                            Row(Modifier.fillMaxWidth()) {
                                week.forEach { day ->
                                    if (day == null) {
                                        Spacer(Modifier.weight(1f).height(PulseTokens.touchTarget))
                                    } else {
                                        val active = day >= today
                                        val chosen = day == selected
                                        Box(
                                            Modifier.weight(1f).heightIn(min = PulseTokens.touchTarget)
                                                .clip(CircleShape).selectable(chosen, enabled = active, role = Role.RadioButton) {
                                                    selectedDay = day.toEpochDay()
                                                }.semantics { contentDescription = day.displayDate() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(day.dayOfMonth.toString(),
                                                Modifier.clip(CircleShape).background(if (chosen) colors.primary else colors.surface)
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = when {
                                                    chosen -> colors.onPrimary
                                                    active -> colors.onSurface
                                                    else -> colors.onSurfaceVariant.copy(alpha = .55f)
                                                })
                                        }
                                    }
                                }
                            }
                        }
                    }
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)) {
                        TextButton(dismiss) { Text("Cancel") }
                        TextButton({ change(selected); dismiss() }) { Text("Set date") }
                    }
                }
            }
        }
    }
}
