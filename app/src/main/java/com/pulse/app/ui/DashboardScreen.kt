package com.pulse.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.semantics.*
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
@Composable fun HomeScreen(
    vm: PulseViewModel,
    p: Preferences,
    snapshot: HealthSnapshot,
    settings: ()->Unit,
    metricDetail: (Metric, LocalDate)->Unit,
    daily: (LocalDate)->Unit
) {
    val menu = LocalPulseMenu.current
    val modes by vm.modes.collectAsStateWithLifecycle()
    val mode = modes.active
    val layout = mode.widgets
    val editing by vm.editing.collectAsStateWithLifecycle()
    val undo by vm.canUndo.collectAsStateWithLifecycle()
    val redo by vm.canRedo.collectAsStateWithLifecycle()
    var selected by rememberSaveable {mutableStateOf<String?>(null)}
    var configure by rememberSaveable {mutableStateOf(false)}
    var newWidgetId by rememberSaveable { mutableStateOf<String?>(null) }
    var showModes by rememberSaveable { mutableStateOf(false) }
    var reset by rememberSaveable { mutableStateOf(false) }
    fun startAdd() { if (layout.size < 100) newWidgetId = UUID.randomUUID().toString() }
    var viewportTop by remember {mutableFloatStateOf(0f)}
    var viewportHeight by remember {mutableFloatStateOf(0f)}
    val scroll=rememberScrollState()
    val haptics=LocalHapticFeedback.current
    val motionEnabled = LocalAnimations.current
    val entryOffset = with(LocalDensity.current) { 22.dp.roundToPx() }
    val today=LocalDate.now()
    val greetingName=p.displayName.takeIf{it.isNotBlank()}?.let{", $it"} ?: ""
    val current=snapshot.days.firstOrNull {it.date==today}
    BackHandler(editing && !configure && newWidgetId == null && !reset) { vm.edit(false) }
    LaunchedEffect(editing) {if(!editing) {selected=null;configure=false;newWidgetId=null}}
    LaunchedEffect(mode.id) { if (!editing) scroll.scrollTo(0) }
    LaunchedEffect(selected) {if(selected!=null && layout.firstOrNull()?.id==selected)scroll.scrollTo(0)}
    fun beginEdit() {if(!editing && p.haptics)haptics.performHapticFeedback(HapticFeedbackType.LongPress);vm.edit(true)}

    RefreshSurface(snapshot.loading,{vm.refresh()},!editing,showIndicator=false) { pull ->
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.reveal().fillMaxWidth().padding(start=PulseTokens.screenPadding,end=8.dp,top=4.dp,bottom=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            PulseSwap(editing, Modifier.weight(1f), label = "Studio header") { studio ->
            if (studio) Column {
                Text("PULSE Studio", style = MaterialTheme.typography.titleMedium)
                MetaText(mode.name)
            } else BrandHeader(loading = snapshot.loading, pulled = pull)
            }
            PulseSwap(editing, label = "Studio actions") { studio ->
                Row { if (studio) SmallAction("Done") { vm.edit(false) } else {
                    IconAction("edit", "Edit dashboard", onClick = ::beginEdit)
                    if (menu == null) IconAction("settings", "Settings", onClick = settings)
                    else menu { showModes = true }
                } }
            }
        }
        PulseSwap(editing, Modifier.fillMaxWidth(), label = "Studio toolbar", distance = 14) { studio ->
            if (studio) Column(Modifier.foregroundSemantics(editing)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconAction("back", "Undo", undo, vm::undo)
                    IconAction("next", "Redo", redo, vm::redo)
                    IconAction("add", "Add widget", layout.size < 100) { startAdd() }
                    SmallAction("Reset") { reset = true }
                }
                Text("One finger to move · two fingers to scroll", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodySmall)
            } else Row(Modifier.reveal(2).padding(horizontal = PulseTokens.screenPadding)) {
                ModeSwitcher(mode) { showModes = true }
            }
        }
        Column(Modifier.weight(1f).onGloballyPositioned {viewportTop=it.positionInRoot().y;viewportHeight=it.size.height.toFloat()}
            .editScroll(editing,scroll).verticalScroll(scroll,enabled=!editing).semantics {
                if(editing) customActions=listOf(CustomAccessibilityAction("Scroll dashboard down") {scroll.dispatchRawDelta(400f);true},CustomAccessibilityAction("Scroll dashboard up") {scroll.dispatchRawDelta(-400f);true})
            }.padding(horizontal=PulseTokens.screenPadding),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(!editing) {
                Row(Modifier.reveal(1).fillMaxWidth().padding(top=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(when(LocalTime.now().hour){in 5..11->"Good morning";in 12..17->"Good afternoon";else->"Good evening"}+greetingName,modifier=Modifier.weight(1f),maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,style=MaterialTheme.typography.headlineMedium)
                    MetaText(today.format(DateTimeFormatter.ofPattern("d MMM")))
                }
                Box(Modifier.reveal(3)) { CompactSourceStatus(snapshot,p.mode){vm.refresh()} }
            }
            SyncFeedback(snapshot)
            val motion = motionEnabled && !editing
            AnimatedContent(mode, contentKey = { it.id }, transitionSpec = {
                if (motion) (fadeIn(tween(285, delayMillis = 28, easing = PulseMotion.Arrive)) +
                    slideInVertically(PulseMotion.settle(true)) { entryOffset })
                    .togetherWith(fadeOut(PulseMotion.tween(true, 180)) +
                        scaleOut(PulseMotion.tween(true, 220), targetScale = .975f))
                    .using(SizeTransform(clip = false) { _, _ -> PulseMotion.settle(true) })
                else (EnterTransition.None togetherWith ExitTransition.None).using(null)
            }, label = "Dashboard mode") { panel ->
                val isActive = panel.id == mode.id
                Column(Modifier.foregroundSemantics(isActive), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (panel.widgets.isEmpty()) {
                        Text("Your dashboard is empty.")
                        PulseButton("Add widget", enabled = isActive) { vm.edit(true); startAdd() }
                    }
                    StudioGrid(
                        layout = panel.widgets, editing = editing && isActive, selected = selected, scroll = scroll,
                        viewportTop = viewportTop, viewportHeight = viewportHeight,
                        select = { selected = it }, beginEdit = ::beginEdit,
                        commit = { vm.commit(panel.id, it) }, configure = { id -> selected = id; configure = true },
                        openMetric = { metricDetail(it, today) }, interactive = isActive, haptic = {
                            if (p.haptics) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        }
                    ) { w -> MetricWidget(w, p, current, snapshot.days, snapshot.loading, snapshot) }
                }
            }
            if(!editing && mode.showObservations) {
                val summary=remember(snapshot.days){HistoryMath.compare(snapshot.days,today,7,Metric.STEPS)}
                HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.5f))
                Column(Modifier.reveal(6).padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text("This week",style=MaterialTheme.typography.titleMedium)
                    Text(if(summary.change!=null)"${number(abs(summary.change!!))} ${if(summary.change!!>=0)"more"else"fewer"} steps than the preceding 7 days."else"${summary.valid} of 7 days recorded. More data is needed for a comparison.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    }
    if (showModes && !editing) ModeSheet(modes, vm) { showModes = false }
    if (reset) PulseConfirmDialog(onDismiss = { reset = false }, title = { Text("Reset ${mode.name}?") },
        text = { Text("Replace this dashboard with ${mode.preset?.let { "the ${it.title} preset" } ?: "an empty layout"}. You can undo the reset in Studio.") },
        confirmLabel = "Reset", onConfirm = { vm.resetLayout(mode.id); reset = false })
    newWidgetId?.let { id -> WidgetSheet(Widget(id,Metric.STEPS),p,snapshot,{newWidgetId=null}, {next ->
        vm.commit(mode.id, AdaptiveFlow.pack(listOf(next)+layout));selected=next.id;newWidgetId=null
    },{newWidgetId=null},isNew=true) }
    if(configure)layout.firstOrNull {it.id==selected}?.let {w->WidgetSheet(
        w,
        p,
        snapshot,
        {configure=false},
        {next->vm.commit(mode.id, AdaptiveFlow.configure(layout,w.id,next))},
        {vm.commit(mode.id, AdaptiveFlow.pack(layout.filterNot {it.id==w.id}));configure=false;selected=null},
        duplicate = if (layout.size < 100) { next ->
            val copy = next.copy(id = UUID.randomUUID().toString())
            val position = layout.indexOfFirst { it.id == w.id } + 1
            vm.commit(mode.id, AdaptiveFlow.pack(layout.toMutableList().apply { add(position, copy) }))
            selected = copy.id
            configure = false
        } else null
    )}
}

fun DataMode.displayName()=when(this){DataMode.NONE->"No data source";DataMode.HEALTH_CONNECT->"Health Connect";DataMode.GARMIN->"Garmin";DataMode.GOOGLE_HEALTH->"Google Health"}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun CompactSourceStatus(snapshot: HealthSnapshot,mode: DataMode,refresh:()->Unit) {
    var detail by rememberSaveable {mutableStateOf(false)}
    val time=snapshot.synced?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("HH:mm"))
    val state=when {mode==DataMode.NONE->"Connect a source in Settings";snapshot.loading->"Syncing…";snapshot.error!=null->"Sync needs attention";time!=null->"Synced $time";else->"Not synchronized"}
    Row(Modifier.fillMaxWidth().heightIn(min=36.dp).clickable {detail=true},verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        Text("●",fontSize=8.sp,color=if(snapshot.error!=null)Red else MaterialTheme.colorScheme.onSurfaceVariant)
        PulseSwap("${mode.displayName()} · $state", Modifier.weight(1f), label = "Data source status") {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
