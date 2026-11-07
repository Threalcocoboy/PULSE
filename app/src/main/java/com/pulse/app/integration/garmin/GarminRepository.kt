package com.pulse.app.integration.garmin

import com.pulse.app.core.*
import com.pulse.app.data.*
import com.pulse.app.integration.HealthDataRepository
import kotlinx.coroutines.*
import org.json.*
import java.time.*

class GarminRepository(private val api: GarminApi,private val dao: HealthDao): HealthDataRepository {
    suspend fun cached(): HealthSnapshot=withContext(Dispatchers.IO) {
        val rows=dao.garminDays();val days=rows.mapNotNull{runCatching{GarminMapping.decode(it.payload,true)}.getOrNull()}
        HealthSnapshot(days,"Garmin · cached records",rows.maxOfOrNull{it.synced}?.let(Instant::ofEpochMilli),available=days.flatMap{it.readings.filterValues{r->r.value!=null}.keys}.toSet(),loading=true,mode=DataMode.GARMIN)
    }
    override suspend fun load(today: LocalDate): HealthSnapshot=withContext(Dispatchers.IO) {
        val now=Instant.now();val recent=today.minusDays(30);val historyStart=today.minusDays(89)
        try {
            val days=GarminMapping.summaries(api.json("/usersummary-service/v2/dailysummaries?startDate=$historyStart&endDate=$today"),now).toMutableMap()
            val errors=mutableListOf<String>();val unavailable=mutableSetOf<Metric>();val failed=mutableSetOf<Metric>()
            val prior=dao.garminDays().mapNotNull{runCatching{GarminMapping.decode(it.payload,true)}.getOrNull()}.associateBy{it.date}
            suspend fun optional(metrics: Set<Metric>,read: suspend ()->Unit) {
                try{read()}catch(e: CancellationException){throw e}catch(e: GarminFailure){
                    if(e.code in setOf(GarminError.AUTHORIZATION,GarminError.RATE_LIMIT,GarminError.KEY_REQUIRED))throw e
                    errors+=e.message.orEmpty();if(e.code==GarminError.UNSUPPORTED)unavailable+=metrics else failed+=metrics
                }
            }
            optional(setOf(Metric.SLEEP,Metric.SLEEP_STAGES)){GarminMapping.sleep(api.json("/sleep-service/sleep/dailySleepSummariesByDate?startDate=$recent&endDate=$today"),days,now)}
            optional(setOf(Metric.HRV)){GarminMapping.hrv(api.json("/hrv-service/hrv/daily/$recent/$today"),days,now)}
            optional(setOf(Metric.HEART)){GarminMapping.heart(api.json("/wellness-service/wellness/dailyHeartRate?date=$today"),days,now)}
            optional(setOf(Metric.ACTIVITIES)){
                val records=mutableListOf<JSONObject>();var offset=0;var page: JSONArray
                do {
                    page=api.json("/activitylist-service/activities/search/activities?start=$offset&limit=99&startDate=$recent&endDate=$today&excludeChildren=true") as? JSONArray ?: throw GarminFailure(GarminError.MALFORMED)
                    records+=GarminMapping.objects(page);offset+=99
                    if(offset>=990 && page.length()==99)throw GarminFailure(GarminError.MALFORMED)
                }while(page.length()==99)
                GarminMapping.activities(records,days,recent,today,now)
            }
            unavailable.forEach{m->days.replaceAll{_,d->d.copy(readings=d.readings+(m to Reading(state=DataState.UNSUPPORTED)))}}
            failed.forEach{m->days.replaceAll{date,d->
                val old=prior[date];val reading=old?.readings?.get(m)?.takeIf{it.value!=null}?.copy(state=DataState.STALE) ?: Reading(state=DataState.ERROR)
                d.copy(readings=d.readings+(m to reading),sleepStages=if(m==Metric.SLEEP_STAGES)old?.sleepStages.orEmpty()else d.sleepStages,activities=if(m==Metric.ACTIVITIES)old?.activities.orEmpty()else d.activities)
            }}
            // Separate table: Garmin and Health Connect are never added together.
            dao.saveGarmin(days.values.filter{it.date<=today}.map{GarminDayEntity(it.date.toString(),"GARMIN_ACCOUNT_LOCAL",GarminMapping.encode(it),now.toEpochMilli())})
            val all=dao.garminDays().mapNotNull{runCatching{GarminMapping.decode(it.payload)}.getOrNull()}
            HealthSnapshot(all,"Garmin Personal API · synchronized",now,GarminMapping.supported-unavailable,error=errors.firstOrNull(),mode=DataMode.GARMIN)
        }catch(e: CancellationException){throw e}catch(e: Exception) {
            val cached=dao.garminDays();val days=cached.mapNotNull{runCatching{GarminMapping.decode(it.payload,true)}.getOrNull()}
            HealthSnapshot(days,if(days.isEmpty())"Garmin is not synchronized"else"Garmin · cached records",cached.maxOfOrNull{it.synced}?.let(Instant::ofEpochMilli),
                available=days.flatMap{it.readings.filterValues{r->r.value!=null}.keys}.toSet(),error=(e as? GarminFailure)?.message ?: "Garmin synchronization failed. Retry when connected.",mode=DataMode.GARMIN)
        }
    }
}
