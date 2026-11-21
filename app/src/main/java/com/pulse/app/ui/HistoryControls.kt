package com.pulse.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.pulse.app.core.Metric
import kotlinx.coroutines.launch

private val historyPeriods = listOf(7 to "7D", 30 to "30D", 90 to "90D", 365 to "1Y")
private val primaryMetrics = listOf(Metric.STEPS, Metric.SLEEP, Metric.HEART, Metric.STRESS, Metric.BATTERY,
    Metric.DISTANCE, Metric.RESTING_HEART, Metric.HRV)

@Composable
fun PeriodSegments(current: Int, change: (Int) -> Unit) {
    val motion = LocalAnimations.current
    BoxWithConstraints(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(10.dp))
        .background(MaterialTheme.colorScheme.surface).padding(4.dp).selectableGroup()) {
        val width = maxWidth / historyPeriods.size
        val offset = animateDpAsState(width * historyPeriods.indexOfFirst { it.first == current }.coerceAtLeast(0),
            PulseMotion.settle(motion), label = "History range indicator")
        Box(Modifier.offset { IntOffset(offset.value.roundToPx(), 0) }.width(width).fillMaxHeight()
            .background(MaterialTheme.colorScheme.onSurface, RoundedCornerShape(7.dp)))
        Row(Modifier.fillMaxSize()) {
            historyPeriods.forEach { (days, label) ->
                val selected = current == days
                val color by animateColorAsState(if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                    PulseMotion.tween(motion, PulseMotion.Selection), label = "Range text")
                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(7.dp))
                    .selectable(selected, role = Role.Tab) { if (!selected) change(days) }, contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = color)
                }
            }
        }
    }
}

@Composable
fun MetricTabs(current: Metric, additional: Set<Metric> = emptySet(), change: (Metric) -> Unit) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val motion = LocalAnimations.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current
    val metrics = remember(additional) { (primaryMetrics + additional.sortedBy { it.label }).distinct() }
    val positions = remember { mutableStateMapOf<Metric, Dp>() }
    val x = animateDpAsState(positions[current] ?: 0.dp, PulseMotion.settle(motion), label = "History metric indicator")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).horizontalScroll(scroll)) {
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                metrics.forEach { metric ->
                    key(metric) {
                        val selected = current == metric
                        val requester = remember { BringIntoViewRequester() }
                        LaunchedEffect(selected) { if (selected) requester.bringIntoView() }
                        val color by animateColorAsState(
                            if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            PulseMotion.tween(motion, PulseMotion.Selection), label = "Metric label"
                        )
                        Box(Modifier.bringIntoViewRequester(requester).widthIn(min = 18.dp, max = 260.dp).heightIn(min = 48.dp)
                            .onGloballyPositioned { coordinates ->
                                val left = coordinates.positionInParent().x
                                val start = if (rtl) (coordinates.parentLayoutCoordinates?.size?.width ?: 0) - left - coordinates.size.width else left
                                positions[metric] = with(density) { start.toDp() }
                            }
                            .selectable(selected, role = Role.Tab) { if (!selected) change(metric) },
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(metric.label, style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.padding(bottom = 8.dp))
                        }
                    }
                }
            }
            if (positions[current] != null) Box(Modifier.align(Alignment.BottomStart)
                .offset { IntOffset(x.value.roundToPx(), -8.dp.roundToPx()) }.size(18.dp, 2.dp).background(Red))
        }
        IconAction("next", "More metrics") {
            scope.launch {
                val next = if (scroll.value >= scroll.maxValue) 0 else (scroll.value + 280).coerceAtMost(scroll.maxValue)
                if (motion) scroll.animateScrollTo(next) else scroll.scrollTo(next)
            }
        }
    }
}
