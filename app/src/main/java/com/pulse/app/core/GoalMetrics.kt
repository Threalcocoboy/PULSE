package com.pulse.app.core

import java.text.Normalizer
import java.time.ZoneId
import java.util.Locale

enum class MetricCategory(val title: String, val icon: String) {
    ACTIVITY("Activity", "dumbbell"), SLEEP("Sleep", "moon"), HEART("Heart & body", "heart"),
    RECOVERY("Recovery", "target"), LIFESTYLE("Nutrition & hydration", "dots")
}

enum class GoalAggregation(val title: String) { TOTAL("Total"), AVERAGE("Recorded average"), LATEST("Latest reading") }
enum class GoalCondition(val title: String) { MINIMUM("At least"), MAXIMUM("At most"), RANGE("Between"), TIME("Within") }
enum class GoalCadence(val title: String) { DAILY("Daily"), WEEKLY("Weekly") }
enum class GoalFrequency(val title: String) { EACH_PERIOD("Every period"), DAYS_PER_WEEK("Days per week"), STREAK_PER_WEEK("Consecutive days per week") }

data class GoalMetric(
    val id: String, val source: Metric, val title: String, val category: MetricCategory, val unit: String,
    val keywords: String = "", val description: String = "", val stage: String? = null, val sleepTiming: Boolean = false,
    val dailyOnly: Boolean = sleepTiming, val summable: Boolean = source.cumulative
) {
    val conditions get() = if (sleepTiming) listOf(GoalCondition.TIME) else listOf(GoalCondition.MINIMUM, GoalCondition.MAXIMUM, GoalCondition.RANGE)
    val cadences get() = if (dailyOnly) listOf(GoalCadence.DAILY) else GoalCadence.entries
    fun aggregations(cadence: GoalCadence) = when {
        sleepTiming -> listOf(GoalAggregation.LATEST)
        cadence == GoalCadence.WEEKLY && category == MetricCategory.SLEEP -> listOf(GoalAggregation.AVERAGE, GoalAggregation.TOTAL)
        summable -> listOf(GoalAggregation.TOTAL)
        cadence == GoalCadence.DAILY -> listOf(GoalAggregation.LATEST)
        else -> listOf(GoalAggregation.AVERAGE, GoalAggregation.LATEST)
    }
    private val searchable = normalize("$title $keywords ${category.title} $unit ${source.name.replace('_', ' ')}")
    fun matches(query: String) = normalize(query).split(Regex("\\s+")).all { it in searchable }

    fun value(day: DayData, zone: ZoneId): Double? {
        val reading = day.readings[source]
        if (source in setOf(Metric.ACTIVITIES, Metric.WORKOUT_DURATION) && reading?.state == DataState.EMPTY &&
            day.workoutsRead && day.activities.isEmpty()) return 0.0
        if (reading?.state != DataState.READY && !(reading?.state == DataState.EMPTY && (sleepTiming || stage != null))) return null
        val amount = when {
            stage != null -> day.sleepStages.entries.firstOrNull { it.key.equals(stage, ignoreCase = true) }?.value
            id == BEDTIME -> day.sleepStart?.atZone(zone)?.toLocalTime()?.toSecondOfDay()?.div(60)?.toDouble()
            id == WAKE_TIME -> day.sleepWakeMinute?.toDouble() ?: day.sleepEnd?.atZone(zone)?.toLocalTime()?.toSecondOfDay()?.div(60)?.toDouble()
            else -> reading?.value
        }
        return amount?.takeIf { it.isFinite() }
    }

    companion object {
        const val BEDTIME = "bedtime"
        const val WAKE_TIME = "wake_time"
        private fun normalize(value: String) = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}+"), "").trim()
    }
}

object GoalMetrics {
    private val sleep = setOf(Metric.SLEEP, Metric.TIME_IN_BED, Metric.SLEEP_PERIOD, Metric.SLEEP_LATENCY, Metric.SLEEP_AWAKE)
    private val heart = setOf(Metric.HEART, Metric.RESTING_HEART, Metric.HRV, Metric.DAILY_HRV, Metric.SPO2, Metric.DAILY_SPO2,
        Metric.RESPIRATORY_RATE, Metric.DAILY_RESPIRATORY_RATE, Metric.BODY_TEMPERATURE, Metric.SLEEP_TEMPERATURE,
        Metric.TEMPERATURE_BASELINE, Metric.WEIGHT, Metric.BODY_FAT, Metric.HEIGHT, Metric.BLOOD_GLUCOSE)
    private val lifestyle = setOf(Metric.HYDRATION, Metric.FOOD_ENERGY, Metric.CARBOHYDRATES, Metric.PROTEIN, Metric.FAT, Metric.FOOD_LOGS)
    private val recovery = setOf(Metric.STRESS, Metric.BATTERY)
    private val titles = mapOf(Metric.ACTIVITIES to "Workout count", Metric.SLEEP to "Sleep duration", Metric.RESTING_HEART to "Resting heart rate",
        Metric.HRV to "Heart-rate variability (HRV)", Metric.DAILY_HRV to "Daily heart-rate variability (HRV)", Metric.CALORIES to "Total calories burned")
    private val aliases = mapOf(Metric.STEPS to "walking pedometer", Metric.DISTANCE to "miles kilometers km mi", Metric.ACTIVITIES to "training exercise session frequency workouts",
        Metric.WORKOUT_DURATION to "exercise time training sessions", Metric.HEART to "pulse heart rate bpm", Metric.RESTING_HEART to "rhr pulse bpm",
        Metric.HRV to "heart rate variability rmssd", Metric.DAILY_HRV to "heart rate variability rmssd", Metric.SPO2 to "spo2 blood oxygen",
        Metric.DAILY_SPO2 to "spo2 blood oxygen", Metric.RESPIRATORY_RATE to "breathing respiration", Metric.DAILY_RESPIRATORY_RATE to "breathing respiration",
        Metric.ACTIVE_ZONE to "azm exercise", Metric.FLOORS to "stairs climbing", Metric.BODY_FAT to "composition percentage", Metric.WEIGHT to "body mass",
        Metric.HYDRATION to "water fluid drink", Metric.FOOD_ENERGY to "nutrition calories diet", Metric.SLEEP_AWAKE to "awake time wake after sleep onset",
        Metric.CARBOHYDRATES to "carbs macros", Metric.PROTEIN to "macros", Metric.FAT to "macros", Metric.BLOOD_GLUCOSE to "blood sugar")
    private val descriptions = mapOf(Metric.SLEEP to "Counts toward the date you woke up.", Metric.ACTIVE to "Uses your provider’s definition of active time.",
        Metric.ACTIVE_ZONE to "Zone-weighted minutes recorded by Google Health.", Metric.ACTIVITIES to "Counts workouts on the date they started.",
        Metric.WORKOUT_DURATION to "Minutes from workouts that started that day.", Metric.HEART to "Uses the provider’s daily average.",
        Metric.BATTERY to "Uses the daily summary reported by Garmin.", Metric.STRESS to "Uses the daily score reported by Garmin.")
    private val excluded = setOf(Metric.GOAL, Metric.WEEKLY, Metric.TREND, Metric.WEEKLY_ACTIVE, Metric.SLEEP_STAGES, Metric.HEART_ZONES)
    val entries = Metric.entries.filterNot { it in excluded }.map { metric ->
        val category = when (metric) {
            in sleep -> MetricCategory.SLEEP
            in heart -> MetricCategory.HEART
            in lifestyle -> MetricCategory.LIFESTYLE
            in recovery -> MetricCategory.RECOVERY
            else -> MetricCategory.ACTIVITY
        }
        GoalMetric(metric.name, metric, titles[metric] ?: metric.label, category, metric.unit,
            aliases[metric].orEmpty(), descriptions[metric].orEmpty(), summable = metric.cumulative || metric in sleep)
    } + listOf("Deep", "REM", "Light", "Awake").map { stage ->
        GoalMetric("stage_${stage.lowercase(Locale.ROOT)}", Metric.SLEEP_STAGES,
            if (stage == "Awake") "Awake sleep stage" else "$stage sleep duration", MetricCategory.SLEEP, "h",
            description = "Uses the sleep stage reported by your provider.", stage = stage, summable = true)
    } + listOf(
        GoalMetric(GoalMetric.BEDTIME, Metric.SLEEP, "Bedtime consistency", MetricCategory.SLEEP, "HH:mm", "sleep schedule sleep consistency",
            "Compares recorded sleep start with your chosen time.", sleepTiming = true),
        GoalMetric(GoalMetric.WAKE_TIME, Metric.SLEEP, "Wake-up consistency", MetricCategory.SLEEP, "HH:mm", "wake time sleep schedule sleep consistency",
            "Compares recorded wake-up time with your chosen time.", sleepTiming = true)
    )
    private val byId = entries.associateBy { it.id }
    operator fun get(id: String) = byId[id]
}
