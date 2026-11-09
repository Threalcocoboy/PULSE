package com.pulse.app.integration

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.*
import androidx.health.connect.client.time.TimeRangeFilter
import com.pulse.app.core.*
import com.pulse.app.data.*
import kotlinx.coroutines.*
import java.time.*
import org.json.*

interface HealthDataRepository { suspend fun load(today: LocalDate): HealthSnapshot }
class DemoRepository : HealthDataRepository { override suspend fun load(today: LocalDate) = withContext(Dispatchers.Default) { DemoData.snapshot(today) } }
class HealthConnectRepository(private val context: Context, private val dao: HealthDao) : HealthDataRepository {
    companion object {
        val permissions = setOf(HealthPermission.getReadPermission(StepsRecord::class), HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class), HealthPermission.getReadPermission(DistanceRecord::class),HealthPermission.getReadPermission(RestingHeartRateRecord::class),HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),HealthPermission.getReadPermission(ExerciseSessionRecord::class))
        val supported = setOf(Metric.STEPS, Metric.SLEEP, Metric.SLEEP_STAGES, Metric.HEART, Metric.DISTANCE,Metric.RESTING_HEART,Metric.HRV,Metric.CALORIES,Metric.ACTIVE,Metric.ACTIVITIES)
    }
    fun requestedPermissions(): Set<String> {
        if(availability()!=HealthConnectClient.SDK_AVAILABLE) return permissions
        val features=HealthConnectClient.getOrCreate(context).features
        return permissions + buildSet {
            if(features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY)==HealthConnectFeatures.FEATURE_STATUS_AVAILABLE) add(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY)
            if(features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND)==HealthConnectFeatures.FEATURE_STATUS_AVAILABLE) add(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND)
        }
    }
    fun availability(): Int = HealthConnectClient.getSdkStatus(context)
    override suspend fun load(today: LocalDate): HealthSnapshot = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        if(availability() != HealthConnectClient.SDK_AVAILABLE) return@withContext empty("Health Connect is unavailable. Install or update it on a supported Android device.", DataState.UNAVAILABLE, today)
        val client = HealthConnectClient.getOrCreate(context)
        var granted = emptySet<String>()
        try {
            granted = client.permissionController.getGrantedPermissions()
            val available = supported.filter { permission(it) in granted }.toSet()
            if(available.isEmpty()) return@withContext empty("Read permission required", DataState.PERMISSION_DENIED, today).copy(granted=granted)
            val hasHistory=HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in granted
            val backfill=hasHistory && RefreshPolicy.needsHistory(dao.days(zone.id).map{LocalDate.parse(it.date) to Instant.ofEpochMilli(it.synced)},today,Instant.now())
            val start = today.minusDays(if(backfill) 365 else 28)
            val end=today.plusDays(1).atStartOfDay(zone).toInstant()
            val heart=mutableListOf<HeartSample>()
            if(Metric.HEART in available) {
                var token: String?=null
                do {
                    val page=client.readRecords(ReadRecordsRequest(HeartRateRecord::class,
                        timeRangeFilter=TimeRangeFilter.between(start.atStartOfDay(zone).toInstant(),end),pageToken=token))
                    page.records.forEach { record -> record.samples.forEach { sample ->
                        heart += HeartSample(sample.time,sample.beatsPerMinute.toDouble(),record.metadata.dataOrigin.packageName)
                    } }
                    token=page.pageToken
                } while(!token.isNullOrEmpty())
            }
            val heartDays=HeartHistory.daily(heart,zone,start,today)
            if(Metric.HEART in available)dao.replaceSamples(zone.id,start.atStartOfDay(zone).toInstant().toEpochMilli(),end.toEpochMilli(),heart.filter{it.time>=start.atStartOfDay(zone).toInstant()&&it.time<end}.map{HeartSampleEntity(zone.id,it.time.toEpochMilli(),it.source,it.bpm)})
            suspend fun <T:Record> records(type:kotlin.reflect.KClass<T>):List<T>{val all=mutableListOf<T>();var token:String?=null;do{val page=client.readRecords(ReadRecordsRequest(type,timeRangeFilter=TimeRangeFilter.between(start.atStartOfDay(zone).toInstant(),end),pageToken=token));all+=page.records;token=page.pageToken}while(!token.isNullOrEmpty());return all}
            val resting=if(Metric.RESTING_HEART in available)records(RestingHeartRateRecord::class) else emptyList()
            val hrv=if(Metric.HRV in available)records(HeartRateVariabilityRmssdRecord::class) else emptyList()
            val exercise=if(Metric.ACTIVITIES in available)records(ExerciseSessionRecord::class) else emptyList()

            val metrics = buildSet {
                if(Metric.STEPS in available) add(StepsRecord.COUNT_TOTAL)
                if(Metric.DISTANCE in available) add(DistanceRecord.DISTANCE_TOTAL)
                if(Metric.CALORIES in available) add(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
            }
            val buckets = if(metrics.isEmpty()) emptyList() else client.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(
                metrics=metrics, timeRangeFilter=TimeRangeFilter.between(start.atStartOfDay(), today.plusDays(1).atStartOfDay()), timeRangeSlicer=Period.ofDays(1)))
            val sleep = mutableListOf<SleepSessionRecord>()
            if(Metric.SLEEP in available) {
                var token: String? = null
                do {
                    val page = client.readRecords(ReadRecordsRequest(SleepSessionRecord::class,
                        timeRangeFilter=TimeRangeFilter.between(start.minusDays(1).atStartOfDay(zone).toInstant(), today.plusDays(1).atStartOfDay(zone).toInstant()), pageToken=token))
                    sleep.addAll(page.records); token=page.pageToken
                } while(!token.isNullOrEmpty())
            }
            val now = Instant.now()
            val byDate = buckets.associateBy { it.startTime.toLocalDate() }
            val rows = (0L..java.time.temporal.ChronoUnit.DAYS.between(start,today)).map { ago ->
                val date = today.minusDays(ago); val result=byDate[date]?.result
                val sessions=sleep.filter { HistoryMath.sleepDate(it.endTime,zone)==date }.distinctBy { it.metadata.id }
                // A deterministic one-source-per-night policy; union overlapping sessions within that source.
                val primary=sessions.minOfOrNull { it.metadata.dataOrigin.packageName }
                val intervals=sessions.filter { it.metadata.dataOrigin.packageName==primary }.map { it.startTime to it.endTime }
                DailyEntity(date.toString(),zone.id,result?.get(StepsRecord.COUNT_TOTAL)?.toDouble(),
                    intervals.takeIf { it.isNotEmpty() }?.let(HistoryMath::sleepHours),heartDays[date],result?.get(DistanceRecord.DISTANCE_TOTAL)?.inKilometers,now.toEpochMilli())
            }
            val extraRows=rows.map{row->val date=LocalDate.parse(row.date);val json=JSONObject();
                resting.filter{it.time.atZone(zone).toLocalDate()==date}.map{it.beatsPerMinute.toDouble()}.takeIf{it.isNotEmpty()}?.let{json.put("RESTING_HEART",it.average())}
                hrv.filter{it.time.atZone(zone).toLocalDate()==date}.map{it.heartRateVariabilityMillis}.takeIf{it.isNotEmpty()}?.let{json.put("HRV",it.average())}
                byDate[date]?.result?.get(TotalCaloriesBurnedRecord.ENERGY_TOTAL)?.inKilocalories?.let{json.put("CALORIES",it)}
                val bounds=HistoryMath.dayBounds(date,zone);val candidates=exercise.filter{it.startTime<bounds.second&&it.endTime>bounds.first};val origin=candidates.minOfOrNull{it.metadata.dataOrigin.packageName};val intervalsForDay=candidates.filter{it.metadata.dataOrigin.packageName==origin}.distinctBy{it.metadata.id};val workouts=intervalsForDay.filter{it.startTime.atZone(zone).toLocalDate()==date}
                if(workouts.isNotEmpty())json.put("ACTIVITIES",workouts.size);if(intervalsForDay.isNotEmpty())json.put("ACTIVE",HistoryMath.sleepHours(intervalsForDay.map{maxOf(it.startTime,bounds.first) to minOf(it.endTime,bounds.second)})*60)
                val night=sleep.filter{HistoryMath.sleepDate(it.endTime,zone)==date};val nightOrigin=night.minOfOrNull{it.metadata.dataOrigin.packageName};val primaryNight=night.filter{it.metadata.dataOrigin.packageName==nightOrigin};primaryNight.minOfOrNull{it.startTime}?.let{json.put("sleep_start",it.toString())};primaryNight.maxOfOrNull{it.endTime}?.let{json.put("sleep_end",it.toString())};val stages=JSONObject();sleep.filter{HistoryMath.sleepDate(it.endTime,zone)==date&&it.metadata.dataOrigin.packageName==sleep.filter{v->HistoryMath.sleepDate(v.endTime,zone)==date}.minOfOrNull{v->v.metadata.dataOrigin.packageName}}.flatMap{it.stages}.groupBy{it.stage}.forEach{(stage,list)->val key=when(stage){SleepSessionRecord.STAGE_TYPE_DEEP->"deep";SleepSessionRecord.STAGE_TYPE_LIGHT->"light";SleepSessionRecord.STAGE_TYPE_REM->"rem";SleepSessionRecord.STAGE_TYPE_AWAKE->"awake";else->"other"};stages.put(key,HistoryMath.sleepHours(list.map{it.startTime to it.endTime}))};json.put("stages",stages)
                val activities=JSONArray();workouts.forEach{a->activities.put(JSONObject().put("id",a.metadata.id).put("name",a.title ?: "Exercise").put("type",a.exerciseType.toString()).put("start",a.startTime.atZone(zone).toLocalTime().toString()).put("minutes",Duration.between(a.startTime,a.endTime).toMinutes()))};json.put("activities",activities)
                HealthExtraEntity(zone.id,row.date,json.toString(),now.toEpochMilli())
            }
            dao.saveExtras(extraRows)
            dao.saveDays(rows)
            val snapshot = fromRows(dao.days(zone.id), available, granted, false).copy(status="Health Connect · synchronized" + if(hasHistory) " · authorized history · active minutes reflect recorded exercise" else " · recent history (older records require history access)",synced=now)
            val medals=snapshot.days.map { YearMonth.from(it.date) }.distinct().map { month ->
                val m=Achievements.calculate(month,snapshot.days,today)
                MedalEntity(month.toString(),zone.id,m.total,m.tier?.name,m.state,now.toEpochMilli())
            }
            dao.saveMedals(medals)
            snapshot
        } catch(e: CancellationException) { throw e
        } catch(e: Exception) {
            val available = supported.filter { permission(it) in granted }.toSet()
            val cached = fromRows(dao.days(zone.id),available,granted,true)
            cached.copy(status="Synchronization failed · cached records", error=if(e is SecurityException) "Permission changed. Review Health Connect access." else "Could not read Health Connect. Retry when the provider is available.")
        }
    }
    private fun permission(m:Metric)=HealthPermission.getReadPermission(when(m){Metric.STEPS->StepsRecord::class;Metric.SLEEP,Metric.SLEEP_STAGES->SleepSessionRecord::class;Metric.HEART->HeartRateRecord::class;Metric.RESTING_HEART->RestingHeartRateRecord::class;Metric.HRV->HeartRateVariabilityRmssdRecord::class;Metric.CALORIES->TotalCaloriesBurnedRecord::class;Metric.ACTIVE,Metric.ACTIVITIES->ExerciseSessionRecord::class;else->DistanceRecord::class})
    private fun empty(message: String, state: DataState, today: LocalDate) = HealthSnapshot(listOf(DayData(today,Metric.entries.associateWith { Reading(state=if(it in supported) state else DataState.UNSUPPORTED) })),message)
    private suspend fun fromRows(rows: List<DailyEntity>, available: Set<Metric>, granted: Set<String>, stale: Boolean): HealthSnapshot {
        val cacheZone=rows.firstOrNull()?.zone ?: ZoneId.systemDefault().id
        val extras=dao.extras(cacheZone).associateBy{it.date}
        val days=rows.map { r -> DayData(LocalDate.parse(r.date),Metric.entries.associateWith { m ->
            val extra=extras[r.date]?.let{runCatching{JSONObject(it.payload)}.getOrNull()}
            val v=when(m) { Metric.STEPS -> r.steps; Metric.SLEEP -> r.sleep; Metric.HEART -> r.heart; Metric.DISTANCE -> r.distance;Metric.SLEEP_STAGES->r.sleep;else -> extra?.takeIf{it.has(m.name)}?.optDouble(m.name)?.takeIf{it.isFinite()} }
            when { m !in supported -> Reading(state=DataState.UNSUPPORTED)
                m !in available -> Reading(state=DataState.PERMISSION_DENIED)
                else -> Reading(v,if(v==null) DataState.EMPTY else if(stale) DataState.STALE else DataState.READY,Instant.ofEpochMilli(r.synced)) }
        },complete=false).let{day->val extra=extras[r.date]?.let{runCatching{JSONObject(it.payload)}.getOrNull()};val stages=extra?.optJSONObject("stages");val list=extra?.optJSONArray("activities");day.copy(sleepStart=if(Metric.SLEEP in available)extra?.optString("sleep_start")?.takeIf{it.isNotBlank()}?.let{runCatching{Instant.parse(it)}.getOrNull()} else null,sleepEnd=if(Metric.SLEEP in available)extra?.optString("sleep_end")?.takeIf{it.isNotBlank()}?.let{runCatching{Instant.parse(it)}.getOrNull()}else null,sleepStages=if(Metric.SLEEP_STAGES in available)stages?.keys()?.asSequence()?.associateWith{stages.getDouble(it)} ?: emptyMap()else emptyMap(),activities=if(Metric.ACTIVITIES in available)(0 until (list?.length() ?: 0)).map{val a=list!!.getJSONObject(it);RecordedActivity(a.getString("id"),a.getString("name"),a.getString("type"),a.getString("start"),null,a.getDouble("minutes"))}else emptyList())} }
        return HealthSnapshot(days, synced=rows.maxOfOrNull { it.synced }?.let(Instant::ofEpochMilli),available=available,granted=granted,heartSamples=if(Metric.HEART in available)dao.samples(cacheZone).map{HeartSample(Instant.ofEpochMilli(it.time),it.bpm,it.source)}else emptyList())
    }
}
