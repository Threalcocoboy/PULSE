package com.pulse.app.core

import java.time.*
import java.time.format.DateTimeFormatter

enum class Metric(val label: String, val unit: String, val cumulative: Boolean = false) {
    STEPS("Steps", "steps", true), DISTANCE("Distance", "km", true), HEART("Heart rate", "bpm"),
    RESTING_HEART("Resting heart", "bpm"), SLEEP("Sleep", "h"), SLEEP_STAGES("Sleep stages", "h"),
    BATTERY("Body Battery", "score"), STRESS("Stress", "/100"), HRV("HRV", "ms"),
    ACTIVE("Active minutes", "min", true), CALORIES("Calories", "kcal", true),
    ACTIVITIES("Activities", "sessions", true), ACTIVE_CALORIES("Active calories", "kcal", true),
    FLOORS("Floors climbed", "floors", true), HYDRATION("Hydration", "L", true),
    WORKOUT_DURATION("Workout duration", "min", true), GOAL("Goals", "%"), WEEKLY("Weekly steps", "steps", true), TREND("Step trend", "steps"),
    ACTIVE_ZONE("Active Zone Minutes", "min", true), LIGHT_ACTIVITY("Light activity", "min", true),
    MODERATE_ACTIVITY("Moderate activity", "min", true), VIGOROUS_ACTIVITY("Vigorous activity", "min", true),
    SEDENTARY("Sedentary time", "min", true), ELEVATION("Elevation gain", "m", true),
    HEART_ZONE_TIME("Time in heart-rate zones", "min", true), HEART_ZONE_ENERGY("Heart-rate zone energy", "kcal", true),
    HEART_ZONES("Heart-rate zone thresholds", "bpm"),
    VO2_MAX("VO₂ max", "mL/kg/min"), DAILY_VO2_MAX("Daily VO₂ max", "mL/kg/min"),
    RUN_VO2_MAX("Running VO₂ max", "mL/kg/min"), SWIM_STROKES("Swim strokes", "strokes", true),
    SWIM_LENGTHS("Pool lengths", "lengths", true), TIME_IN_BED("Time in bed", "h"),
    SLEEP_PERIOD("Sleep period", "h"), SLEEP_LATENCY("Time to fall asleep", "min"),
    BASAL_CALORIES("Basal energy", "kcal", true),
    SLEEP_AWAKE("Awake during sleep", "h"), DAILY_HRV("Daily HRV", "ms"),
    SPO2("Oxygen saturation", "%"), DAILY_SPO2("Daily oxygen saturation", "%"),
    RESPIRATORY_RATE("Sleep respiratory rate", "breaths/min"), DAILY_RESPIRATORY_RATE("Daily respiratory rate", "breaths/min"),
    BODY_TEMPERATURE("Core temperature", "°C"), SLEEP_TEMPERATURE("Nightly skin temperature", "°C"),
    TEMPERATURE_BASELINE("Skin temperature baseline", "°C"),
    WEIGHT("Weight", "kg"), BODY_FAT("Body fat", "%"), HEIGHT("Height", "cm"),
    BLOOD_GLUCOSE("Blood glucose", "mg/dL"), FOOD_ENERGY("Food energy", "kcal", true),
    CARBOHYDRATES("Carbohydrates", "g", true), PROTEIN("Protein", "g", true), FAT("Fat", "g", true),
    FOOD_LOGS("Food logs", "entries", true), WEEKLY_ACTIVE("Active minutes per week", "min", true)
}
enum class DataMode { NONE, HEALTH_CONNECT, GARMIN, GOOGLE_HEALTH;
    companion object {
        fun fromStored(value: String) = entries.firstOrNull { it.name == value } ?: NONE
    }
}
enum class SetupStep { WELCOME, SOURCE, CONNECT, GOALS, NOTIFICATIONS, FINISH }
enum class SetupStatus { NOT_STARTED, IN_PROGRESS, COMPLETE, NEEDS_ATTENTION, SKIPPED }
enum class ProviderAccess { DISCONNECTED, PARTIAL, CONNECTED, UNAVAILABLE, DENIED, ERROR;
    val verified get() = this == CONNECTED || this == PARTIAL
}
enum class DataState { READY, EMPTY, PERMISSION_DENIED, UNSUPPORTED, UNAVAILABLE, ERROR, STALE }
data class Reading(val value: Double? = null, val state: DataState = DataState.EMPTY, val updated: Instant? = null,
    val provider: DataMode? = null)
data class RecordedActivity(val id: String,val name: String,val type: String,val localStart: String,val distanceKm: Double?,val minutes: Double?)
data class DayData(val date: LocalDate, val readings: Map<Metric, Reading>, val complete: Boolean = false,
    val sleepStages: Map<String,Double> = emptyMap(),val activities: List<RecordedActivity> = emptyList(),val sleepStart:Instant?=null,val sleepEnd:Instant?=null, val workoutsRead: Boolean = false, val sleepWakeMinute: Int? = null,
    val recordDetails: Map<Metric, List<String>> = emptyMap()) {
    fun value(metric: Metric): Double? = readings[when(metric) { Metric.GOAL, Metric.WEEKLY, Metric.TREND -> Metric.STEPS; else -> metric }]?.value
}
data class HealthSnapshot(val days: List<DayData> = emptyList(), val status: String = "Not synchronized", val synced: Instant? = null,
    val available: Set<Metric> = emptySet(), val granted: Set<String> = emptySet(), val loading: Boolean = false, val error: String? = null, val mode: DataMode = DataMode.HEALTH_CONNECT, val heartSamples: List<HeartSample> = emptyList(),
    val access: ProviderAccess = ProviderAccess.DISCONNECTED, val sourceScope: String? = null,
    val secondary: HealthSnapshot? = null)
data class Preferences(val onboarded: Boolean = false, val mode: DataMode = DataMode.NONE, val stepGoal: Int = 5530,
    val sleepGoal: Double = 8.0, val theme: String = "DARK", val animations: Boolean = true, val haptics: Boolean = true,
    val insights: Boolean = true, val imperial: Boolean = false,
    val distanceGoal: Double = 5.0, val activeGoal: Int = 30, val workoutGoal: Int = 3,
    val enabledGoals: Set<Metric> = setOf(Metric.STEPS, Metric.SLEEP),
    val displayName: String = "", val introSeen: Boolean = false, val onboardingStep: Int = 0,
    val progressNotifications: Boolean = false, val notificationMetrics: Set<Metric> = setOf(Metric.STEPS,Metric.DISTANCE),
    val setupSource: DataMode = DataMode.NONE, val sourceSetup: SetupStatus = SetupStatus.NOT_STARTED,
    val goalsSetup: SetupStatus = SetupStatus.NOT_STARTED, val notificationSetup: SetupStatus = SetupStatus.NOT_STARTED,
    val combineHealthConnect: Boolean = false, val providerPriority: Map<Metric, DataMode> = emptyMap(),
    val weeklyActiveGoal: Int = 150, val personalGoals: List<PersonalGoal> = emptyList()) {
    fun sanitized() = copy(progressNotifications = progressNotifications && mode != DataMode.NONE, displayName=displayName.filter { !it.isISOControl() }.trim().take(40), onboardingStep=onboardingStep.coerceIn(0, SetupStep.entries.lastIndex), notificationMetrics=notificationMetrics.intersect(setOf(Metric.STEPS,Metric.DISTANCE,Metric.ACTIVE,Metric.CALORIES,Metric.ACTIVE_CALORIES,Metric.FLOORS,Metric.HYDRATION,Metric.ACTIVITIES)),stepGoal = stepGoal.coerceIn(100, 100000), sleepGoal = sleepGoal.takeIf { it.isFinite() }?.coerceIn(1.0, 16.0) ?: 8.0,
        distanceGoal = distanceGoal.takeIf { it.isFinite() }?.coerceIn(.5,100.0) ?: 5.0,
        activeGoal = activeGoal.coerceIn(5,300), workoutGoal = workoutGoal.coerceIn(1,21),
        weeklyActiveGoal = weeklyActiveGoal.coerceIn(15, 2100),
        enabledGoals = enabledGoals.intersect(Goals.supported),
        personalGoals = personalGoals.filter(PersonalGoal::valid).distinctBy { it.id },
        theme = theme.takeIf { it in setOf("DARK", "LIGHT", "SYSTEM") } ?: "DARK")
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
