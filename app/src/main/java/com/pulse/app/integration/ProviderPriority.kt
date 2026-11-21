package com.pulse.app.integration

import com.pulse.app.core.*
import java.time.ZoneId

internal object ProviderPriority {
    fun key(metric: Metric): Metric = when (metric) {
        Metric.SLEEP_STAGES, Metric.TIME_IN_BED, Metric.SLEEP_AWAKE, Metric.SLEEP_PERIOD, Metric.SLEEP_LATENCY -> Metric.SLEEP
        Metric.WORKOUT_DURATION -> Metric.ACTIVITIES
        Metric.GOAL, Metric.WEEKLY, Metric.TREND -> Metric.STEPS
        else -> metric
    }

    fun merge(google: HealthSnapshot, healthConnect: HealthSnapshot, priorities: Map<Metric, DataMode>, zone: ZoneId): HealthSnapshot {
        val googleDays = google.days.associateBy { it.date }
        val localDays = healthConnect.days.associateBy { it.date }
        val useLocalHeart = hashSetOf<java.time.LocalDate>()
        val days = (googleDays.keys + localDays.keys).sorted().map { date ->
            val cloud = googleDays[date]
            val local = localDays[date]
            fun choose(metric: Metric): DataMode {
                val grouped = key(metric)
                val first = priorities[grouped] ?: DataMode.GOOGLE_HEALTH
                val other = if (first == DataMode.GOOGLE_HEALTH) DataMode.HEALTH_CONNECT else DataMode.GOOGLE_HEALTH
                val candidates = listOf(first, other)
                fun reading(provider: DataMode) = (if (provider == DataMode.GOOGLE_HEALTH) cloud else local)?.readings?.get(grouped)
                fun workoutCoverage(provider: DataMode) =
                    grouped == Metric.ACTIVITIES && (if (provider == DataMode.GOOGLE_HEALTH) cloud else local)?.workoutsRead == true
                fun usable(provider: DataMode) = reading(provider)?.let { it.state == DataState.READY && it.value != null } == true ||
                    workoutCoverage(provider)
                return candidates.firstOrNull(::usable)
                    ?: candidates.firstOrNull { reading(it)?.value != null && reading(it)?.state == DataState.STALE }
                    ?: candidates.firstOrNull { grouped in (if (it == DataMode.GOOGLE_HEALTH) google.available else healthConnect.available) }
                    ?: first
            }
            val sleep = if (choose(Metric.SLEEP) == DataMode.GOOGLE_HEALTH) cloud else local
            val workouts = if (choose(Metric.ACTIVITIES) == DataMode.GOOGLE_HEALTH) cloud else local
            if (choose(Metric.HEART) == DataMode.HEALTH_CONNECT) useLocalHeart += date
            val readings = Metric.entries.associateWith { metric ->
                // Provider-exclusive measurements remain available beside the selected shared stream.
                val provider = when {
                    metric !in HealthConnectRepository.supported && metric in google.available -> DataMode.GOOGLE_HEALTH
                    metric !in google.available && metric in healthConnect.available -> DataMode.HEALTH_CONNECT
                    else -> choose(metric)
                }
                val day = if (provider == DataMode.GOOGLE_HEALTH) cloud else local
                day?.readings?.get(metric)?.copy(provider = provider) ?: Reading(state =
                    (if (provider == DataMode.GOOGLE_HEALTH) local else cloud)?.readings?.get(metric)?.state
                        ?.takeIf { it == DataState.PERMISSION_DENIED } ?: DataState.EMPTY)
            }
            val details = cloud?.recordDetails.orEmpty().filterKeys { readings[it]?.provider == DataMode.GOOGLE_HEALTH }
            DayData(date, readings, (if (choose(Metric.STEPS) == DataMode.GOOGLE_HEALTH) cloud else local)?.complete == true,
                sleep?.sleepStages.orEmpty(), workouts?.activities.orEmpty(), sleep?.sleepStart, sleep?.sleepEnd,
                workouts?.workoutsRead == true, sleep?.sleepWakeMinute, details)
        }
        val heart = google.heartSamples.filter { it.time.atZone(zone).toLocalDate() !in useLocalHeart } +
            healthConnect.heartSamples.filter { it.time.atZone(zone).toLocalDate() in useLocalHeart }
        return google.copy(days = days, available = google.available + healthConnect.available,
            heartSamples = heart.sortedBy { it.time }, secondary = healthConnect,
            status = "${google.status} · Health Connect enabled")
    }
}
