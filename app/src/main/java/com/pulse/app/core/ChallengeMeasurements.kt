package com.pulse.app.core

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

enum class ChallengeCondition(val title: String) {
    AT_LEAST("Reach at least"), BELOW("Stay below"), RECORDED("Record a measurement"),
    PERSONAL_RANGE("Stay within my recent range")
}

enum class ChallengeMeasure(val metric: Metric, val title: String, val detail: String) {
    STEPS(Metric.STEPS, "Steps", "Daily steps recorded by your source."),
    DISTANCE(Metric.DISTANCE, "Distance", "Daily distance in kilometers."),
    ACTIVE(Metric.ACTIVE, "Active minutes", "Recorded activity minutes. Your provider determines the definition."),
    ACTIVE_ZONE(Metric.ACTIVE_ZONE, "Active Zone Minutes", "Uses the zone-weighted minutes recorded by Google Health."),
    ELEVATION(Metric.ELEVATION, "Elevation gain", "Recorded altitude gain in meters."),
    SWIM_LENGTHS(Metric.SWIM_LENGTHS, "Pool lengths", "Pool lengths recorded in workout summaries."),
    CALORIES(Metric.CALORIES, "Total calories", "Recorded active and resting energy combined."),
    ACTIVE_CALORIES(Metric.ACTIVE_CALORIES, "Active calories", "Recorded energy from activity."),
    FLOORS(Metric.FLOORS, "Floors climbed", "Recorded floors ascended."),
    HYDRATION(Metric.HYDRATION, "Hydration", "Recorded fluid intake in liters."),
    WORKOUT_COUNT(Metric.ACTIVITIES, "Workout count", "Recorded sessions count on the date they started."),
    WORKOUT_DURATION(Metric.WORKOUT_DURATION, "Workout duration", "Minutes from workouts that started that day."),
    WORKOUT_TYPE(Metric.ACTIVITIES, "Workout type", "Complete a recorded workout of the selected type."),
    WORKOUT_START(Metric.ACTIVITIES, "Workout start time", "Complete a workout that started by the time you choose."),
    SLEEP(Metric.SLEEP, "Sleep duration", "Sleep counts on the date you woke up."),
    SLEEP_STAGE(Metric.SLEEP_STAGES, "Sleep stage", "Track days when your source recorded the selected stage."),
    SLEEP_SCHEDULE(Metric.SLEEP, "Sleep consistency", "Wake within the same time window on multiple days."),
    HEART(Metric.HEART, "Heart rate", "Track recorded daily averages or compare with your own recent observations."),
    RESTING_HEART(Metric.RESTING_HEART, "Resting heart rate", "Uses recorded resting heart rate."),
    HRV(Metric.HRV, "HRV", "Uses recorded heart rate variability from your source."),
    STRESS(Metric.STRESS, "Stress level", "Uses Garmin’s recorded daily stress score."),
    BATTERY(Metric.BATTERY, "Body Battery", "Uses Garmin’s recorded daily Body Battery summary.");

    val sensitive get() = this in setOf(HEART, RESTING_HEART, HRV, STRESS, BATTERY)
    val timeBased get() = this == WORKOUT_START || this == SLEEP_SCHEDULE
    val presence get() = this in setOf(WORKOUT_TYPE, WORKOUT_START, SLEEP_STAGE, SLEEP_SCHEDULE)
    val conditions get() = when {
        sensitive -> listOf(ChallengeCondition.RECORDED, ChallengeCondition.PERSONAL_RANGE)
        presence -> listOf(ChallengeCondition.RECORDED)
        this in setOf(SLEEP, ACTIVE, CALORIES, ACTIVE_CALORIES, WORKOUT_DURATION) ->
            listOf(ChallengeCondition.AT_LEAST, ChallengeCondition.BELOW)
        else -> listOf(ChallengeCondition.AT_LEAST)
    }

    fun rules(condition: ChallengeCondition) = if (metric.cumulative && !presence && condition == ChallengeCondition.AT_LEAST)
        ChallengeRule.entries else ChallengeRule.entries - ChallengeRule.CUMULATIVE

    companion object {
        val stages = listOf("Deep", "Light", "REM", "Awake")
        val workoutTypes = listOf("Running", "Walking", "Cycling", "Swimming", "Strength training", "Yoga")
        fun forMetric(metric: Metric) = entries.firstOrNull { it.metric == metric }
    }
}

data class ChallengePeriod(val date: LocalDate, val value: Double?, val met: Boolean?, val closed: Boolean)

internal fun Reading?.challengeValue(): Double? =
    this?.takeIf { it.state == DataState.READY }?.value?.takeIf { it.isFinite() && it >= 0 }

internal fun DayData.closedFor(metric: Metric, today: LocalDate, zone: ZoneId): Boolean =
    date < today && (complete || readings[metric]?.updated?.let { it >= date.plusDays(1).atStartOfDay(zone).toInstant() } == true)

fun parseChallengeTime(text: String): Int? {
    val parts = text.trim().split(':')
    if (parts.size != 2 || parts[0].length !in 1..2 || parts[1].length != 2) return null
    val hour = parts[0].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
    val minute = parts[1].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
    return hour * 60 + minute
}

internal fun RecordedActivity.minuteOfDay(): Int? {
    val value = localStart.substringAfter('T').substringAfter(' ')
    val time = runCatching { LocalTime.parse(value) }.getOrNull()
    return time?.let { it.hour * 60 + it.minute } ?: parseChallengeTime(value.take(5))
}

fun workoutTypeName(type: String): String {
    val key = type.lowercase(Locale.ROOT).replace('_', ' ')
    return when {
        key in setOf("run", "running") || "running" in key -> "Running"
        key in setOf("walk", "walking") || "walking" in key -> "Walking"
        key in setOf("bike", "biking", "cycling") || "cycling" in key || "biking" in key -> "Cycling"
        "swim" in key -> "Swimming"
        "strength" in key || "weightlifting" in key -> "Strength training"
        "yoga" in key -> "Yoga"
        key.isBlank() || key.all(Char::isDigit) -> "Other workout"
        else -> key.replaceFirstChar(Char::titlecase)
    }
}
