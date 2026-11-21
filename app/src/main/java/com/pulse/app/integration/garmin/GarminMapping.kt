package com.pulse.app.integration.garmin

import com.pulse.app.core.*
import org.json.*
import java.time.*

/** Maps only fields published by Garmin's Personal API OpenAPI document (checked 2026-11-05). */
object GarminMapping {
    /** A 90-day summary refresh must not erase detail outside the shorter detail endpoints' windows. */
    fun preserveOutsideDetailWindow(fresh: DayData, old: DayData?, recent: LocalDate, today: LocalDate): DayData {
        if(old==null || old.date!=fresh.date) return fresh
        val metrics=buildSet {
            if(fresh.date<recent) addAll(setOf(Metric.SLEEP,Metric.SLEEP_STAGES,Metric.HRV,Metric.ACTIVITIES,Metric.WORKOUT_DURATION))
            if(fresh.date<today) add(Metric.HEART)
        }
        val retained=metrics.mapNotNull { metric -> old.readings[metric]?.takeIf { it.value != null || old.workoutsRead && metric in setOf(Metric.ACTIVITIES, Metric.WORKOUT_DURATION) }?.let { metric to it } }.toMap()
        return fresh.copy(readings=fresh.readings+retained,
            sleepStages=if(fresh.date<recent) old.sleepStages else fresh.sleepStages,
            activities=if(fresh.date<recent) old.activities else fresh.activities,
            sleepStart=old.sleepStart,sleepEnd=old.sleepEnd,workoutsRead=fresh.date<recent && old.workoutsRead, sleepWakeMinute=old.sleepWakeMinute)
    }
    val supported=setOf(Metric.STEPS,Metric.DISTANCE,Metric.RESTING_HEART,Metric.HEART,Metric.SLEEP,Metric.SLEEP_STAGES,Metric.STRESS,Metric.BATTERY,Metric.HRV,Metric.ACTIVE,Metric.CALORIES,Metric.ACTIVITIES,Metric.WORKOUT_DURATION,Metric.ACTIVE_CALORIES,Metric.FLOORS,Metric.HYDRATION)
    fun number(o: JSONObject?,key: String): Double?=(o?.opt(key) as? Number)?.toDouble()?.takeIf {it.isFinite() && it>=0}
    fun objects(a: JSONArray)= (0 until a.length()).mapNotNull {a.optJSONObject(it)}
    private fun date(raw: String)=runCatching{LocalDate.parse(raw)}.getOrNull()
    fun accountFingerprint(root: Any): String? {
        val data = (root as? JSONObject)?.optJSONArray("data") ?: return null
        val account = objects(data).filter { it.optString("source").equals("garmin", true) }
            .mapNotNull { (it.opt("userProfilePk") as? Number)?.toString() }.distinct().singleOrNull() ?: return null
        return java.security.MessageDigest.getInstance("SHA-256").digest("pulse.garmin.account:$account".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun summaries(root: Any,now: Instant): Map<LocalDate,DayData> {
        val array=(root as? JSONObject)?.optJSONArray("data") ?: throw GarminFailure(GarminError.MALFORMED)
        val days=objects(array).filter {it.optString("source").equals("garmin",true)}.mapNotNull {o->
            val d=date(o.optString("calendarDate")) ?: return@mapNotNull null
            val movement=o.optJSONObject("movement")?.optJSONObject("steps")
            val intensity=o.optJSONObject("intensityMinutes")
            val moderate=number(intensity,"moderate");val vigorous=number(intensity,"vigorous")
            val data=mapOf(Metric.STEPS to number(movement,"value"),Metric.DISTANCE to number(movement,"distanceInMeters")?.div(1000),
                Metric.RESTING_HEART to number(o.optJSONObject("heartRate"),"restingValue")?.takeIf{it>0},
                Metric.CALORIES to number(o.optJSONObject("calories"),"burnedTotal"),
                Metric.ACTIVE_CALORIES to number(o.optJSONObject("calories"),"burnedActive"),
                Metric.FLOORS to number(o.optJSONObject("movement")?.optJSONObject("floorsAscended"),"value"),
                Metric.HYDRATION to number(o.optJSONObject("hydration"),"consumedInFractionalMl")?.div(1000),
                Metric.STRESS to number(o.optJSONObject("stress"),"avgLevel")?.takeIf{it<=100},
                Metric.BATTERY to number(o.optJSONObject("bodyBattery"),"latestValue")?.takeIf{it<=100},
                Metric.ACTIVE to if(moderate==null && vigorous==null)null else (moderate ?: 0.0)+(vigorous ?: 0.0))
            DayData(d,Metric.entries.associateWith {m->val v=data[m];Reading(v,if(m !in supported)DataState.UNSUPPORTED else if(v==null)DataState.EMPTY else DataState.READY,now)})
        }
        if(array.length()>0 && days.isEmpty())throw GarminFailure(GarminError.MALFORMED)
        return days.associateBy {it.date}
    }
    fun sleep(root: Any,days: MutableMap<LocalDate,DayData>,now: Instant) {
        val array=root as? JSONArray ?: throw GarminFailure(GarminError.MALFORMED)
        objects(array).forEach {o->val d=date(o.optString("calendarDate")) ?: return@forEach
            val hours=number(o,"sleepTimeSeconds")?.div(3600)
            val stages=mapOf("Deep" to "deepSleepSeconds","Light" to "lightSleepSeconds","REM" to "remSleepSeconds","Awake" to "awakeSleepSeconds")
                .mapNotNull {(label,field)->number(o,field)?.let{label to it/3600}}.toMap()
            val day=days[d] ?: DayData(d,emptyMap())
            days[d]=day.copy(readings=day.readings+mapOf(Metric.SLEEP to reading(hours,now),Metric.SLEEP_STAGES to reading(hours.takeIf{stages.isNotEmpty()},now)),sleepStages=stages,
                sleepStart=timestamp(o,"sleepStartTimestampGMT"),sleepEnd=timestamp(o,"sleepEndTimestampGMT"),
                sleepWakeMinute=wakeMinute(o.optString("sleepEndTimestampLocal")))
        }
    }
    private fun wakeMinute(text: String): Int? {
        val time = runCatching { OffsetDateTime.parse(text).toLocalTime() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(text.replace(' ', 'T')).toLocalTime() }.getOrNull()
        return time?.let { it.hour * 60 + it.minute }
    }
    private fun timestamp(o: JSONObject, field: String): Instant? = when (val raw = o.opt(field)) {
        is String -> runCatching { Instant.parse(raw) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(raw).toInstant(ZoneOffset.UTC) }.getOrNull()
        is Number -> runCatching { Instant.ofEpochMilli(raw.toLong()) }.getOrNull()
        else -> null
    }
    fun hrv(root: Any,days: MutableMap<LocalDate,DayData>,now: Instant) {
        val array=(root as? JSONObject)?.optJSONArray("hrvSummaries") ?: throw GarminFailure(GarminError.MALFORMED)
        objects(array).forEach {o->val d=date(o.optString("calendarDate")) ?: return@forEach;val day=days[d] ?: DayData(d,emptyMap());days[d]=day.copy(readings=day.readings+(Metric.HRV to reading(number(o,"lastNightAvg"),now)))}
    }
    fun heart(root: Any,days: MutableMap<LocalDate,DayData>,now: Instant) {
        val o=root as? JSONObject ?: throw GarminFailure(GarminError.MALFORMED)
        val d=date(o.optString("calendarDate")) ?: throw GarminFailure(GarminError.MALFORMED)
        val descriptors=o.optJSONArray("heartRateValueDescriptors") ?: JSONArray()
        val index=objects(descriptors).firstOrNull {it.optString("key").equals("heartrate",true)}?.optInt("index",-1) ?: -1
        val samples=o.optJSONArray("heartRateValues") ?: JSONArray()
        val values=if(index<0)emptyList()else (0 until samples.length()).mapNotNull {i->(samples.optJSONArray(i)?.opt(index) as? Number)?.toDouble()?.takeIf{it.isFinite() && it>0}}
        val day=days[d] ?: DayData(d,emptyMap());days[d]=day.copy(readings=day.readings+(Metric.HEART to reading(values.takeIf{it.isNotEmpty()}?.average(),now)))
    }
    fun activities(records: List<JSONObject>,days: MutableMap<LocalDate,DayData>,from: LocalDate,to: LocalDate,now: Instant) {
        val activities=records.distinctBy {it.optString("activityId")}.mapNotNull {o->
            val local=o.optString("startTimeLocal");val d=date(local.take(10)) ?: return@mapNotNull null
            if(o.isNull("activityId"))return@mapNotNull null
            d to RecordedActivity(o.optString("activityId"),o.optString("activityName","Activity").take(120),o.optJSONObject("activityType")?.optString("typeKey") ?: "activity",local,number(o,"distance")?.div(1000),number(o,"duration")?.div(60))
        }.groupBy({it.first},{it.second})
        var d=from
        while(d<=to){val day=days[d] ?: DayData(d,emptyMap());val list=activities[d].orEmpty();days[d]=day.copy(readings=day.readings+(Metric.ACTIVITIES to reading(list.size.toDouble().takeIf { activities.isNotEmpty() },now)) + (Metric.WORKOUT_DURATION to reading(
            if(activities.isNotEmpty() && list.all { it.minutes != null }) list.sumOf { it.minutes ?: 0.0 } else null, now)),activities=list,workoutsRead=true);d=d.plusDays(1)}
    }
    private fun reading(value: Double?,now: Instant)=Reading(value,if(value==null)DataState.EMPTY else DataState.READY,now)
    fun encode(day: DayData): String {
        val metrics=JSONObject();day.readings.forEach{(m,r)->metrics.put(m.name,JSONObject().put("value",r.value ?: JSONObject.NULL).put("state",r.state.name).put("updated",r.updated?.toString() ?: JSONObject.NULL))}
        val activities=JSONArray();day.activities.forEach{a->activities.put(JSONObject().put("id",a.id).put("name",a.name).put("type",a.type).put("start",a.localStart).put("km",a.distanceKm ?: JSONObject.NULL).put("minutes",a.minutes ?: JSONObject.NULL))}
        return JSONObject().put("date",day.date.toString()).put("readings",metrics).put("stages",JSONObject(day.sleepStages)).put("activities",activities).put("workoutsRead",day.workoutsRead).put("sleepWakeMinute",day.sleepWakeMinute).put("sleepStart",day.sleepStart?.toString()).put("sleepEnd",day.sleepEnd?.toString()).toString()
    }
    fun decode(raw: String,stale: Boolean=false): DayData {
        val o=JSONObject(raw);val metrics=o.getJSONObject("readings")
        val readings=metrics.keys().asSequence().mapNotNull{key->val metric=runCatching{Metric.valueOf(key)}.getOrNull() ?: return@mapNotNull null;val r=metrics.getJSONObject(key);val value=number(r,"value");metric to Reading(value,if(stale && value!=null)DataState.STALE else DataState.valueOf(r.getString("state")),r.optString("updated").takeIf{it!="null" && it.isNotBlank()}?.let(Instant::parse))}.toMap()
        val stages=o.optJSONObject("stages");val values=stages?.keys()?.asSequence()?.mapNotNull{k->number(stages,k)?.let{k to it}}?.toMap().orEmpty()
        val activities=objects(o.optJSONArray("activities") ?: JSONArray()).map{a->RecordedActivity(a.getString("id"),a.getString("name"),a.getString("type"),a.getString("start"),number(a,"km"),number(a,"minutes"))}
        return DayData(LocalDate.parse(o.getString("date")),readings,sleepStages=values,activities=activities,workoutsRead=!stale && o.optBoolean("workoutsRead"),sleepWakeMinute=o.optInt("sleepWakeMinute",-1).takeIf { it in 0..1439 },sleepStart=timestamp(o,"sleepStart"),sleepEnd=timestamp(o,"sleepEnd"))
    }
}
