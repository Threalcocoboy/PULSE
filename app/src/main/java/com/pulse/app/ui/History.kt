package com.pulse.app.ui

import androidx.compose.foundation.*
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
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlinx.coroutines.launch

@Composable fun HistoryScreen(p: Preferences,snapshot: HealthSnapshot,daily:(LocalDate)->Unit) {
    var count by rememberSaveable {mutableIntStateOf(7)};var metricName by rememberSaveable {mutableStateOf(Metric.STEPS.name)}
    var endDate by rememberSaveable {mutableStateOf(LocalDate.now().toString())};var selected by rememberSaveable {mutableIntStateOf(-1)}
    var calendar by rememberSaveable {mutableStateOf(YearMonth.now().toString())}
    val metric=Metric.valueOf(metricName); val end=LocalDate.parse(endDate)
    val haptics=LocalHapticFeedback.current
    val start=if(count==365) end.minusMonths(11).withDayOfMonth(1) else end.minusDays(count-1L)
    val days=remember(snapshot.days,start,end){HistoryMath.unique(snapshot.days).filter {it.date in start..end}}
    val dateValues=remember(days,count,metric,start,end) {
        if(count==365) (0L..11L).map {ago->val month=YearMonth.from(start.plusMonths(ago)); month.atDay(1) to HistoryMath.aggregate(days.filter {YearMonth.from(it.date)==month},metric)}
        else (0 until count).map {i->val date=start.plusDays(i.toLong());date to days.firstOrNull {it.date==date}?.value(metric)}
    }
    val summary=remember(snapshot.days,end,count,metric){HistoryMath.compare(snapshot.days,end,if(count==365) java.time.temporal.ChronoUnit.DAYS.between(start,end).toInt()+1 else count,metric)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PulseTokens.screenPadding),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        SectionTitle("History")
        if(LocalDemo.current)Label("Demo data")
        PeriodSegments(count){count=it;selected=-1}
        MetricTabs(metric){metricName=it.name;selected=-1}
        PulseCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                IconAction("back","Previous period"){endDate=(if(count==365)end.minusYears(1)else end.minusDays(count.toLong())).toString();selected=-1}
                Text("${start.format(DateTimeFormatter.ofPattern("d MMM yy"))} — ${end.format(DateTimeFormatter.ofPattern("d MMM yy"))}",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,textAlign=TextAlign.Center)
                IconAction("next","Next period",end<LocalDate.now()){endDate=(if(count==365)end.plusYears(1)else end.plusDays(count.toLong())).coerceAtMost(LocalDate.now()).toString();selected=-1}
            }
            Label(if(metric.cumulative) "${metric.label.uppercase()} / TOTAL" else "${metric.label.uppercase()} / RECORDED AVERAGE")
            Text(valueText(summary.value,metric,p.imperial),fontSize=36.sp,fontWeight=FontWeight.Normal)
            if(metric!=Metric.SLEEP)Label(if(metric==Metric.DISTANCE && p.imperial)"mi" else metric.unit)
            DotChart(dateValues.map {it.second},Modifier.fillMaxWidth().height(150.dp),selected.takeIf {it in dateValues.indices},{if(selected!=it && p.haptics)haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove);selected=it},bars=metric in setOf(Metric.STEPS,Metric.DISTANCE,Metric.SLEEP),description="${metric.label}, ${summary.valid} recorded days of ${summary.total}. Total or average ${valueText(summary.value,metric,p.imperial)}. Swipe or use next value to inspect.")
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Label(start.format(DateTimeFormatter.ofPattern("d MMM")));Label(end.format(DateTimeFormatter.ofPattern("d MMM")))}
            if(selected in dateValues.indices) {val point=dateValues[selected];Text("${point.first.format(DateTimeFormatter.ofPattern(if(count==365)"MMMM yyyy" else "EEE, d MMM"))} · ${valueText(point.second,metric,p.imperial)} ${if(metric==Metric.SLEEP)""else metric.unit}",color=Red);if(count!=365) SmallAction("Open day") {daily(point.first)}}
            else Label("TOUCH THE DOTS TO EXPLORE")
        }
        Row(Modifier.fillMaxWidth().padding(vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f)){Label("Coverage");Text("${summary.valid} / ${summary.total}",fontSize=22.sp);Label("Recorded days")}
            Column(Modifier.weight(1f)){Label("Previous period");Text(valueText(summary.previous,metric,p.imperial),fontSize=22.sp);Label(if(metric.cumulative)"Total"else"Average")}
        }
        HorizontalDivider(color=MaterialTheme.colorScheme.outline)
        Text(if(summary.change==null) "Not enough recorded data for a complete comparison." else "${valueText(abs(summary.change!!),metric,p.imperial)} ${if(metric==Metric.SLEEP) "" else metric.unit} ${if(summary.change!!>=0) "higher" else "lower"} than the preceding period${summary.percent?.let {" (${String.format(java.util.Locale.getDefault(),"%.1f",abs(it))}%)"} ?: ""}.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Column{Text("The daily archive",style=MaterialTheme.typography.titleLarge);Label(metric.label.uppercase())};Glyph("dots",color=Red)}
        CalendarHeatmap(YearMonth.parse(calendar),metric,p,snapshot.days,{calendar=it.toString()},daily)
        Spacer(Modifier.height(8.dp))
    }
}
@Composable private fun CalendarHeatmap(month: YearMonth,metric: Metric,p: Preferences,days: List<DayData>,change:(YearMonth)->Unit,daily:(LocalDate)->Unit) {
    val values=remember(days,month){HistoryMath.unique(days).filter {YearMonth.from(it.date)==month}.associateBy {it.date}}
    val max=values.values.mapNotNull {it.value(metric)}.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){IconAction("back","Previous month"){change(month.minusMonths(1))};Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")));IconAction("next","Next month",month<YearMonth.now()){change(month.plusMonths(1))}}
        Row(Modifier.fillMaxWidth()){listOf("M","T","W","T","F","S","S").forEach {Box(Modifier.weight(1f),contentAlignment=Alignment.Center){Label(it)}}}
        val offset=month.atDay(1).dayOfWeek.value-1;val weeks=(offset+month.lengthOfMonth()+6)/7
        repeat(weeks){week->Row(Modifier.fillMaxWidth()) {repeat(7){d->
            val day=week*7+d-offset+1
            if(day !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(56.dp))
            else {val date=month.atDay(day);val v=values[date]?.value(metric);val future=date>LocalDate.now()
                Column(Modifier.weight(1f).heightIn(min=56.dp).clip(RoundedCornerShape(8.dp)).clickable(enabled=!future){daily(date)}.padding(vertical=8.dp).semantics(mergeDescendants=true){contentDescription="${date}: ${valueText(v,metric,p.imperial)} ${metric.unit}"},horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(day.toString(),fontSize=12.sp,color=if(future)MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
                    Box(Modifier.size(8.dp).background(if(v==null)MaterialTheme.colorScheme.outline else Red.copy(alpha=(.25+.75*v/max).toFloat().coerceIn(.25f,1f)),CircleShape))
                }
            }
        }}}
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

        if (LocalDemo.current) {
            Label("●  DEMO DATA · NOT LIVE", color = Red)
        }

        // Summary Hero Card: Today's High-Level Vitals
        PulseCard(Modifier.fillMaxWidth()) {
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
                    Label(if (sleepVal == null) "No sleep" else "Rest duration")
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
