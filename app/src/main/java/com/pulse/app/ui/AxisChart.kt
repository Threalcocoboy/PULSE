package com.pulse.app.ui
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import com.pulse.app.core.*
@Composable fun AxisChart(values:List<Double?>,metric:Metric,selected:Int?,choose:(Int)->Unit,bars:Boolean,x:List<Float>?=null,imperial:Boolean=false){val clean=values.map{v->v?.takeIf{it.isFinite()&&it>=0&&(metric !in setOf(Metric.STRESS,Metric.BATTERY,Metric.GOAL)||it<=100)}};val plotted=if(metric==Metric.DISTANCE&&imperial)clean.map{it?.times(.621371)}else clean;val axis=remember(plotted,metric){AxisMath.scale(plotted,metric)}
 Column{Row(Modifier.fillMaxWidth().height(190.dp)){Column(Modifier.width(54.dp).fillMaxHeight(),verticalArrangement=Arrangement.SpaceBetween){axis.ticks.reversed().forEach{Text(AxisMath.label(it,metric),style=MaterialTheme.typography.labelSmall)}};DotChart(plotted,Modifier.weight(1f).fillMaxHeight(),selected,choose,bars,"${metric.label} recorded values, axis ${axis.minimum} to ${axis.maximum}",axis.minimum,axis.maximum,x)};if(values.count{it!=null}!=clean.count{it!=null})Text("Invalid source values omitted",style=MaterialTheme.typography.bodySmall);if(clean.all{it==null})Text("No records in this interval",style=MaterialTheme.typography.bodySmall)}
}
