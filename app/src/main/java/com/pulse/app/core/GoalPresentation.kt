package com.pulse.app.core

import java.text.NumberFormat
import java.time.LocalTime

internal fun GoalMetric.scale(imperial: Boolean) = if (source == Metric.DISTANCE && imperial) .621371 else 1.0
internal fun GoalMetric.displayUnit(imperial: Boolean) = if (source == Metric.DISTANCE && imperial) "mi" else unit

internal fun localGoalTime(value: Double) = LocalTime.ofSecondOfDay(value.toLong() * 60).toString()

internal fun GoalMetric.amount(value: Double, imperial: Boolean) = if (sleepTiming) localGoalTime(value)
    else "${NumberFormat.getNumberInstance().apply { maximumFractionDigits = 3 }.format(value * scale(imperial))} ${displayUnit(imperial)}"

internal fun PersonalGoal.summary(imperial: Boolean): String {
    val scope = when {
        condition == GoalCondition.TIME -> "Within $toleranceMinutes min of ${localGoalTime(target)}"
        condition == GoalCondition.RANGE -> "${condition.title} ${metric.amount(target, imperial)} and ${metric.amount(requireNotNull(upper), imperial)}"
        else -> "${condition.title} ${metric.amount(target, imperial)}"
    }
    val period = when (frequency) {
        GoalFrequency.DAYS_PER_WEEK -> "$requiredDays ${if (requiredDays == 1) "day" else "days"} per week"
        GoalFrequency.STREAK_PER_WEEK -> "$requiredDays consecutive ${if (requiredDays == 1) "day" else "days"} per week"
        GoalFrequency.EACH_PERIOD -> if (cadence == GoalCadence.DAILY) "Daily" else "Weekly ${aggregation.title.lowercase()}"
    }
    return "$scope · $period"
}

internal fun PersonalGoal.progressText(progress: GoalProgress, imperial: Boolean): String = progress.value?.let { value ->
    if (frequency == GoalFrequency.EACH_PERIOD) metric.amount(value, imperial) else "${value.toInt()} / $requiredDays days"
} ?: "No readings"

