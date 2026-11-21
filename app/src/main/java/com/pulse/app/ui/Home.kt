package com.pulse.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
    Metric.HEART -> "Recorded heart rate averages; samples are available in History"
    Metric.RESTING_HEART -> "Baseline cardiovascular recovery indicator"
    Metric.SLEEP -> "Total sleep duration and nightly rest tracking"
    Metric.SLEEP_STAGES -> "Duration breakdown across deep, light and REM stages"
    Metric.BATTERY -> "Garmin recovery and energy score on a 0–100 scale"
    Metric.STRESS -> "Autonomic nervous system strain on a 0–100 scale"
    Metric.HRV -> "Heart rate variability overnight trend and baseline"
    Metric.ACTIVE -> "Recorded exercise minutes; source definitions may differ"
    Metric.CALORIES -> "Total daily active and resting energy expenditure"
    Metric.ACTIVE_CALORIES -> "Recorded active energy expenditure"
    Metric.FLOORS -> "Number of floors climbed"
    Metric.HYDRATION -> "Recorded fluid intake"
    Metric.WORKOUT_DURATION -> "Duration of recorded workouts"
    Metric.ACTIVITIES -> "Recorded workout sessions and exercise logs"
    Metric.GOAL -> "All your personal goals and their recorded progress"
    Metric.WEEKLY -> "Total steps accumulated over the last 7 days"
    Metric.TREND -> "Step volume trajectory compared to previous days"
    else -> "Recorded ${metric.label.lowercase()} in ${metric.unit}"
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
@Composable internal fun WidgetSheet(w:Widget,p:Preferences=Preferences(),snapshot:HealthSnapshot=HealthSnapshot(),dismiss:()->Unit,change:(Widget)->Unit,remove:()->Unit,isNew:Boolean=false,duplicate:((Widget)->Unit)?=null) {
 val saver = remember { Saver<Widget, String>(save = { LayoutCodec.encode(listOf(it.copy(column = 0, row = 0))) }, restore = { LayoutCodec.decode(it).singleOrNull() }) }
 var draft by rememberSaveable(w.id, stateSaver = saver){mutableStateOf(w)}
 var tab by rememberSaveable {mutableIntStateOf(0)};var menu by remember{mutableStateOf(false)}
 PulseSheet(dismiss) { close ->
 Column(Modifier.reveal(1).fillMaxWidth().heightIn(max=620.dp).verticalScroll(rememberScrollState()).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(if(isNew)"Add widget" else "Edit widget",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);IconAction("close","Close editor",onClick={ close(dismiss) })}
 BoxWithConstraints(Modifier.fillMaxWidth().height(180.dp),contentAlignment=Alignment.Center) {
 val cell=(maxWidth-24.dp)/4;val actualWidth=(cell+8.dp)*draft.width-8.dp;val actualHeight=(cell+8.dp)*draft.height-8.dp
 val scale=minOf(1f,170f/actualHeight.value)
 Box(Modifier.requiredSize(actualWidth,actualHeight).graphicsLayer{scaleX=scale;scaleY=scale}){MetricWidget(draft,p,snapshot.days.firstOrNull{it.date==LocalDate.now()},snapshot.days,snapshot.loading,snapshot)}
 }
 Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {(if(isNew)listOf("Metric","Shape & size")else listOf("Metric","Shape & size","Display")).forEachIndexed{i,t->FilterChip(tab==i,{tab=i},label={Text(t)})}}
 PulseSwap(tab, Modifier.fillMaxWidth().heightIn(min = 82.dp), label = "Widget settings tab",
     direction = { from, to -> to.compareTo(from) }) { shown -> when(shown){
 0->Column {OutlinedButton({menu=true},Modifier.fillMaxWidth()){Text(draft.metric.label)};DropdownMenu(menu,{menu=false},modifier=Modifier.heightIn(max=300.dp)){Metric.entries.filterNot { it in setOf(Metric.HEART_ZONES, Metric.WEEKLY_ACTIVE) }.forEach{m->DropdownMenuItem(text={Text(m.label)},onClick={draft=draft.copy(metric=m);menu=false})}};Text(metricDescription(draft.metric),style=MaterialTheme.typography.bodySmall)}
 1->Column {Row(Modifier.horizontalScroll(rememberScrollState())){WidgetShape.entries.forEach{shape->FilterChip(draft.shape==shape,{val n=draft.copy(shape=shape);val size=n.allowedSizes().minBy{abs(it.columns-draft.width)+abs(it.rows-draft.height)};draft=n.copy(width=size.columns,height=size.rows)},label={Text(shape.name.lowercase())})}};FlowRow{draft.allowedSizes().forEach{f->FilterChip(draft.width==f.columns&&draft.height==f.rows,{draft=draft.copy(width=f.columns,height=f.rows)},label={Text("${f.columns} × ${f.rows}")})}}}
 else->Column{listOf("Details" to draft.detailed,"Goal indicator" to draft.showGoal,"Trend" to draft.showTrend).forEachIndexed{i,v->Row(Modifier.fillMaxWidth().height(48.dp),verticalAlignment=Alignment.CenterVertically){Text(v.first,Modifier.weight(1f));Switch(v.second,{value->draft=when(i){0->draft.copy(detailed=value);1->draft.copy(showGoal=value);else->draft.copy(showTrend=value)}})}}}
 }}
 FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
  PulseButton(if(isNew)"Add" else "Save widget"){ close { change(draft); dismiss() } }
  if (!isNew) TextButton({ close(remove) }) { Text("Remove", color = Red) } else TextButton({ close(dismiss) }) { Text("Cancel") }
  if (duplicate != null) TextButton({ close { duplicate(draft); dismiss() } }) { Text("Duplicate") }
 }
 Spacer(Modifier.height(12.dp))
 }
 }
}
