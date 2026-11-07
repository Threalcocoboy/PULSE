package com.pulse.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import com.pulse.app.core.*
import java.time.LocalDate

@Composable internal fun MetricWidget(w: Widget,p: Preferences,current: DayData?,days: List<DayData>,loading: Boolean) {
    val value=when(w.metric) {
        Metric.WEEKLY->HistoryMath.aggregate(days.filter {it.date in LocalDate.now().minusDays(6)..LocalDate.now()},Metric.STEPS)
        Metric.GOAL->current?.value(Metric.STEPS)?.div(p.stepGoal)?.times(100)
        else->current?.value(w.metric)
    }
    val scale=LocalDensity.current.fontScale
    val compact=w.height==1 || !w.detailed || scale>=1.4f
    val circular=w.shape==WidgetShape.CIRCLE
    val shape=when(w.shape){WidgetShape.CIRCLE->CircleShape;WidgetShape.PILL->RoundedCornerShape(50);else->RoundedCornerShape(PulseTokens.corner)}
    BoxWithConstraints(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
        val frame=if(circular)Modifier.size(minOf(maxWidth,maxHeight))else Modifier.fillMaxSize()
        BoxWithConstraints(frame.clip(shape).background(MaterialTheme.colorScheme.surface).padding(if(w.width==1)8.dp else PulseTokens.cardPadding)) {
            val contentWidth=maxWidth
            val featured=w.metric==Metric.STEPS && w.width==4 && w.height>=2
            if(featured) {
                Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text("Steps",style=MaterialTheme.typography.bodyMedium)
                    Row(Modifier.weight(1f).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(.6f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            val count=if(loading && value==null)"···"else number(value)
                            val font=minOf(44f,contentWidth.value*.58f/(count.length*.56f*scale)).coerceAtLeast(23f).sp
                            Text(count,fontSize=font,fontWeight=FontWeight.Normal,maxLines=1)
                            if(w.showGoal)Text("of ${number(p.stepGoal.toDouble())} goal",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        BoxWithConstraints(Modifier.weight(.4f).fillMaxHeight(),contentAlignment=Alignment.Center) {
                            val ring=minOf(maxWidth,maxHeight,126.dp)
                            Box(Modifier.size(ring),contentAlignment=Alignment.Center) {
                                DotRing((value ?: 0.0)/p.stepGoal,Modifier.fillMaxSize())
                                Text(if(value==null)"—"else"${(value/p.stepGoal*100).toInt()}%",fontSize=if(scale>1.3f)17.sp else 22.sp)
                            }
                        }
                    }
                    Label(if(value==null)"No recorded steps"else if(value>=p.stepGoal)"Daily goal reached"else"${number((p.stepGoal-value).coerceAtLeast(0.0))} to your goal")
                    if(w.height>=3 && w.showTrend && scale<1.4f) {
                        DotChart(days.takeLast(7).map{it.value(Metric.STEPS)},Modifier.fillMaxWidth().height(36.dp),bars=true,description="Daily steps over the last seven days")
                        Label("Last 7 days")
                    }
                }
            } else Column(Modifier.fillMaxSize(),horizontalAlignment=if(circular)Alignment.CenterHorizontally else Alignment.Start,
                verticalArrangement=if(compact || circular)Arrangement.spacedBy(6.dp,Alignment.CenterVertically)else Arrangement.SpaceBetween) {
                Text(w.metric.label,style=if(w.width==1)MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium,maxLines=2,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(if(loading && value==null)"···"else valueText(value,w.metric,p.imperial),fontSize=when {w.width==1->18.sp;scale>=1.4f->22.sp;else->32.sp},fontWeight=FontWeight.Normal,maxLines=1)
                    if(value!=null && w.metric !in setOf(Metric.SLEEP,Metric.SLEEP_STAGES) && w.width>1)Text(if(w.metric==Metric.DISTANCE && p.imperial)"mi"else w.metric.unit,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(bottom=3.dp))
                }
                if(!compact && w.width>=2) {
                    if(w.metric==Metric.SLEEP_STAGES && current?.sleepStages?.isNotEmpty()==true)SleepStageDots(current.sleepStages,Modifier.fillMaxWidth().height(26.dp))
                    else if(value!=null && w.showTrend)DotChart(days.takeLast(7).map{it.value(w.metric)},Modifier.fillMaxWidth().height(30.dp),bars=w.metric in setOf(Metric.SLEEP,Metric.ACTIVE,Metric.WEEKLY),description="Last seven daily ${w.metric.label} readings")
                    if(value==null)Label(when(current?.readings?.get(w.metric)?.state){DataState.PERMISSION_DENIED->"Permission needed";DataState.UNSUPPORTED->"Unavailable from source";else->"No recorded data"})
                    else if(w.metric==Metric.SLEEP && w.showGoal)Label("${(value/p.sleepGoal*100).toInt()}% of ${p.sleepGoal}h goal")
                    else if(w.metric==Metric.WEEKLY)Label("Last 7 days")
                    else if(w.metric==Metric.GOAL && w.showGoal)Label("${number(p.stepGoal.toDouble())} step goal")
                }
            }
        }
    }
}
