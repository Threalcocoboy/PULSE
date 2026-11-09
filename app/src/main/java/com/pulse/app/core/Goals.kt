package com.pulse.app.core
import java.time.LocalDate
object Goals {
 val supported=setOf(Metric.STEPS,Metric.SLEEP,Metric.DISTANCE,Metric.ACTIVE,Metric.ACTIVITIES)
 fun available(mode:DataMode)=supported
 fun target(m:Metric,p:Preferences)=when(m){Metric.STEPS->p.stepGoal.toDouble();Metric.SLEEP->p.sleepGoal;Metric.DISTANCE->p.distanceGoal;Metric.ACTIVE->p.activeGoal.toDouble();Metric.ACTIVITIES->p.workoutGoal.toDouble();else->1.0}
 fun value(m:Metric,days:List<DayData>,today:LocalDate):Double? {val values=HistoryMath.unique(days).filter{if(m==Metric.ACTIVITIES)it.date in today.with(java.time.DayOfWeek.MONDAY)..today else it.date==today}.mapNotNull{it.readings[m]?.takeIf{v->v.state in setOf(DataState.READY,DataState.STALE)}?.value};return if(values.isEmpty())null else if(m==Metric.ACTIVITIES)values.sum() else values.last()}
}
