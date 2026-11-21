package com.pulse.app.core
import kotlin.math.*
data class ChartAxis(val minimum:Double,val maximum:Double,val ticks:List<Double>){fun fraction(v:Double)=((v-minimum)/(maximum-minimum)).coerceIn(0.0,1.0)}
object AxisMath {
 fun scale(values:List<Double?>,metric:Metric):ChartAxis {
 val data=values.filterNotNull().filter{it.isFinite()};val zero=metric.cumulative||metric in setOf(Metric.SLEEP,Metric.SLEEP_STAGES,Metric.WEEKLY,Metric.TIME_IN_BED,Metric.SLEEP_PERIOD,Metric.SLEEP_AWAKE)
 if(metric in setOf(Metric.STRESS,Metric.BATTERY,Metric.GOAL))return ChartAxis(0.0,100.0,listOf(0.0,25.0,50.0,75.0,100.0))
 if(data.isEmpty())return ChartAxis(0.0,1.0,listOf(0.0,.25,.5,.75,1.0))
 var low=if(zero)0.0 else data.min();var high=data.max();val pad=if(high==low)max(1.0,abs(high)*.1)else (high-low)*.08
 if(!zero)low=max(0.0,low-pad);high+=pad
 val raw=(high-low)/4;val power=10.0.pow(floor(log10(raw.coerceAtLeast(.0001))));val unit=raw/power
 val step=(when{unit<=1->1.0;unit<=2->2.0;unit<=2.5->2.5;unit<=5->5.0;else->10.0})*power
 val a=floor(low/step)*step;val b=ceil(high/step)*step
 return ChartAxis(a,b,(0..((b-a)/step).roundToInt()).map{a+it*step})
 }
 fun label(v:Double,m:Metric):String=when{m in setOf(Metric.SLEEP,Metric.SLEEP_STAGES)->"${(v*60).roundToInt()/60}h ${((v*60).roundToInt()%60)}m";abs(v)>=1000->"${java.text.DecimalFormat("0.#").format(v/1000)}k";else->java.text.DecimalFormat("0.##").format(v)}
}
