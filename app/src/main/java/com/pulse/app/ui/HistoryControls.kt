package com.pulse.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulse.app.core.Metric
import kotlinx.coroutines.launch

@Composable fun PeriodSegments(current: Int,change:(Int)->Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface).padding(4.dp)) {
        listOf(7 to "7D",30 to "30D",90 to "90D",365 to "1Y").forEach {(days,label)->
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(7.dp))
                .background(if(current==days)MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface)
                .clickable {change(days)}.semantics {selected=current==days;role=Role.Tab},contentAlignment=Alignment.Center) {
                Text(label,style=MaterialTheme.typography.bodySmall,fontWeight=FontWeight.Medium,color=if(current==days)MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
@Composable fun MetricTabs(current: Metric,change:(Metric)->Unit) {
    val scroll=rememberScrollState();val scope=rememberCoroutineScope()
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(scroll),horizontalArrangement=Arrangement.spacedBy(18.dp)) {
            listOf(Metric.STEPS,Metric.SLEEP,Metric.HEART,Metric.STRESS,Metric.BATTERY,Metric.DISTANCE,Metric.RESTING_HEART,Metric.HRV).forEach {m->
                Column(Modifier.heightIn(min=48.dp).clickable {change(m)}.semantics {selected=current==m;role=Role.Tab},verticalArrangement=Arrangement.Center) {
                    Text(m.label,style=MaterialTheme.typography.bodyMedium,fontWeight=if(current==m)FontWeight.Medium else FontWeight.Normal,color=if(current==m)MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp));Box(Modifier.width(18.dp).height(2.dp).background(if(current==m)Red else MaterialTheme.colorScheme.background))
                }
            }
        }
        IconAction("next","More metrics"){scope.launch {scroll.animateScrollTo(if(scroll.value>=scroll.maxValue)0 else (scroll.value+280).coerceAtMost(scroll.maxValue))}}
    }
}
