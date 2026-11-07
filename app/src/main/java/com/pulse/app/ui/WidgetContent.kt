package com.pulse.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.pulse.app.core.*
import java.time.LocalDate
import kotlin.math.*

val LocalWidgetEditing=staticCompositionLocalOf { false }

@Composable internal fun MetricWidget(
    w: Widget,
    p: Preferences,
    current: DayData?,
    days: List<DayData>,
    loading: Boolean
) {
    if(w.metric == Metric.GOAL) { GoalsWidget(w,p,days); return }
    val value = when (w.metric) {
        Metric.WEEKLY -> HistoryMath.aggregate(
            days.filter { it.date in LocalDate.now().minusDays(6)..LocalDate.now() },
            Metric.STEPS
        )
        Metric.GOAL -> current?.value(Metric.STEPS)?.div(p.stepGoal)?.times(100)
        else -> current?.value(w.metric)
    }

    when (w.shape) {
        WidgetShape.CIRCLE -> CircleWidgetContent(w, value, p, current, days, loading)
        WidgetShape.SQUARE -> SquareWidgetContent(w, value, p, current, days, loading)
        WidgetShape.PILL -> PillWidgetContent(w, value, p, current, days, loading)
        WidgetShape.BANNER -> BannerWidgetContent(w, value, p, current, days, loading)
        WidgetShape.RECTANGLE -> RectangleWidgetContent(w, value, p, current, days, loading)
    }
}

/**
 * Circle Widget: Uses a circular safe area layout.
 * Content is strictly inset to ensure it never touches or escapes the circular boundary.
 */
@Composable private fun CircleWidgetContent(
    w: Widget,
    value: Double?,
    p: Preferences,
    current: DayData?,
    days: List<DayData>,
    loading: Boolean
) {
    val scale = LocalDensity.current.fontScale
    val hasGoal = w.metric in setOf(Metric.STEPS, Metric.GOAL, Metric.SLEEP) && w.showGoal
    val progress = when (w.metric) {
        Metric.STEPS, Metric.GOAL -> (value ?: 0.0) / p.stepGoal
        Metric.SLEEP -> (value ?: 0.0) / p.sleepGoal
        else -> 0.0
    }

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val diameter = minOf(maxWidth, maxHeight)
        val isLarge = diameter >= 220.dp

        Box(
            Modifier
                .size(diameter)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            // Concentric perimeter dot ring
            if (hasGoal) {
                DotRing(progress, Modifier.fillMaxSize().padding(6.dp))
            }

            // Safe inner area (inscribed diameter * 0.72)
            val safeSize = diameter * 0.72f
            Box(
                Modifier
                    .size(safeSize)
                    .padding(if (isLarge) 12.dp else 4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isLarge) {
                    // Expanded Large Circle Composition (4x4)
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Top Section: Metric Label + icon dot
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(Modifier.size(6.dp).background(Red, CircleShape))
                            Text(
                                w.metric.label.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }

                        // Center: Prominent Value
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            val countStr = if (loading && value == null) "···" else valueText(value, w.metric, p.imperial)
                            val fontSize = (safeSize.value * 0.22f / (countStr.length.coerceAtLeast(3) * 0.16f * scale))
                                .coerceIn(24f, 44f).sp
                            Text(
                                countStr,
                                fontSize = fontSize,
                                fontWeight = FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                            if (w.metric !in setOf(Metric.SLEEP, Metric.SLEEP_STAGES)) {
                                Text(
                                    if (w.metric == Metric.DISTANCE && p.imperial) "mi" else w.metric.unit,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Bottom Section: Goal / Status
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (hasGoal && value != null) {
                                val percent = (progress * 100).toInt()
                                Text(
                                    "$percent% of goal",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (percent >= 100) Red else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                            } else {
                                Text(
                                    when (current?.readings?.get(w.metric)?.state) {
                                        DataState.PERMISSION_DENIED -> "Permission needed"
                                        DataState.UNSUPPORTED -> "Unavailable"
                                        else -> if (value == null) "No data" else "Today"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }

                            // Secondary metrics or compact sparkline strictly constrained
                            if (w.showTrend && w.detailed && scale < 1.3f) {
                                val recent = days.takeLast(5).map { it.value(w.metric) }
                                if (recent.any { it != null }) {
                                    DotChart(
                                        recent,
                                        Modifier.width(safeSize * 0.65f).height(20.dp),
                                        description = "Trend"
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Standard Circle Composition (2x2)
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            w.metric.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        val countStr = if (loading && value == null) "···" else valueText(value, w.metric, p.imperial)
                        val fontSize = (safeSize.value * 0.28f / (countStr.length.coerceAtLeast(3) * 0.18f * scale))
                            .coerceIn(18f, 30f).sp
                        Text(
                            countStr,
                            fontSize = fontSize,
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                        Spacer(Modifier.height(2.dp))
                        if (hasGoal && value != null) {
                            Text(
                                "${(progress * 100).toInt()}%",
                                fontSize = 11.sp,
                                color = Red,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Text(
                                if (w.metric == Metric.DISTANCE && p.imperial) "mi" else w.metric.unit,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Square Widget: Balanced vertical composition.
 * Supported footprints: 1x1, 2x2, 4x4.
 */
@Composable private fun SquareWidgetContent(
    w: Widget,
    value: Double?,
    p: Preferences,
    current: DayData?,
    days: List<DayData>,
    loading: Boolean
) {
    val scale = LocalDensity.current.fontScale
    val isMini = w.width == 1
    val isLarge = w.width >= 4

    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(PulseTokens.corner))
            .background(MaterialTheme.colorScheme.surface)
            .padding(if (isMini) 8.dp else PulseTokens.cardPadding)
    ) {
        if (isMini) {
            // 1x1 Mini Square
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    w.metric.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (loading && value == null) "···" else valueText(value, w.metric, p.imperial),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1
                )
                Text(
                    if (w.metric == Metric.DISTANCE && p.imperial) "mi" else w.metric.unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (isLarge) {
            // 4x4 Large Square
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(w.metric.label, style = MaterialTheme.typography.titleMedium)
                    if (w.showGoal && w.metric in setOf(Metric.STEPS, Metric.GOAL)) {
                        val pct = ((value ?: 0.0) / p.stepGoal * 100).toInt()
                        Text("$pct% goal", style = MaterialTheme.typography.labelSmall, color = Red)
                    }
                }

                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        if (loading && value == null) "···" else valueText(value, w.metric, p.imperial),
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1
                    )
                    Text(
                        if (w.metric == Metric.DISTANCE && p.imperial) "mi" else w.metric.unit,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                if (w.showTrend) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Past 7 days", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        DotChart(
                            days.takeLast(7).map { it.value(w.metric) },
                            Modifier.fillMaxWidth().height(60.dp),
                            bars = w.metric in setOf(Metric.STEPS, Metric.SLEEP, Metric.ACTIVE),
                            description = "${w.metric.label} trend"
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Label(
                        when (current?.readings?.get(w.metric)?.state) {
                            DataState.PERMISSION_DENIED -> "Permission needed"
                            DataState.UNSUPPORTED -> "Unavailable from source"
                            else -> if (value == null) "No recorded data" else "Today's summary"
                        }
                    )
                    if (w.metric == Metric.STEPS && value != null) {
                        Label(if (value >= p.stepGoal) "Goal achieved" else "${number((p.stepGoal - value).coerceAtLeast(0.0))} to go")
                    }
                }
            }
        } else {
            // 2x2 Standard Square
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        w.metric.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box(Modifier.size(5.dp).background(Red, CircleShape))
                }

                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        if (loading && value == null) "···" else valueText(value, w.metric, p.imperial),
                        fontSize = if (scale > 1.3f) 24.sp else 32.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1
                    )
                    if (w.metric !in setOf(Metric.SLEEP, Metric.SLEEP_STAGES)) {
                        Text(
                            if (w.metric == Metric.DISTANCE && p.imperial) "mi" else w.metric.unit,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }

                if (w.detailed && w.showTrend && scale < 1.4f) {
                    DotChart(
                        days.takeLast(7).map { it.value(w.metric) },
                        Modifier.fillMaxWidth().height(26.dp),
                        bars = w.metric in setOf(Metric.STEPS, Metric.SLEEP, Metric.ACTIVE),
                        description = "Trend"
                    )
                }

                Label(
                    when {
                        value == null -> "No data"
                        w.metric in setOf(Metric.STEPS, Metric.GOAL) && w.showGoal -> "${((value / p.stepGoal) * 100).toInt()}% goal"
                        w.metric == Metric.SLEEP && w.showGoal -> "${((value / p.sleepGoal) * 100).toInt()}% goal"
                        else -> "Today"
                    }
                )
            }
        }
    }
}

/**
 * Rectangle Widget: Prefers horizontal information hierarchy.
 * Supported footprints: 2x1, 2x2, 4x2, 4x3.
 */
@Composable private fun RectangleWidgetContent(
    w: Widget,
    value: Double?,
    p: Preferences,
    current: DayData?,
    days: List<DayData>,
    loading: Boolean
) {
    val scale = LocalDensity.current.fontScale
    val isSlim = w.height == 1
    val isFeatured = w.width >= 4 && w.height >= 2

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(PulseTokens.corner))
            .background(MaterialTheme.colorScheme.surface)
            .padding(PulseTokens.cardPadding)
    ) {
        if (isSlim) {
            // 2x1 Slim Rectangle: Clean horizontal row
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        w.metric.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (loading && value == null) "···" else valueText(value, w.metric, p.imperial),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1
                        )
                        Text(
                            if (w.metric == Metric.DISTANCE && p.imperial) "mi" else w.metric.unit,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (w.showGoal && w.metric in setOf(Metric.STEPS, Metric.GOAL)) {
                    val pct = ((value ?: 0.0) / p.stepGoal * 100).toInt()
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("$pct%", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Red)
                    }
                }
            }
        } else if (isFeatured) {
            // 4x2 or 4x3 Featured Rectangle (e.g., Steps Hero)
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(w.metric.label, style = MaterialTheme.typography.bodyMedium)

                Row(
                    Modifier.weight(1f).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        Modifier.weight(0.6f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val count = if (loading && value == null) "···" else valueText(value, w.metric, p.imperial)
                        val font = minOf(44f, this@BoxWithConstraints.maxWidth.value * 0.58f / (count.length * 0.56f * scale))
                            .coerceAtLeast(23f).sp
                        Text(count, fontSize = font, fontWeight = FontWeight.Normal, maxLines = 1)
                        if (w.showGoal && w.metric in setOf(Metric.STEPS, Metric.GOAL)) {
                            Text("of ${number(p.stepGoal.toDouble())} goal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else if (w.metric !in setOf(Metric.SLEEP, Metric.SLEEP_STAGES)) {
                            Text(if (w.metric == Metric.DISTANCE && p.imperial) "mi" else w.metric.unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    if (w.showGoal && w.metric in setOf(Metric.STEPS, Metric.GOAL)) {
                        BoxWithConstraints(
                            Modifier.weight(0.4f).fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            val ring = minOf(maxWidth, maxHeight, 126.dp)
                            Box(Modifier.size(ring), contentAlignment = Alignment.Center) {
                                DotRing((value ?: 0.0) / p.stepGoal, Modifier.fillMaxSize())
                                Text(
                                    if (value == null) "—" else "${((value / p.stepGoal) * 100).toInt()}%",
                                    fontSize = if (scale > 1.3f) 17.sp else 22.sp
                                )
                            }
                        }
                    } else if (w.showTrend && value != null) {
                        Box(Modifier.weight(0.4f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            DotChart(
                                days.takeLast(7).map { it.value(w.metric) },
                                Modifier.fillMaxSize().padding(vertical = 4.dp),
                                description = "Trend"
                            )
                        }
                    }
                }

                Label(
                    if (value == null) "No recorded data"
                    else if (w.metric == Metric.STEPS && value >= p.stepGoal) "Daily goal reached"
                    else if (w.metric == Metric.STEPS) "${number((p.stepGoal - value).coerceAtLeast(0.0))} to your goal"
                    else "Today's measurement"
                )

                if (w.height >= 3 && w.showTrend && scale < 1.4f) {
                    DotChart(
                        days.takeLast(7).map { it.value(w.metric) },
                        Modifier.fillMaxWidth().height(36.dp),
                        bars = w.metric in setOf(Metric.STEPS, Metric.SLEEP, Metric.ACTIVE),
                        description = "7-day trend"
                    )
                    Label("Last 7 days")
                }
            }
        } else {
            // 2x2 Standard Rectangle
            SquareWidgetContent(w, value, p, current, days, loading)
        }
    }
}

/**
 * Pill Widget: Extremely compact horizontal capsule composition.
 * Supported footprints: 2x1, 4x1.
 */
@Composable private fun PillWidgetContent(
    w: Widget,
    value: Double?,
    p: Preferences,
    current: DayData?,
    days: List<DayData>,
    loading: Boolean
) {
    val isWide = w.width >= 4
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(if (value != null) Red else MaterialTheme.colorScheme.outline, CircleShape)
                )
                Text(
                    w.metric.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    if (loading && value == null) "···" else valueText(value, w.metric, p.imperial),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                if (w.metric !in setOf(Metric.SLEEP, Metric.SLEEP_STAGES)) {
                    Text(
                        if (w.metric == Metric.DISTANCE && p.imperial) "mi" else w.metric.unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isWide && w.showGoal && w.metric in setOf(Metric.STEPS, Metric.GOAL)) {
                    val pct = ((value ?: 0.0) / p.stepGoal * 100).toInt()
                    Text("· $pct%", fontSize = 12.sp, color = Red, fontWeight = FontWeight.Normal)
                }
            }
        }
    }
}

/**
 * Banner Widget: Full-width deliberate layout.
 * Supported footprints: 4x1, 4x2.
 */
@Composable private fun BannerWidgetContent(
    w: Widget,
    value: Double?,
    p: Preferences,
    current: DayData?,
    days: List<DayData>,
    loading: Boolean
) {
    val isDeep = w.height >= 2
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(PulseTokens.corner))
            .background(MaterialTheme.colorScheme.surface)
            .padding(PulseTokens.cardPadding)
    ) {
        if (isDeep) {
            // 4x2 Deep Banner
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(w.metric.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (w.metric == Metric.WEEKLY) "Last 7 days total" else "Past week overview",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (loading && value == null) "···" else valueText(value, w.metric, p.imperial),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1
                        )
                        Text(
                            if (w.metric == Metric.DISTANCE && p.imperial) "mi" else w.metric.unit,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }

                if (w.showTrend) {
                    DotChart(
                        days.takeLast(7).map { it.value(w.metric) },
                        Modifier.fillMaxWidth().height(42.dp),
                        bars = true,
                        description = "${w.metric.label} 7-day trend"
                    )
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val daysRecorded = days.takeLast(7).count { it.value(w.metric) != null }
                    Label("$daysRecorded of 7 days recorded")
                    if (w.metric == Metric.WEEKLY) {
                        val avg = HistoryMath.aggregate(days.takeLast(7), Metric.STEPS)?.div(7)
                        Label("Avg ${number(avg)} / day")
                    }
                }
            }
        } else {
            // 4x1 Slim Banner
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(0.35f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        w.metric.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            if (loading && value == null) "···" else valueText(value, w.metric, p.imperial),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1
                        )
                        Text(
                            if (w.metric == Metric.DISTANCE && p.imperial) "mi" else w.metric.unit,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (w.showTrend) {
                    Box(Modifier.weight(0.45f).height(24.dp).padding(horizontal = 8.dp)) {
                        DotChart(
                            days.takeLast(7).map { it.value(w.metric) },
                            Modifier.fillMaxSize(),
                            description = "Trend"
                        )
                    }
                }

                Box(Modifier.weight(0.2f), contentAlignment = Alignment.CenterEnd) {
                    if (w.showGoal && w.metric in setOf(Metric.STEPS, Metric.GOAL)) {
                        val pct = ((value ?: 0.0) / p.stepGoal * 100).toInt()
                        Text("$pct%", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Red)
                    } else {
                        Box(Modifier.size(6.dp).background(Red, CircleShape))
                    }
                }
            }
        }
    }
}

@Composable private fun GoalsWidget(w: Widget,p: Preferences,days: List<DayData>) {
    val goals=p.enabledGoals.intersect(Goals.available(p.mode)).toList()
    BoxWithConstraints(Modifier.fillMaxSize().clip(w.shape.outline()).background(MaterialTheme.colorScheme.surface),contentAlignment=Alignment.Center) {
        val small=maxHeight<100.dp || maxWidth<150.dp
        val inset=if(w.shape==WidgetShape.CIRCLE) maxWidth*.15f else 10.dp
        val goalWidth=((maxWidth-inset*2-12.dp*(goals.size-1))/goals.size.coerceAtLeast(1)).coerceIn(48.dp,if(small) 68.dp else 88.dp)
        Row(Modifier.fillMaxWidth().padding(horizontal=inset).horizontalScroll(rememberScrollState(),enabled=!LocalWidgetEditing.current),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            goals.forEach { metric ->
                val value=Goals.value(metric,days,LocalDate.now())
                val target=Goals.target(metric,p)
                Column(Modifier.width(goalWidth),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    if(!small) Box(contentAlignment=Alignment.Center) { DotRing((value ?: 0.0)/target,Modifier.size(48.dp));Text(if(value==null) "—" else "${(value/target*100).toInt()}%",style=MaterialTheme.typography.labelSmall) }
                    Text(metric.label,style=MaterialTheme.typography.labelSmall,maxLines=1)
                    Text("${number(value)} / ${number(target)}",style=MaterialTheme.typography.labelSmall,maxLines=1)
                    if(small) LinearProgressIndicator(progress={((value ?: 0.0)/target).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth(),color=Red)
                }
            }
        }
    }
}
