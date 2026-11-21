package com.pulse.app.integration.googlehealth

import com.pulse.app.core.Metric
import com.pulse.app.core.Metric.*

internal enum class GoogleHealthCategory(val slug: String, val title: String, val description: String) {
    ACTIVITY("activity_and_fitness", "Activity & fitness", "Steps, workouts, exercise time and energy for goals and challenges."),
    VITALS("health_metrics_and_measurements", "Heart & body", "Heart rate, oxygen, temperature and other body measurements for history."),
    SLEEP("sleep", "Sleep", "Sleep duration, timing and stages for your sleep history and goals."),
    NUTRITION("nutrition", "Nutrition & hydration", "Food and water logs for your daily intake history.");

    val scope get() = "https://www.googleapis.com/auth/googlehealth.$slug.readonly"
}

internal enum class GoogleHealthMethod(val suffix: String, val responseKey: String) {
    LIST("", "dataPoints"),
    RECONCILE(":reconcile", "dataPoints"),
    DAILY_ROLLUP(":dailyRollUp", "rollupDataPoints")
}

internal enum class GoogleHealthTime(val field: String) {
    START("interval.civil_start_time"), END("interval.civil_end_time"),
    SAMPLE("sample_time.civil_time"), DAILY("date")
}

internal data class GoogleHealthType(
    val id: String,
    val category: GoogleHealthCategory,
    val method: GoogleHealthMethod,
    val time: GoogleHealthTime,
    val metrics: Set<Metric>
) {
    val dateFilter get() = time.field
    val payloadKey = id.split('-').let { words -> words.first() + words.drop(1).joinToString("") { it.replaceFirstChar(Char::uppercase) } }
}

internal object GoogleHealthMetrics {
    private val activity = GoogleHealthCategory.ACTIVITY
    private val vitals = GoogleHealthCategory.VITALS
    private val sleep = GoogleHealthCategory.SLEEP
    private val nutrition = GoogleHealthCategory.NUTRITION
    private val rollup = GoogleHealthMethod.DAILY_ROLLUP
    private val reconcile = GoogleHealthMethod.RECONCILE
    private val start = GoogleHealthTime.START
    private val end = GoogleHealthTime.END
    private val sample = GoogleHealthTime.SAMPLE
    private val daily = GoogleHealthTime.DAILY

    private fun type(id: String, scope: GoogleHealthCategory, method: GoogleHealthMethod, time: GoogleHealthTime, vararg metrics: Metric) =
        GoogleHealthType(id, scope, method, time, metrics.toSet())

    val types = listOf(
        type("steps", activity, rollup, start, STEPS),
        type("distance", activity, rollup, start, DISTANCE),
        type("active-minutes", activity, rollup, start, ACTIVE),
        type("active-zone-minutes", activity, rollup, start, ACTIVE_ZONE),
        type("total-calories", activity, rollup, start, CALORIES),
        type("basal-energy-burned", activity, reconcile, start, BASAL_CALORIES),
        type("active-energy-burned", activity, rollup, start, ACTIVE_CALORIES),
        type("floors", activity, rollup, start, FLOORS),
        type("exercise", activity, reconcile, start, ACTIVITIES, WORKOUT_DURATION, SWIM_LENGTHS),
        type("activity-level", activity, reconcile, start, LIGHT_ACTIVITY, MODERATE_ACTIVITY, VIGOROUS_ACTIVITY),
        type("sedentary-period", activity, rollup, start, SEDENTARY),
        type("altitude", activity, rollup, start, ELEVATION),
        type("time-in-heart-rate-zone", activity, rollup, start, HEART_ZONE_TIME),
        type("calories-in-heart-rate-zone", activity, rollup, start, HEART_ZONE_ENERGY),
        type("daily-vo2-max", activity, reconcile, daily, DAILY_VO2_MAX),
        type("vo2-max", activity, reconcile, sample, VO2_MAX),
        type("run-vo2-max", activity, rollup, sample, RUN_VO2_MAX),
        type("swim-lengths-data", activity, reconcile, start, SWIM_STROKES),
        type("sleep", sleep, reconcile, end, SLEEP, SLEEP_STAGES, TIME_IN_BED, SLEEP_AWAKE, SLEEP_PERIOD, SLEEP_LATENCY),
        type("heart-rate", vitals, rollup, sample, HEART),
        type("daily-resting-heart-rate", vitals, reconcile, daily, RESTING_HEART),
        type("daily-heart-rate-zones", vitals, reconcile, daily, HEART_ZONES),
        type("heart-rate-variability", vitals, reconcile, sample, HRV),
        type("daily-heart-rate-variability", vitals, reconcile, daily, DAILY_HRV),
        type("oxygen-saturation", vitals, reconcile, sample, SPO2),
        type("daily-oxygen-saturation", vitals, reconcile, daily, DAILY_SPO2),
        type("respiratory-rate-sleep-summary", vitals, reconcile, sample, RESPIRATORY_RATE),
        type("daily-respiratory-rate", vitals, reconcile, daily, DAILY_RESPIRATORY_RATE),
        type("daily-sleep-temperature-derivations", vitals, reconcile, daily, SLEEP_TEMPERATURE, TEMPERATURE_BASELINE),
        type("core-body-temperature", vitals, reconcile, sample, BODY_TEMPERATURE),
        type("body-fat", vitals, rollup, sample, BODY_FAT),
        type("weight", vitals, rollup, sample, WEIGHT),
        type("height", vitals, reconcile, sample, HEIGHT),
        type("blood-glucose", vitals, reconcile, sample, BLOOD_GLUCOSE),
        type("hydration-log", nutrition, rollup, start, HYDRATION),
        type("nutrition-log", nutrition, rollup, start, FOOD_ENERGY, CARBOHYDRATES, PROTEIN, FAT, FOOD_LOGS)
    )
    val metrics = types.flatMap { it.metrics }.toSet()
    val scopes = GoogleHealthCategory.entries.map { it.scope }.toSet()

    fun granted(scopes: Set<String>) = types.filter { it.category.scope in scopes }
}
