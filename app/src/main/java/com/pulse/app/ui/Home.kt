package com.pulse.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.app.PulseViewModel
import com.pulse.app.core.*
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun WidgetSheet(w: Widget,dismiss:()->Unit,change:(Widget)->Unit,remove:()->Unit) {
    ModalBottomSheet(onDismissRequest=dismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) { Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        SectionTitle("Edit widget",w.metric.label)
        Label("METRIC");Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){Metric.entries.forEach {m->FilterChip(w.metric==m,{change(w.copy(metric=m))},label={Text(m.label)})}}
        Label("SHAPE");Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){WidgetShape.entries.forEach {s->FilterChip(w.shape==s,{val next=w.copy(shape=s);val size=next.allowedSizes().minBy {abs(it.columns-w.width)+abs(it.rows-w.height)};change(next.copy(width=size.columns,height=size.rows))},label={Text(s.name.lowercase().replaceFirstChar(Char::titlecase))})}}
        Label("SIZE");Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){w.allowedSizes().forEach {s->FilterChip(w.width==s.columns && w.height==s.rows,{change(w.copy(width=s.columns,height=s.rows))},label={Text("${s.columns}×${s.rows}")})}}
        SettingToggle("Detailed presentation",w.detailed){change(w.copy(detailed=it))}
        if(w.metric in setOf(Metric.STEPS,Metric.SLEEP,Metric.GOAL)) SettingToggle("Show goal",w.showGoal){change(w.copy(showGoal=it))}
        if(w.metric!=Metric.STEPS && w.metric!=Metric.SLEEP_STAGES) SettingToggle("Show recent trend",w.showTrend){change(w.copy(showTrend=it))}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){SmallAction("Remove widget",onClick=remove);PulseButton("Done",onClick=dismiss)}
        Spacer(Modifier.height(16.dp))
    } }
}
