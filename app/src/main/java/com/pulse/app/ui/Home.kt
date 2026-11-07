package com.pulse.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.pulse.app.core.*
import java.time.LocalDate
import kotlin.math.abs

fun metricDescription(metric: Metric): String = when (metric) {
    Metric.STEPS -> "Daily step count and progress toward your goal"
    Metric.DISTANCE -> "Total distance covered on foot"
    Metric.HEART -> "Current and resting heart rate monitoring"
    Metric.RESTING_HEART -> "Baseline cardiovascular recovery indicator"
    Metric.SLEEP -> "Total sleep duration and nightly rest tracking"
    Metric.SLEEP_STAGES -> "Duration breakdown across deep, light and REM stages"
    Metric.BATTERY -> "Garmin recovery and energy score on a 0–100 scale"
    Metric.STRESS -> "Autonomic nervous system strain on a 0–100 scale"
    Metric.HRV -> "Heart rate variability overnight trend and baseline"
    Metric.ACTIVE -> "Moderate and vigorous intensity physical minutes"
    Metric.CALORIES -> "Total daily active and resting energy expenditure"
    Metric.ACTIVITIES -> "Recorded workout sessions and exercise logs"
    Metric.GOAL -> "All your personal goals and their recorded progress"
    Metric.WEEKLY -> "Total steps accumulated over the last 7 days"
    Metric.TREND -> "Step volume trajectory compared to previous days"
}

fun sizeDisplayName(shape: WidgetShape, footprint: Footprint): String = when (shape) {
    WidgetShape.CIRCLE -> when (footprint.columns) {
        2 -> "Standard (2×2)"
        3 -> "Medium (3×3)"
        else -> "Large (4×4)"
    }
    WidgetShape.SQUARE -> when (footprint.columns) {
        1 -> "Mini (1×1)"
        2 -> "Standard (2×2)"
        3 -> "Medium (3×3)"
        else -> "Large (4×4)"
    }
    WidgetShape.PILL -> if (footprint.columns == 2) "Standard (2×1)" else "Wide (4×1)"
    WidgetShape.BANNER -> if (footprint.rows == 1) "Slim (4×1)" else "Deep (4×2)"
    WidgetShape.RECTANGLE -> when {
        footprint.columns == 2 && footprint.rows == 1 -> "Compact (2×1)"
        footprint.columns == 2 && footprint.rows == 2 -> "Standard (2×2)"
        footprint.columns == 4 && footprint.rows == 2 -> "Wide (4×2)"
        else -> "Expanded (4×3)"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun WidgetSheet(
    w: Widget,
    p: Preferences = Preferences(),
    snapshot: HealthSnapshot = HealthSnapshot(),
    dismiss: () -> Unit,
    change: (Widget) -> Unit,
    remove: () -> Unit,
    isNew: Boolean = false
) {
    var draft by remember(w) { mutableStateOf(w) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now()
    val currentDay = snapshot.days.firstOrNull { it.date == today }

    ModalBottomSheet(
        onDismissRequest = {
            dismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if(isNew) "Create widget" else "Edit widget", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${draft.metric.label} · ${metricDescription(draft.metric)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconAction("close", "Close editor", onClick = {
                    dismiss()
                })
            }

            // Live Widget Preview
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Label("LIVE PREVIEW")
                    Text(
                        "${draft.shape.name.lowercase().replaceFirstChar(Char::titlecase)} · ${draft.width}×${draft.height}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp, max = 220.dp)
                        .clip(RoundedCornerShape(PulseTokens.corner))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(PulseTokens.corner))
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val previewWidth = when (draft.width) {
                        1 -> 90.dp
                        2 -> 160.dp
                        else -> 280.dp
                    }
                    val previewHeight = when (draft.height) {
                        1 -> 70.dp
                        2 -> 140.dp
                        else -> 190.dp
                    }
                    Box(Modifier.size(previewWidth.coerceAtMost(190.dp), if (draft.shape in setOf(WidgetShape.CIRCLE, WidgetShape.SQUARE)) previewWidth.coerceAtMost(190.dp) else previewHeight)) {
                        MetricWidget(draft, p, currentDay, snapshot.days, false)
                    }
                }
            }

            // Metric Selection (Structured Flow)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label("METRIC")
                val categories = listOf(
                    "Activity" to listOf(Metric.STEPS, Metric.DISTANCE, Metric.ACTIVE, Metric.CALORIES),
                    "Vitals" to listOf(Metric.HEART, Metric.RESTING_HEART, Metric.HRV),
                    "Recovery" to listOf(Metric.SLEEP, Metric.SLEEP_STAGES, Metric.BATTERY, Metric.STRESS),
                    "Goals & Trends" to listOf(Metric.GOAL, Metric.WEEKLY, Metric.TREND)
                )

                categories.forEach { (categoryName, metrics) ->
                    Text(categoryName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        metrics.forEach { m ->
                            val selected = draft.metric == m
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    draft = draft.copy(metric = m)
                                },
                                label = {
                                    Text(
                                        m.label,
                                        color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.onSurface,
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = if (selected) Red else MaterialTheme.colorScheme.outline,
                                    enabled = true,
                                    selected = selected
                                )
                            )
                        }
                    }
                }
            }

            // Shape Selection (With Visual Previews)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label("SHAPE")
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    WidgetShape.entries.forEach { s ->
                        val selected = draft.shape == s
                        Box(
                            Modifier
                                .width(96.dp)
                                .height(72.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface)
                                .border(
                                    if (selected) 1.5.dp else 1.dp,
                                    if (selected) Red else MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    val next = draft.copy(shape = s)
                                    val bestSize = next.allowedSizes().minBy {
                                        abs(it.columns - draft.width) + abs(it.rows - draft.height)
                                    }
                                    draft = next.copy(width = bestSize.columns, height = bestSize.rows)
                                }
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Visual Shape Icon
                                ShapePreviewIcon(s, selected)
                                Text(
                                    s.name.lowercase().replaceFirstChar(Char::titlecase),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Size Selection
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label("SIZE")
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    draft.allowedSizes().forEach { footprint ->
                        val selected = draft.width == footprint.columns && draft.height == footprint.rows
                        FilterChip(
                            selected = selected,
                            onClick = {
                                draft = draft.copy(width = footprint.columns, height = footprint.rows)
                            },
                            label = { Text(sizeDisplayName(draft.shape, footprint)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.onSurface,
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = if (selected) Red else MaterialTheme.colorScheme.outline,
                                enabled = true,
                                selected = selected
                            )
                        )
                    }
                }
            }

            // Display Preferences
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label("DISPLAY PREFERENCES")
                SettingToggle("Detailed presentation", draft.detailed) {
                    draft = draft.copy(detailed = it)
                }

                if (draft.metric in setOf(Metric.STEPS, Metric.SLEEP, Metric.GOAL, Metric.DISTANCE, Metric.ACTIVE)) {
                    SettingToggle("Show goal indicator", draft.showGoal) {
                        draft = draft.copy(showGoal = it)
                    }
                }

                val canFitTrend = draft.shape != WidgetShape.PILL &&
                    !(draft.shape == WidgetShape.SQUARE && draft.width == 1) &&
                    !(draft.shape == WidgetShape.CIRCLE && draft.width == 2) &&
                    draft.metric != Metric.SLEEP_STAGES

                if (canFitTrend) {
                    SettingToggle("Show recent trend sparkline", draft.showTrend) {
                        draft = draft.copy(showTrend = it)
                    }
                }
            }

            // Footer Actions
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (confirmDelete) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = remove,
                            colors = ButtonDefaults.textButtonColors(contentColor = Red)
                        ) {
                            Text("Confirm delete")
                        }
                        SmallAction("Cancel") { confirmDelete = false }
                    }
                } else {
                    TextButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    ) {
                        Text(if(isNew) "Cancel" else "Remove widget")
                    }
                }

                PulseButton(if(isNew) "Preview placement" else "Save widget") {
                    change(draft)
                    dismiss()
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable private fun ShapePreviewIcon(shape: WidgetShape, selected: Boolean) {
    val color = if (selected) Red else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier
            .size(width = 36.dp, height = 24.dp)
            .border(
                1.5.dp,
                color,
                when (shape) {
                    WidgetShape.CIRCLE -> CircleShape
                    WidgetShape.SQUARE -> RoundedCornerShape(4.dp)
                    WidgetShape.PILL -> RoundedCornerShape(50)
                    WidgetShape.BANNER -> RoundedCornerShape(2.dp)
                    WidgetShape.RECTANGLE -> RoundedCornerShape(4.dp)
                }
            )
    )
}
