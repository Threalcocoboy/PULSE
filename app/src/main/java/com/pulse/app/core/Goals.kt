package com.pulse.app.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.math.abs

data class PersonalGoal(
    val id: String = UUID.randomUUID().toString(), val metricId: String, val target: Double,
    val upper: Double? = null, val cadence: GoalCadence = GoalCadence.DAILY,
    val aggregation: GoalAggregation = GoalAggregation.LATEST, val condition: GoalCondition = GoalCondition.MINIMUM,
    val frequency: GoalFrequency = GoalFrequency.EACH_PERIOD, val requiredDays: Int = 1,
    val toleranceMinutes: Int = 30, val archived: Boolean = false
) {
    val metric get() = requireNotNull(GoalMetrics[metricId])
    val completionCadence get() = if (frequency == GoalFrequency.EACH_PERIOD) cadence else GoalCadence.WEEKLY
    fun valid(): Boolean {
        val definition = GoalMetrics[metricId] ?: return false
        if (cadence !in definition.cadences || aggregation !in definition.aggregations(cadence) || condition !in definition.conditions) return false
        if (frequency != GoalFrequency.EACH_PERIOD && (cadence != GoalCadence.DAILY || requiredDays !in 1..7)) return false
        if (!target.isFinite() || !id.matches(Regex("[A-Za-z0-9_-]{1,80}"))) return false
        if (condition == GoalCondition.TIME) return target in 0.0..1439.0 && toleranceMinutes in 1..180
        val allowsNegative = definition.source in setOf(Metric.BODY_TEMPERATURE, Metric.SLEEP_TEMPERATURE, Metric.TEMPERATURE_BASELINE, Metric.ELEVATION)
        if (!allowsNegative && target < 0.0) return false
        if (definition.source in setOf(Metric.STEPS, Metric.ACTIVITIES, Metric.SWIM_STROKES, Metric.SWIM_LENGTHS, Metric.FOOD_LOGS) &&
            (target > Int.MAX_VALUE || target % 1.0 != 0.0 || upper?.let { it % 1.0 != 0.0 } == true)) return false
        return condition != GoalCondition.RANGE || upper != null && upper.isFinite() && upper > target
    }
}

data class GoalProgress(val value: Double?, val met: Boolean?, val closed: Boolean)

object Goals {
 val supported=setOf(Metric.STEPS,Metric.SLEEP,Metric.DISTANCE,Metric.ACTIVE,Metric.ACTIVITIES,Metric.WEEKLY_ACTIVE)
 fun available(mode:DataMode) = if (mode == DataMode.NONE) supported - Metric.DISTANCE else supported
 fun label(metric: Metric) = if (metric == Metric.ACTIVITIES) "Workouts per week" else metric.label
 fun weekly(metric: Metric) = metric in setOf(Metric.ACTIVITIES, Metric.WEEKLY_ACTIVE)
 fun sourceMetric(metric: Metric) = if (metric == Metric.WEEKLY_ACTIVE) Metric.ACTIVE else metric
 fun target(m:Metric,p:Preferences)=when(m){Metric.STEPS->p.stepGoal.toDouble();Metric.SLEEP->p.sleepGoal;Metric.DISTANCE->p.distanceGoal;Metric.ACTIVE->p.activeGoal.toDouble();Metric.ACTIVITIES->p.workoutGoal.toDouble();Metric.WEEKLY_ACTIVE->p.weeklyActiveGoal.toDouble();else->1.0}
 fun value(m: Metric, days: List<DayData>, today: LocalDate): Double? {
     val week = weekly(m)
     val range = (if (week) today.with(java.time.DayOfWeek.MONDAY) else today)..today
     val values = HistoryMath.unique(days).filter { it.date in range }.mapNotNull { day ->
         day.readings[sourceMetric(m)]?.takeIf { it.state == DataState.READY ||
             it.state == DataState.STALE && it.provider != DataMode.GOOGLE_HEALTH }?.value
     }
     return values.takeIf { it.isNotEmpty() }?.let { if (week) it.sum() else it.last() }
 }

    fun legacyId(metric: Metric) = "legacy_${metric.name}"

    private fun legacyGoal(metric: Metric, p: Preferences): PersonalGoal {
        val cadence = if (weekly(metric)) GoalCadence.WEEKLY else GoalCadence.DAILY
        val definition = requireNotNull(GoalMetrics[sourceMetric(metric).name])
        return PersonalGoal(legacyId(metric), definition.id, target(metric, p), cadence = cadence,
            aggregation = definition.aggregations(cadence).first())
    }

    fun configurations(p: Preferences): List<PersonalGoal> {
        val overrides = p.personalGoals.associateBy { it.id }
        val legacy = p.enabledGoals.map { overrides[legacyId(it)] ?: legacyGoal(it, p) }
        return (legacy + p.personalGoals).distinctBy { it.id }
    }

    fun active(p: Preferences) = configurations(p).filterNot { it.archived }

    fun save(p: Preferences, goal: PersonalGoal): Preferences {
        require(goal.valid())
        val existing = p.personalGoals.indexOfFirst { it.id == goal.id }
        val list = p.personalGoals.toMutableList().apply { if (existing >= 0) this[existing] = goal else add(goal) }
        val updated = p.copy(personalGoals = list)
        return updated.copy(goalsSetup = if (active(updated).isEmpty()) SetupStatus.SKIPPED else SetupStatus.COMPLETE)
    }

    fun indicator(metric: Metric, p: Preferences): Double? = active(p).firstOrNull {
        it.metricId == metric.name && it.completionCadence == GoalCadence.DAILY && it.condition == GoalCondition.MINIMUM && it.target > 0
    }?.target

    fun current(goal: PersonalGoal, snapshot: HealthSnapshot, today: LocalDate, zone: ZoneId): GoalProgress {
        if (!snapshot.access.verified || goal.metric.source !in snapshot.available) return GoalProgress(null, null, false)
        val start = if (goal.completionCadence == GoalCadence.WEEKLY) today.with(DayOfWeek.MONDAY) else today
        return progress(goal, HistoryMath.unique(snapshot.days).associateBy { it.date }, start, today, zone)
    }

    fun progress(goal: PersonalGoal, days: Map<LocalDate, DayData>, start: LocalDate, today: LocalDate, zone: ZoneId): GoalProgress {
        if (goal.frequency == GoalFrequency.EACH_PERIOD) return measure(goal, days, start, today, zone)
        val end = start.plusDays(6)
        val results = generateSequence(start) { it.plusDays(1) }.takeWhile { it <= minOf(end, today) }
            .map { measure(goal, days, it, today, zone).met }.toList()
        var run = 0
        var best = 0
        for (met in results) {
            run = if (met == true) run + 1 else 0
            best = maxOf(best, run)
        }
        val count = if (goal.frequency == GoalFrequency.DAYS_PER_WEEK) results.count { it == true } else best
        val closed = end < today && results.none { it == null }
        return GoalProgress(count.toDouble().takeIf { results.any { it != null } },
            if (count >= goal.requiredDays) true else if (closed) false else null, closed)
    }

    private fun measure(goal: PersonalGoal, days: Map<LocalDate, DayData>, start: LocalDate, today: LocalDate, zone: ZoneId): GoalProgress {
        val end = if (goal.cadence == GoalCadence.WEEKLY) start.plusDays(6) else start
        val metric = goal.metric
        val records = generateSequence(start) { it.plusDays(1) }.takeWhile { it <= minOf(end, today) }.map { days[it] }.toList()
        val values = records.map { it?.let { day -> metric.value(day, zone) } }
        val known = values.filterNotNull()
        if (known.isEmpty()) return GoalProgress(null, null, false)
        val amount = when (goal.aggregation) {
            GoalAggregation.TOTAL -> known.sum()
            GoalAggregation.AVERAGE -> known.average()
            GoalAggregation.LATEST -> known.last()
        }
        val completeCoverage = end < today && values.all { it != null } && records.all { it?.closedFor(metric.source, today, zone) == true }
        val closed = if (goal.cadence == GoalCadence.WEEKLY && goal.aggregation != GoalAggregation.TOTAL) {
            val latest = records.lastOrNull { it?.let { day -> metric.value(day, zone) } != null }
            end < today && latest?.closedFor(metric.source, today, zone) == true
        } else completeCoverage
        val met = when (goal.condition) {
            GoalCondition.TIME -> abs(amount - goal.target).let { minOf(it, 1440 - it) <= goal.toleranceMinutes }
            GoalCondition.MINIMUM -> when {
                goal.cadence == GoalCadence.DAILY && (metric.summable || metric.category == MetricCategory.SLEEP) && amount >= goal.target -> true
                goal.aggregation == GoalAggregation.TOTAL && amount >= goal.target && values.all { it == null || it >= 0.0 } -> true
                closed -> amount >= goal.target
                else -> null
            }
            GoalCondition.MAXIMUM -> when {
                closed -> amount <= goal.target
                goal.aggregation == GoalAggregation.TOTAL && amount > goal.target && values.all { it == null || it >= 0.0 } -> false
                else -> null
            }
            GoalCondition.RANGE -> if (closed) amount in goal.target..requireNotNull(goal.upper) else null
        }
        return GoalProgress(amount, met, closed)
    }
}

object PersonalGoalCodec {
    fun encode(goals: List<PersonalGoal>) = goals.joinToString(";") {
        listOf(it.id, it.metricId, it.target, it.upper ?: "", it.cadence, it.aggregation, it.condition,
            it.frequency, it.requiredDays, it.toleranceMinutes, it.archived).joinToString(",")
    }

    fun decode(value: String) = value.split(';').mapNotNull { record ->
        runCatching {
            val fields = record.split(',')
            PersonalGoal(fields[0], fields[1], fields[2].toDouble(), fields[3].toDoubleOrNull(), GoalCadence.valueOf(fields[4]),
                GoalAggregation.valueOf(fields[5]), GoalCondition.valueOf(fields[6]), GoalFrequency.valueOf(fields[7]),
                fields[8].toInt(), fields[9].toInt(), fields[10].toBooleanStrict()).takeIf(PersonalGoal::valid)
        }.getOrNull()
    }.distinctBy { it.id }
}
