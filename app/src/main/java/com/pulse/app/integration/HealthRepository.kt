package com.pulse.app.integration

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.*
import androidx.health.connect.client.time.TimeRangeFilter
import com.pulse.app.core.*
import com.pulse.app.data.*
import kotlinx.coroutines.*
import java.time.*

interface HealthDataRepository { suspend fun load(today: LocalDate): HealthSnapshot }
class DemoRepository : HealthDataRepository { override suspend fun load(today: LocalDate) = withContext(Dispatchers.Default) { DemoData.snapshot(today) } }
class HealthConnectRepository(private val context: Context, private val dao: HealthDao) : HealthDataRepository {
    companion object {
        val permissions = setOf(HealthPermission.getReadPermission(StepsRecord::class), HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class), HealthPermission.getReadPermission(DistanceRecord::class))
        val supported = setOf(Metric.STEPS, Metric.SLEEP, Metric.HEART, Metric.DISTANCE)
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
            val start = today.minusDays(28)
            val metrics = buildSet {
                if(Metric.STEPS in available) add(StepsRecord.COUNT_TOTAL)
                if(Metric.HEART in available) add(HeartRateRecord.BPM_AVG)
                if(Metric.DISTANCE in available) add(DistanceRecord.DISTANCE_TOTAL)
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
                } while(token!=null)
            }
            val now = Instant.now()
            val byDate = buckets.associateBy { it.startTime.toLocalDate() }
            val rows = (0L..28L).map { ago ->
                val date = today.minusDays(ago); val result=byDate[date]?.result
                val sessions=sleep.filter { HistoryMath.sleepDate(it.endTime,zone)==date }.distinctBy { it.metadata.id }
                // A deterministic one-source-per-night policy; union overlapping sessions within that source.
                val primary=sessions.minOfOrNull { it.metadata.dataOrigin.packageName }
                val intervals=sessions.filter { it.metadata.dataOrigin.packageName==primary }.map { it.startTime to it.endTime }
                DailyEntity(date.toString(),zone.id,result?.get(StepsRecord.COUNT_TOTAL)?.toDouble(),
                    intervals.takeIf { it.isNotEmpty() }?.let(HistoryMath::sleepHours),result?.get(HeartRateRecord.BPM_AVG)?.toDouble(),result?.get(DistanceRecord.DISTANCE_TOTAL)?.inKilometers,now.toEpochMilli())
            }
            dao.saveDays(rows)
            val snapshot = fromRows(dao.days(zone.id), available, granted, false).copy(status="Health Connect · synchronized",synced=now)
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
    private fun permission(m: Metric) = when(m) { Metric.STEPS -> HealthPermission.getReadPermission(StepsRecord::class); Metric.SLEEP -> HealthPermission.getReadPermission(SleepSessionRecord::class); Metric.HEART -> HealthPermission.getReadPermission(HeartRateRecord::class); else -> HealthPermission.getReadPermission(DistanceRecord::class) }
    private fun empty(message: String, state: DataState, today: LocalDate) = HealthSnapshot(listOf(DayData(today,Metric.entries.associateWith { Reading(state=if(it in supported) state else DataState.UNSUPPORTED) })),message)
    private fun fromRows(rows: List<DailyEntity>, available: Set<Metric>, granted: Set<String>, stale: Boolean): HealthSnapshot {
        val days=rows.map { r -> DayData(LocalDate.parse(r.date),Metric.entries.associateWith { m ->
            val v=when(m) { Metric.STEPS -> r.steps; Metric.SLEEP -> r.sleep; Metric.HEART -> r.heart; Metric.DISTANCE -> r.distance; else -> null }
            when { m !in supported -> Reading(state=DataState.UNSUPPORTED)
                m !in available -> Reading(state=DataState.PERMISSION_DENIED)
                else -> Reading(v,if(v==null) DataState.EMPTY else if(stale) DataState.STALE else DataState.READY,Instant.ofEpochMilli(r.synced)) }
        },complete=false) }
        return HealthSnapshot(days, synced=rows.maxOfOrNull { it.synced }?.let(Instant::ofEpochMilli),available=available,granted=granted)
    }
}
