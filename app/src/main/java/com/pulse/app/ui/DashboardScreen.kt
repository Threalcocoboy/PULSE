package com.pulse.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.app.PulseViewModel
import com.pulse.app.core.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HomeScreen(vm: PulseViewModel,p: Preferences,snapshot: HealthSnapshot,settings:()->Unit,daily:(LocalDate)->Unit) {
    val layout by vm.layout.collectAsStateWithLifecycle()
    val editing by vm.editing.collectAsStateWithLifecycle()
    val undo by vm.canUndo.collectAsStateWithLifecycle()
    val redo by vm.canRedo.collectAsStateWithLifecycle()
    var selected by rememberSaveable {mutableStateOf<String?>(null)}
    var configure by rememberSaveable {mutableStateOf(false)}
    var add by rememberSaveable {mutableStateOf(false)}
    var viewportTop by remember {mutableFloatStateOf(0f)}
    var viewportHeight by remember {mutableFloatStateOf(0f)}
    val scroll=rememberScrollState()
    val haptics=LocalHapticFeedback.current
    val today=LocalDate.now()
    val current=snapshot.days.firstOrNull {it.date==today}
    LaunchedEffect(editing) {if(!editing) {selected=null;configure=false}}
    fun beginEdit() {if(!editing && p.haptics)haptics.performHapticFeedback(HapticFeedbackType.LongPress);vm.edit(true)}

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start=PulseTokens.screenPadding,end=8.dp,top=4.dp,bottom=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            if(editing) Text("PULSE Studio",style=MaterialTheme.typography.titleMedium) else DotWordmark()
            Row {if(editing)SmallAction("Done"){vm.edit(false)}else {IconAction("edit","Edit dashboard"){vm.edit(true)};IconAction("settings","Settings",onClick=settings)}}
        }
        if(editing) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
            SmallAction("Undo",undo,vm::undo);SmallAction("Redo",redo,vm::redo)
            SmallAction("+ Add"){add=true};SmallAction("Configure",selected!=null){configure=true};SmallAction("Reset",onClick=vm::resetLayout)
        }
        Column(Modifier.weight(1f).onGloballyPositioned {viewportTop=it.positionInRoot().y;viewportHeight=it.size.height.toFloat()}
            .verticalScroll(scroll).padding(horizontal=PulseTokens.screenPadding),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(!editing) {
                Row(Modifier.fillMaxWidth().padding(top=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(when(LocalTime.now().hour){in 5..11->"Good morning";in 12..17->"Good afternoon";else->"Good evening"},style=MaterialTheme.typography.headlineMedium)
                    MetaText(today.format(DateTimeFormatter.ofPattern("d MMM")))
                }
                CompactSourceStatus(snapshot,p.mode){vm.refresh()}
            }
            if(snapshot.loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp),color=Red)
            if(layout.isEmpty()) {Text("Your dashboard is empty.");PulseButton("Add widget"){vm.edit(true);add=true}}
            StudioGrid(layout,editing,selected,scroll,viewportTop,viewportHeight,
                select={selected=it},beginEdit=::beginEdit,commit=vm::commit,
                configure={selected=it;configure=true},open={daily(today)}) {w->MetricWidget(w,p,current,snapshot.days,snapshot.loading)}
            if(!editing && p.insights) {
                val summary=remember(snapshot.days){HistoryMath.compare(snapshot.days,today,7,Metric.STEPS)}
                HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.5f))
                Column(Modifier.padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text("This week",style=MaterialTheme.typography.titleMedium)
                    Text(if(summary.change!=null)"${number(abs(summary.change!!))} ${if(summary.change!!>=0)"more"else"fewer"} steps than the preceding 7 days."else"${summary.valid} of 7 days recorded. More data is needed for a comparison.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    if(add) ModalBottomSheet(onDismissRequest={add=false},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            SectionTitle("Add widget")
            Metric.entries.forEach {metric->TextButton({if(layout.size<100){val w=Widget(UUID.randomUUID().toString(),metric);vm.commit(AdaptiveFlow.pack(layout+w));selected=w.id;add=false}},Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(metric.label);Glyph("add")}
            }}
        }
    }
    if(configure)layout.firstOrNull {it.id==selected}?.let {w->WidgetSheet(w,{configure=false},{next->
        vm.commit(AdaptiveFlow.pack(layout.map {if(it.id==w.id)next else it},w.id,next.column,next.row))
    },{vm.commit(AdaptiveFlow.pack(layout.filterNot {it.id==w.id}));configure=false;selected=null})}
}

fun DataMode.displayName()=when(this){DataMode.DEMO->"Demo";DataMode.HEALTH_CONNECT->"Health Connect";DataMode.GARMIN->"Garmin"}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CompactSourceStatus(snapshot: HealthSnapshot,mode: DataMode,refresh:()->Unit) {
    var detail by rememberSaveable {mutableStateOf(false)}
    val time=snapshot.synced?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("HH:mm"))
    val state=when {mode==DataMode.DEMO->"Sample data";snapshot.loading->"Syncing…";snapshot.error!=null->"Sync needs attention";time!=null->"Synced $time";else->"Not synchronized"}
    Row(Modifier.fillMaxWidth().heightIn(min=36.dp).clickable {detail=true},verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        Text("●",fontSize=8.sp,color=if(snapshot.error!=null)Red else MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${mode.displayName()} · $state",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(1f))
        Glyph("next",Modifier.size(12.dp),MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if(detail)ModalBottomSheet(onDismissRequest={detail=false}) {
        Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            SectionTitle(mode.displayName())
            Text(snapshot.status,style=MaterialTheme.typography.bodyMedium)
            snapshot.synced?.let{MetaText("Last sync ${it.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"))}")}
            Text("Available: "+snapshot.available.filterNot {it in setOf(Metric.GOAL,Metric.WEEKLY,Metric.TREND)}.joinToString {it.label},style=MaterialTheme.typography.bodySmall)
            snapshot.error?.let{Text(it,color=Red,style=MaterialTheme.typography.bodyMedium)}
            PulseButton("Refresh",enabled=!snapshot.loading,onClick=refresh)
            Spacer(Modifier.height(16.dp))
        }
    }
}
