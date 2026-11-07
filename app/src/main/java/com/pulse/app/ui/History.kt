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
@Composable fun DailyScreen(date: LocalDate,p: Preferences,snapshot: HealthSnapshot,back:()->Unit) {
    val day=snapshot.days.firstOrNull {it.date==date}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically){IconAction("back","Back",onClick=back);Label("DAILY ARCHIVE")}
        SectionTitle(date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),date.year.toString())
        if(LocalDemo.current)Label("Demo data")
        if(day?.sleepStages?.isNotEmpty()==true) {
            Text("Sleep stages",style=MaterialTheme.typography.titleMedium)
            SleepStageDots(day.sleepStages,Modifier.fillMaxWidth().height(38.dp))
            day.sleepStages.forEach{(stage,hours)->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(stage,style=MaterialTheme.typography.bodyMedium);Label(valueText(hours,Metric.SLEEP))}}
        }
        Metric.entries.filterNot {it in setOf(Metric.GOAL,Metric.WEEKLY,Metric.TREND,Metric.SLEEP_STAGES)}.forEach {m->PulseCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(m.label);Text(valueText(day?.value(m),m,p.imperial)+if(day?.value(m)!=null && m!=Metric.SLEEP) " ${if(m==Metric.DISTANCE && p.imperial)"mi"else m.unit}" else "",color=if(day?.value(m)!=null)MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)}
            if(day?.value(m)==null) Label(if(m !in snapshot.available)"UNAVAILABLE FROM THIS SOURCE"else"NO RECORD FOR THIS DAY")
        }}
        if(day?.activities?.isNotEmpty()==true) {
            Text("Recorded activities",style=MaterialTheme.typography.titleMedium)
            day.activities.forEach{activity->Column(verticalArrangement=Arrangement.spacedBy(3.dp)){
                Text(activity.name,style=MaterialTheme.typography.bodyMedium)
                Label("${activity.localStart.takeLast(8)} local · ${activity.type.replace('_',' ')} · ${number(activity.minutes)} min")
            }}
        }
        Text(if(day?.sleepStages?.isNotEmpty()==true)"Sleep stages are duration totals, not a reconstructed timeline."else"Daily summaries only. Exact health event times and sleep stages are unavailable.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
    }
}
