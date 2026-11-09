package com.pulse.app.core

import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.abs

enum class Metric(val label: String, val unit: String, val cumulative: Boolean = false) {
    STEPS("Steps", "steps", true), DISTANCE("Distance", "km", true), HEART("Heart rate", "bpm"),
    RESTING_HEART("Resting heart", "bpm"), SLEEP("Sleep", "h"), SLEEP_STAGES("Sleep stages", "h"),
    BATTERY("Body Battery", "score"), STRESS("Stress", "/100"), HRV("HRV", "ms"),
    ACTIVE("Active minutes", "min", true), CALORIES("Calories", "kcal", true),
    ACTIVITIES("Activities", "sessions", true), GOAL("Goals", "%"), WEEKLY("Weekly steps", "steps", true), TREND("Step trend", "steps")
}
enum class DataMode { DEMO, HEALTH_CONNECT, GARMIN }
enum class DataState { READY, EMPTY, PERMISSION_DENIED, UNSUPPORTED, UNAVAILABLE, ERROR, STALE }
data class Reading(val value: Double? = null, val state: DataState = DataState.EMPTY, val updated: Instant? = null)
data class RecordedActivity(val id: String,val name: String,val type: String,val localStart: String,val distanceKm: Double?,val minutes: Double?)
data class DayData(val date: LocalDate, val readings: Map<Metric, Reading>, val complete: Boolean = false,
    val sleepStages: Map<String,Double> = emptyMap(),val activities: List<RecordedActivity> = emptyList(),val sleepStart:Instant?=null,val sleepEnd:Instant?=null) {
    fun value(metric: Metric): Double? = readings[when(metric) { Metric.GOAL, Metric.WEEKLY, Metric.TREND -> Metric.STEPS; else -> metric }]?.value
}
data class HealthSnapshot(val days: List<DayData> = emptyList(), val status: String = "Not synchronized", val synced: Instant? = null,
    val available: Set<Metric> = emptySet(), val granted: Set<String> = emptySet(), val loading: Boolean = false, val error: String? = null, val mode: DataMode = DataMode.HEALTH_CONNECT, val heartSamples: List<HeartSample> = emptyList())
data class Preferences(val onboarded: Boolean = false, val mode: DataMode = DataMode.DEMO, val stepGoal: Int = 5530,
    val sleepGoal: Double = 8.0, val theme: String = "DARK", val animations: Boolean = true, val haptics: Boolean = true,
    val insights: Boolean = true, val imperial: Boolean = false,
    val distanceGoal: Double = 5.0, val activeGoal: Int = 30, val workoutGoal: Int = 3,
    val enabledGoals: Set<Metric> = setOf(Metric.STEPS, Metric.SLEEP),
    val displayName: String = "", val introSeen: Boolean = false, val onboardingStep: Int = 0,
    val progressNotifications: Boolean = false, val notificationMetrics: Set<Metric> = setOf(Metric.STEPS,Metric.DISTANCE)) {
    fun sanitized() = copy(displayName=displayName.filter { !it.isISOControl() }.trim().take(40), onboardingStep=onboardingStep.coerceIn(0,6), notificationMetrics=notificationMetrics.intersect(setOf(Metric.STEPS,Metric.DISTANCE,Metric.ACTIVE,Metric.CALORIES)),stepGoal = stepGoal.coerceIn(100, 100000), sleepGoal = sleepGoal.takeIf { it.isFinite() }?.coerceIn(1.0, 16.0) ?: 8.0,
        distanceGoal = distanceGoal.takeIf { it.isFinite() }?.coerceIn(.5,100.0) ?: 5.0,
        activeGoal = activeGoal.coerceIn(5,300), workoutGoal = workoutGoal.coerceIn(1,21),
        enabledGoals = enabledGoals.intersect(Goals.supported).ifEmpty { setOf(Metric.STEPS) },
        theme = theme.takeIf { it in setOf("DARK", "LIGHT", "SYSTEM") } ?: "DARK")
}
object DemoData {
    // Pure calendar-keyed data: the same date always gives the same sample. Never persisted as real health data.
    fun snapshot(today: LocalDate): HealthSnapshot {
        val days = (0L..730L).map { ago ->
            val date = today.minusDays(ago)
            val seed = abs((date.toEpochDay() * 1103515245L + 12345) % 2147483647L)
            val steps = when(date.monthValue%5) {0 -> 800+seed%1400;1 -> 3000+seed%2200;2 -> 5000+seed%2400;3 -> 7500+seed%1800;else -> 10000+seed%3000}
            val values = mapOf(Metric.STEPS to steps.toDouble(), Metric.DISTANCE to steps * 0.00073,
                Metric.HEART to (60 + seed % 25).toDouble(), Metric.RESTING_HEART to (52 + seed % 13).toDouble(),
                Metric.SLEEP to if(seed % 17 == 0L) null else 6.0 + seed % 210 / 60.0,
                Metric.BATTERY to (35 + seed % 61).toDouble(), Metric.STRESS to (17 + seed % 47).toDouble(),
                Metric.HRV to (28 + seed % 46).toDouble(), Metric.ACTIVE to (12 + seed % 78).toDouble(),
                Metric.CALORIES to (1700 + seed % 900).toDouble(), Metric.ACTIVITIES to (seed % 3).toDouble())
            DayData(date, values.mapValues { (_, v) -> Reading(v, if(v == null) DataState.EMPTY else DataState.READY, date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant()) }, ago > 0)
        }.sortedBy { it.date }
        return HealthSnapshot(days, "Deterministic sample data · not live", available = Metric.entries.toSet()-Metric.SLEEP_STAGES,mode=DataMode.DEMO)
    }
}
data class PeriodSummary(val value: Double?, val previous: Double?, val valid: Int, val total: Int, val previousValid: Int) {
    val change get() = if(value != null && previous != null && valid==total && previousValid==total) value - previous else null
    val percent get() = if(previous != null && previous != 0.0) change?.div(previous)?.times(100) else null
}
object HistoryMath {
    fun unique(days: List<DayData>): List<DayData> = days.associateBy { it.date }.values.sortedBy { it.date }
    fun aggregate(days: List<DayData>, metric: Metric): Double? {
        val values = unique(days).mapNotNull { it.value(metric) }
        return if(values.isEmpty()) null else if(metric.cumulative || metric == Metric.TREND) values.sum() else values.average()
    }
    fun compare(days: List<DayData>, end: LocalDate, count: Int, metric: Metric): PeriodSummary {
        require(count > 0)
        val start = end.minusDays(count - 1L)
        val selected = unique(days).filter { it.date in start..end }
        val previous = unique(days).filter { it.date in start.minusDays(count.toLong())..start.minusDays(1) }
        return PeriodSummary(aggregate(selected, metric), aggregate(previous, metric), selected.count { it.value(metric) != null }, count,previous.count {it.value(metric)!=null})
    }
    fun monthly(days: List<DayData>, metric: Metric) = unique(days).groupBy { YearMonth.from(it.date) }.toSortedMap().mapValues { aggregate(it.value, metric) }
    fun dayBounds(date: LocalDate, zone: ZoneId): Pair<Instant, Instant> = date.atStartOfDay(zone).toInstant() to date.plusDays(1).atStartOfDay(zone).toInstant()
    fun sleepDate(end: Instant, zone: ZoneId) = end.atZone(zone).toLocalDate()
    fun sleepHours(intervals: List<Pair<Instant, Instant>>): Double {
        val sorted = intervals.filter { it.second > it.first }.sortedBy { it.first }
        if(sorted.isEmpty()) return 0.0
        var start = sorted.first().first; var end = sorted.first().second; var seconds = 0L
        sorted.drop(1).forEach { (a, b) -> if(a <= end) { if(b > end) end = b } else { seconds += Duration.between(start, end).seconds; start = a; end = b } }
        return (seconds + Duration.between(start, end).seconds) / 3600.0
    }
}
enum class Tier(val threshold: Long) { BRONZE(90000), SILVER(150000), GOLD(225000), DIAMOND(300000) }
enum class Season { WINTER, SPRING, SUMMER, AUTUMN }
data class Medal(val month: YearMonth, val total: Long?, val tier: Tier?, val state: String, val days: Int, val best: Long?, val average: Double?) {
    val engraving: String get() = month.format(DateTimeFormatter.ofPattern("MM/yy"))
    val season: Season get() = when(month.monthValue) { 12,1,2 -> Season.WINTER; 3,4,5 -> Season.SPRING; 6,7,8 -> Season.SUMMER; else -> Season.AUTUMN }
    val next: Tier? get() = Tier.entries.firstOrNull { it.threshold > (total ?: 0) }
}
object Achievements {
    fun tier(steps: Long) = Tier.entries.lastOrNull { steps >= it.threshold }
    fun calculate(month: YearMonth, days: List<DayData>, today: LocalDate): Medal {
        val valid = HistoryMath.unique(days).filter { YearMonth.from(it.date) == month && it.date <= today && it.value(Metric.STEPS) != null }
        val total = valid.takeIf { it.isNotEmpty() }?.sumOf { it.value(Metric.STEPS)!!.toLong() }
        val state = when {
            month > YearMonth.from(today) -> "FUTURE"
            month == YearMonth.from(today) -> "PROVISIONAL"
            valid.size == month.lengthOfMonth() && valid.all { it.complete } -> "FINAL"
            else -> "INCOMPLETE"
        }
        return Medal(month, total, total?.let(::tier), state, valid.size, valid.maxOfOrNull { it.value(Metric.STEPS)!!.toLong() }, valid.takeIf { it.isNotEmpty() }?.map { it.value(Metric.STEPS)!! }?.average())
    }
}
