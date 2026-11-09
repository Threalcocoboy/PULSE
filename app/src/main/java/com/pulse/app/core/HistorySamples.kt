package com.pulse.app.core
object HistorySamples {
 fun plot(samples:List<HeartSample>,limit:Int=600):List<HeartSample>{require(limit>=4);if(samples.size<=limit)return samples
 val chunks=(limit/4).coerceAtLeast(1);val size=kotlin.math.ceil(samples.size.toDouble()/chunks).toInt()
 return samples.chunked(size).flatMap{listOf(it.first(),it.minBy{v->v.bpm},it.maxBy{v->v.bpm},it.last())}.distinctBy{it.time}.sortedBy{it.time}
 }
}
