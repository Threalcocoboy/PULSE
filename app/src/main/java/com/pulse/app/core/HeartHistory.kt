package com.pulse.app.core
import java.time.*
data class HeartSample(val time:Instant,val bpm:Double,val source:String)
object HeartHistory {fun daily(samples:List<HeartSample>,zone:ZoneId,start:LocalDate,end:LocalDate)=samples.filter{it.bpm.isFinite()&&it.bpm>0&&it.time.atZone(zone).toLocalDate() in start..end}.groupBy{it.time.atZone(zone).toLocalDate()}.mapValues{(_,s)->val origin=s.minOf{it.source};s.filter{it.source==origin}.groupBy{it.time}.values.map{it.map{v->v.bpm}.average()}.average()}}
