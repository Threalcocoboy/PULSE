package com.pulse.app.ui

import androidx.compose.foundation.*
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.pulse.app.core.*
import com.pulse.app.integration.googlehealth.GoogleHealthMetrics
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlinx.coroutines.launch

@Composable fun HistoryScreen(p: Preferences,snapshot: HealthSnapshot,daily:(LocalDate)->Unit) {
    var count by rememberSaveable {mutableIntStateOf(7)};var metricName by rememberSaveable {mutableStateOf(Metric.STEPS.name)}
    var endDate by rememberSaveable {mutableStateOf(LocalDate.now().toString())}
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var calendar by rememberSaveable {mutableStateOf(YearMonth.now().toString())}
    var calendarSelection by rememberSaveable { mutableStateOf<String?>(null) }
    val metric=Metric.valueOf(metricName); val end=LocalDate.parse(endDate)
    val haptics=LocalHapticFeedback.current
    val start=if(count==365) end.minusMonths(11).withDayOfMonth(1) else end.minusDays(count-1L)
    val days=remember(snapshot.days,start,end){HistoryMath.unique(snapshot.days).filter {it.date in start..end}}
    val dateValues=remember(days,count,metric,start,end) {
        if(count==365) (0L..11L).map {ago->val month=YearMonth.from(start.plusMonths(ago)); month.atDay(1) to HistoryMath.aggregate(days.filter {YearMonth.from(it.date)==month},metric)}
        else (0 until count).map {i->val date=start.plusDays(i.toLong());date to days.firstOrNull {it.date==date}?.value(metric)}
    }
    var rawHeart by rememberSaveable{mutableStateOf(true)}
    val recorded=remember(snapshot.heartSamples,start,end){snapshot.heartSamples.filter{it.time.atZone(ZoneId.systemDefault()).toLocalDate() in start..end}.groupBy{it.time.atZone(ZoneId.systemDefault()).toLocalDate()}.values.flatMap{day->val source=day.minOf{it.source};day.filter{it.source==source}.distinctBy{it.time}}.sortedBy{it.time}}
    val raw=remember(recorded){HistorySamples.plot(recorded)}
    val samples=metric==Metric.HEART&&rawHeart
    val values = if (samples) raw.map { it.bpm } else dateValues.map { it.second }
    val keys = if (samples) raw.map { it.time.toString() } else dateValues.map { it.first.toString() }
    val selected = keys.indexOf(selectedKey).takeIf { it >= 0 }
    val point = selected?.let { dateValues.getOrNull(it) }?.takeUnless { samples }
    val chosenLabel = selected?.let { index ->
        if (samples) raw[index].time.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, HH:mm:ss"))
        else point?.first?.format(DateTimeFormatter.ofPattern(if (count == 365) "MMMM yyyy" else "EEE, d MMM yyyy"))
    }
    fun choose(index: Int, toggle: Boolean) {
        val next = keys.getOrNull(index)?.takeUnless { toggle && it == selectedKey }
        if (toggle && next != selectedKey && p.haptics) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        selectedKey = next
    }
    val summary=remember(snapshot.days,end,count,metric){HistoryMath.compare(snapshot.days,end,if(count==365) java.time.temporal.ChronoUnit.DAYS.between(start,end).toInt()+1 else count,metric)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PulseTokens.screenPadding),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Row(Modifier.reveal().fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SectionTitle("History") }
            LocalPulseMenu.current?.invoke(null)
        }
        Box(Modifier.reveal(1)) { PeriodSegments(count){count=it;selectedKey=null} }
        Box(Modifier.reveal(2)) { MetricTabs(metric, snapshot.available + if (snapshot.mode == DataMode.GOOGLE_HEALTH) GoogleHealthMetrics.metrics else emptySet()) {
            metricName=it.name;selectedKey=null
        } }
        PulseCard(Modifier.reveal(3, scale = .985f).fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                IconAction("back","Previous period"){endDate=(if(count==365)end.minusYears(1)else end.minusDays(count.toLong())).toString();selectedKey=null}
                PulseSwap(chosenLabel ?: "${start.format(DateTimeFormatter.ofPattern("d MMM yy"))} — ${end.format(DateTimeFormatter.ofPattern("d MMM yy"))}",
                    Modifier.weight(1f), label = "History date", distance = 5) {
                    Text(it, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
                IconAction("next","Next period",end<LocalDate.now()){endDate=(if(count==365)end.plusYears(1)else end.plusDays(count.toLong())).coerceAtMost(LocalDate.now()).toString();selectedKey=null}
            }
            val statistic = when {
                selected != null && samples -> "Sample"
                metric.cumulative -> "Total"
                selected != null && count != 365 -> "Daily reading"
                else -> "Recorded average"
            }
            Label("${metric.label} · $statistic")
            if (snapshot.mode == DataMode.GOOGLE_HEALTH && metric in GoogleHealthMetrics.metrics && metric !in snapshot.available) {
                val state = snapshot.days.firstOrNull { it.date == LocalDate.now() }?.readings?.get(metric)?.state
                Label(if (state == DataState.UNSUPPORTED) "Unavailable from your source" else "Review Google Health permissions in Devices & Data")
            }
            val reading = if (selected == null) summary.value else values[selected]
            val unit = if (metric == Metric.DISTANCE && p.imperial) "mi" else metric.unit
            PulseSwap(metric to unit, label = "History metric reading", distance = 9) { (displayMetric, displayUnit) ->
                var lastReading by remember { mutableStateOf(reading) }
                val displayed = if (displayMetric == metric && displayUnit == unit) reading else lastReading
                SideEffect { if (displayMetric == metric && displayUnit == unit) lastReading = reading }
                Column {
                    AnimatedReading(valueText(displayed, displayMetric, displayUnit == "mi"), displayed, Modifier.fillMaxWidth()) {
                        FitText(it, Modifier.fillMaxWidth(), fontSize = 36.sp, fontWeight = FontWeight.Normal)
                    }
                    if (displayMetric != Metric.SLEEP) Label(displayUnit)
                }
            }
            if(metric==Metric.HEART)Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)){FilterChip(rawHeart,{rawHeart=true;selectedKey=null},label={Text("Samples")});FilterChip(!rawHeart,{rawHeart=false;selectedKey=null},label={Text("Daily averages")})}
            val points = remember(samples, raw, start, end) {
                if (samples && raw.isNotEmpty()) {
                    val zone = ZoneId.systemDefault()
                    val first = start.atStartOfDay(zone).toInstant().toEpochMilli()
                    val span = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - first
                    raw.map { (it.time.toEpochMilli()-first).toFloat() / span }
                } else null
            }
            val frame = HistoryChartFrame(values, metric, selected, !samples && metric in setOf(Metric.STEPS, Metric.DISTANCE, Metric.SLEEP), points, p.imperial)
            val chartKey = Triple(metric, count to end, samples)
            PulseSwap(chartKey, label = "History graph dataset", distance = 16) { key ->
                var retained by remember { mutableStateOf(frame) }
                val active = key == chartKey
                val shown = if (active) frame else retained
                SideEffect { if (active) retained = frame }
                AxisChart(shown.values, shown.metric, shown.selected, { if (active) choose(it,false) },
                    shown.bars, shown.points, shown.imperial, tap = { if (active) choose(it,true) }, interactive = active)
            }
            if(samples) Text(when{snapshot.mode==DataMode.NONE->"Connect a data source to see heart rate.";snapshot.error!=null->"Import failed · retained samples";snapshot.status.contains("unavailable",ignoreCase=true)->"Health Connect or source unavailable";Metric.HEART !in snapshot.available->"Heart-rate read permission is not granted";snapshot.heartSamples.isEmpty()->"No source heart-rate samples imported. Recorded daily averages are available separately.";raw.isEmpty()->"No authorized heart-rate samples in this interval. Daily averages remain available when recorded.";else->"${recorded.size} recorded samples · ${raw.size} plotted"},style=MaterialTheme.typography.bodySmall)

            Row(Modifier.fillMaxWidth().padding(start=54.dp),horizontalArrangement=Arrangement.SpaceBetween){Label(start.format(DateTimeFormatter.ofPattern("d MMM")));Label(end.format(DateTimeFormatter.ofPattern("d MMM")))}
            if (selected != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (values[selected] == null) Label("No reading recorded")
                    if (point != null && count != 365) SmallAction("Open day") { daily(point.first) }
                }
                SmallAction("Clear selection") { selectedKey = null }
            }
        }
        Row(Modifier.reveal(4).fillMaxWidth().padding(vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f)){Label("Coverage");Text("${summary.valid} / ${summary.total}",fontSize=22.sp);Label("Recorded days")}
            Column(Modifier.weight(1f)){Label("Previous period");Text(valueText(summary.previous,metric,p.imperial),fontSize=22.sp);Label(if(metric.cumulative)"Total"else"Average")}
        }
        HorizontalDivider(color=MaterialTheme.colorScheme.outline)
        val comparisonUnit = if (metric == Metric.DISTANCE && p.imperial) "mi" else metric.unit
        Text(if(summary.change==null) "Not enough recorded data for a complete comparison." else "${valueText(abs(summary.change!!),metric,p.imperial)} ${if(metric==Metric.SLEEP) "" else comparisonUnit} ${if(summary.change!!>=0) "higher" else "lower"} than the preceding period${summary.percent?.let {" (${String.format(java.util.Locale.getDefault(),"%.1f",abs(it))}%)"} ?: ""}.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.reveal(5)) { Text("The daily archive",style=MaterialTheme.typography.titleLarge); Label(metric.label.uppercase()) }
        Box(Modifier.reveal(6)) {
            CalendarHeatmap(YearMonth.parse(calendar),metric,p,snapshot.days,calendarSelection,{calendar=it.toString()}) {
                calendarSelection = it.toString()
                daily(it)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
private data class HistoryChartFrame(
    val values: List<Double?>, val metric: Metric, val selected: Int?, val bars: Boolean,
    val points: List<Float>?, val imperial: Boolean
)

@Composable private fun CalendarHeatmap(month: YearMonth,metric: Metric,p: Preferences,days: List<DayData>,selectedDay: String?,change:(YearMonth)->Unit,daily:(LocalDate)->Unit) {
    val motion = LocalAnimations.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconAction("back", "Previous month") { change(month.minusMonths(1)) }
            PulseSwap(month, label = "Calendar month", direction = { from, to -> to.compareTo(from).coerceIn(-1, 1) }) {
                Text(it.format(DateTimeFormatter.ofPattern("MMMM yyyy")))
            }
            IconAction("next", "Next month", month < YearMonth.now()) { change(month.plusMonths(1)) }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Label(it) } }
        }
        AnimatedContent(month, transitionSpec = {
            val direction = targetState.compareTo(initialState).coerceIn(-1, 1) * if (rtl) -1 else 1
            if (motion) (fadeIn(PulseMotion.tween(true)) + slideInHorizontally(PulseMotion.tween(true, PulseMotion.Card)) { direction * it / 5 })
                .togetherWith(fadeOut(PulseMotion.tween(true, 145)) + slideOutHorizontally(PulseMotion.tween(true)) { -direction * it / 5 })
                .using(SizeTransform { _, _ -> PulseMotion.settle(true) })
            else (EnterTransition.None togetherWith ExitTransition.None).using(null)
        }, label = "Calendar days") { shown ->
            val values = remember(days, shown) { HistoryMath.unique(days).filter { YearMonth.from(it.date) == shown }.associateBy { it.date } }
            val maximum = values.values.mapNotNull { it.value(metric) }.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
            val offset = shown.atDay(1).dayOfWeek.value - 1
            val weeks = (offset + shown.lengthOfMonth() + 6) / 7
            val today = LocalDate.now()
            Column(Modifier.foregroundSemantics(shown == month)) {
                repeat(weeks) { week ->
                    Row(Modifier.fillMaxWidth()) {
                        repeat(7) { column ->
                            val day = week * 7 + column - offset + 1
                            if (day !in 1..shown.lengthOfMonth()) Spacer(Modifier.weight(1f).height(56.dp))
                            else {
                                val date = shown.atDay(day)
                                val selected = date.toString() == selectedDay
                                val value = values[date]?.value(metric)
                                val color = if (value == null) MaterialTheme.colorScheme.outline else
                                    Red.copy(alpha = (.25 + .75 * value / maximum).toFloat().coerceIn(.25f, 1f))
                                Column(Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = shown == month && date <= today) { daily(date) }.padding(vertical = 8.dp)
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = "${date}: ${valueText(value,metric,p.imperial)} ${metric.unit}"
                                        this.selected = selected
                                    },
                                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(day.toString(), fontSize = 12.sp, color = when {
                                        date > today -> MaterialTheme.colorScheme.outline
                                        selected || date == today -> Red
                                        else -> MaterialTheme.colorScheme.onSurface
                                    })
                                    val diameter by animateDpAsState(if (selected) 12.dp else 8.dp, PulseMotion.settle(motion), label = "Calendar day selection")
                                    Box(Modifier.size(12.dp), contentAlignment = Alignment.Center) {
                                        Box(Modifier.then(if (date == today) Modifier.reveal(scale = .65f) else Modifier)
                                            .size(diameter).background(if (selected) Red else color, CircleShape))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Label("DOT INTENSITY = RECORDED ${metric.label.uppercase()} · TAP A DAY")
    }
}
@Composable fun DailyScreen(
    initialDate: LocalDate,
    p: Preferences,
    snapshot: HealthSnapshot,
    back: () -> Unit,
    openMetric: (Metric, LocalDate) -> Unit = { _, _ -> }
) {
    var currentDate by rememberSaveable { mutableStateOf(initialDate.toString()) }
    val date = LocalDate.parse(currentDate)
    val today = LocalDate.now()
    val day = snapshot.days.firstOrNull { it.date == date }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PulseTokens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Navigation Bar
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconAction("back", "Back", onClick = back)
                Spacer(Modifier.width(8.dp))
                Label("DAILY ARCHIVE")
            }
            if (date != today) {
                SmallAction("Today") { currentDate = today.toString() }
            }
        }

        // Date Bar with Previous / Next Controls
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconAction("back", "Previous day") {
                currentDate = date.minusDays(1).toString()
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    date.year.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconAction("next", "Next day", enabled = date < today) {
                currentDate = date.plusDays(1).coerceAtMost(today).toString()
            }
        }

        // Summary Hero Card: Today's High-Level Vitals
        PulseCard(Modifier.reveal(2, scale = .98f).fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val stepsVal = day?.value(Metric.STEPS)
                    val goal = p.stepGoal.toDouble()
                    val pct = if (stepsVal != null) ((stepsVal / goal) * 100).toInt() else 0
                    Label("DAILY SUMMARY")
                    Text("${valueText(stepsVal, Metric.STEPS)} steps", fontSize = 26.sp, fontWeight = FontWeight.Normal)
                    Text(
                        if (stepsVal == null) "No steps logged" else "$pct% of ${number(goal)} goal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val sleepVal = day?.value(Metric.SLEEP)
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Label("SLEEP")
                    Text(valueText(sleepVal, Metric.SLEEP), fontSize = 22.sp)
                    Label(if (sleepVal == null) "No sleep record" else "Rest duration")
                }
            }
        }

        if (snapshot.mode == DataMode.GOOGLE_HEALTH) {
            var extra by rememberSaveable { mutableStateOf(false) }
            SmallAction(if (extra) "Hide additional metrics" else "More health records") { extra = !extra }
            val shown = setOf(Metric.STEPS, Metric.DISTANCE, Metric.ACTIVE, Metric.CALORIES, Metric.HEART, Metric.RESTING_HEART,
                Metric.HRV, Metric.SLEEP, Metric.STRESS, Metric.BATTERY)
            if (extra) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (GoogleHealthMetrics.metrics - shown).sortedBy { it.label }.forEach { metric ->
                    val reading = day?.readings?.get(metric)
                    PulseCard(Modifier.fillMaxWidth().clickable { openMetric(metric, date) }) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(metric.label, Modifier.weight(1f))
                            Text(when {
                                reading?.value != null -> "${valueText(reading.value, metric, p.imperial)} ${metric.unit}"
                                !day?.recordDetails?.get(metric).isNullOrEmpty() -> "View records"
                                reading?.state == DataState.UNSUPPORTED -> "Unavailable"
                                metric !in snapshot.available -> "Permission needed"
                                else -> "No record"
                            })
                        }
                    }
                }
            }
        }

        // Section 1: Activity & Movement
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label("ACTIVITY & MOVEMENT")
            listOf(Metric.STEPS, Metric.DISTANCE, Metric.ACTIVE, Metric.CALORIES).forEach { m ->
                val v = day?.value(m)
                PulseCard(
                    Modifier
                        .fillMaxWidth()
                        .clickable { openMetric(m, date) }
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
                            Box(Modifier.size(8.dp).background(if (v != null) Red else MaterialTheme.colorScheme.outline, CircleShape))
                            Column {
                                Text(m.label, style = MaterialTheme.typography.bodyMedium)
                                if (v == null) {
                                    Label(if (m !in snapshot.available) "Unavailable" else "No record")
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (v != null) {
                                Text(
                                    "${valueText(v, m, p.imperial)} ${if (m == Metric.DISTANCE && p.imperial) "mi" else if (m != Metric.SLEEP) m.unit else ""}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Glyph("next", Modifier.size(12.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Section 2: Vitals & Heart
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label("VITALS & HEART")
            listOf(Metric.HEART, Metric.RESTING_HEART, Metric.HRV).forEach { m ->
                val v = day?.value(m)
                PulseCard(
                    Modifier
                        .fillMaxWidth()
                        .clickable { openMetric(m, date) }
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
                            Box(Modifier.size(8.dp).background(if (v != null) Red else MaterialTheme.colorScheme.outline, CircleShape))
                            Column {
                                Text(m.label, style = MaterialTheme.typography.bodyMedium)
                                if (v == null) {
                                    Label(if (m !in snapshot.available) "Unavailable" else "No record")
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (v != null) {
                                Text(
                                    "${valueText(v, m)} ${m.unit}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Glyph("next", Modifier.size(12.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Section 3: Recovery & Rest
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label("RECOVERY & REST")
            listOf(Metric.SLEEP, Metric.STRESS, Metric.BATTERY).forEach { m ->
                val v = day?.value(m)
                PulseCard(
                    Modifier
                        .fillMaxWidth()
                        .clickable { openMetric(m, date) }
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
                            Box(Modifier.size(8.dp).background(if (v != null) Red else MaterialTheme.colorScheme.outline, CircleShape))
                            Column {
                                Text(m.label, style = MaterialTheme.typography.bodyMedium)
                                if (v == null) {
                                    Label(if (m !in snapshot.available) "Unavailable" else "No record")
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (v != null) {
                                Text(
                                    valueText(v, m),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Glyph("next", Modifier.size(12.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Sleep stages breakdown if recorded
        if (day?.sleepStages?.isNotEmpty() == true) {
            PulseCard(
                Modifier
                    .fillMaxWidth()
                    .clickable { openMetric(Metric.SLEEP, date) }
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sleep stages breakdown", style = MaterialTheme.typography.titleMedium)
                    Glyph("next", Modifier.size(12.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SleepStageDots(day.sleepStages, Modifier.fillMaxWidth().height(28.dp))
                day.sleepStages.forEach { (stage, hours) ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stage, style = MaterialTheme.typography.bodySmall)
                        Label(valueText(hours, Metric.SLEEP))
                    }
                }
            }
        }

        // Recorded workouts / sessions
        if (day?.activities?.isNotEmpty() == true) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label("RECORDED ACTIVITIES")
                day.activities.forEach { act ->
                    PulseCard(Modifier.fillMaxWidth()) {
                        Text(act.name, style = MaterialTheme.typography.bodyMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Label("${act.type.replace('_', ' ')} · ${number(act.minutes)} min")
                            act.distanceKm?.let { km ->
                                Text("${valueText(km, Metric.DISTANCE, p.imperial)} ${if (p.imperial) "mi" else "km"}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        Text(
            "Tap any metric to inspect its detailed analysis and historical trend.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(20.dp))
    }
}
