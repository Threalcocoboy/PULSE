package com.pulse.app.core

import java.time.LocalDate
import java.time.LocalTime
import java.time.DayOfWeek
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.ZoneId
import java.util.UUID
import java.text.NumberFormat

enum class ChallengeTracking { AUTOMATIC, CHECKBOX, NUMERIC, PERSONAL_GOALS;
    val automatic get() = this == AUTOMATIC || this == PERSONAL_GOALS
}
enum class GoalMatch { ANY, ALL }
enum class ChallengeRule {
    FLEXIBLE, STREAK, CUMULATIVE, ONE_TIME;

    val countsDays: Boolean get() = this == FLEXIBLE || this == STREAK
}

enum class ChallengeIssue(val message: String) {
    NAME("Enter a challenge name."),
    CATEGORY("Enter a category."),

    METRIC("Choose a supported health metric."),
    UNIT("Enter a unit."),
    GOALS("Choose at least one goal with the same daily or weekly period."),
    CONDITION("Choose a condition that fits this measurement."),
    QUALIFIER("Choose a workout type or sleep stage."),
    TIME("Enter a time in 24-hour format, such as 07:30."),
    RANGE("At least seven recent readings are needed to set your range."),
    SLEEP_RULE("Choose a rule with a daily sleep goal."),
    TARGET("Enter a number greater than zero."),
    DURATION("Choose a duration from 1 to 366 days."),
    REQUIRED_DAYS("Choose a number from 1 to the challenge duration."),
    START_DATE("Choose today or a future date.")
}

fun formatChallengeAmount(value: Double): String = NumberFormat.getNumberInstance().apply {
    maximumFractionDigits = 340
}.format(value)
enum class ChallengeStatus { SCHEDULED, ACTIVE, PAUSED, AWAITING_DATA, COMPLETED, EXPIRED, ARCHIVED }
data class ChallengeEvent(val id: String = UUID.randomUUID().toString(), val date: LocalDate, val amount: Double)
data class PersonalChallenge(
    val id: String = UUID.randomUUID().toString(), val name: String, val description: String = "",
    val category: String = "Custom", val icon: String = "medal",
    val tracking: ChallengeTracking = ChallengeTracking.CHECKBOX, val metric: Metric = Metric.STEPS,
    val rule: ChallengeRule = ChallengeRule.CUMULATIVE, val target: Double = 5.0, val unit: String = "times",
    val duration: Int = 7, val requiredDays: Int = 5, val start: LocalDate = LocalDate.now(),
    val zone: String = ZoneId.systemDefault().id, val source: DataMode = DataMode.HEALTH_CONNECT,
    val events: List<ChallengeEvent> = emptyList(), val status: ChallengeStatus = ChallengeStatus.ACTIVE,
    val progress: Double = 0.0, val completed: LocalDate? = null, val rewardAcknowledged: Boolean = false,
    val measure: ChallengeMeasure? = null, val condition: ChallengeCondition = ChallengeCondition.AT_LEAST,
    val qualifier: String? = null, val timeMinutes: Int? = null, val toleranceMinutes: Int = 30,
    val rangeMin: Double? = null, val rangeMax: Double? = null, val goals: Set<Metric> = emptySet(),
    val goalMatch: GoalMatch = GoalMatch.ALL, val sourceScope: String? = null,
    val goalIds: Set<String> = emptySet(), val goalCadence: GoalCadence? = null, val goalTitles: List<String> = emptyList()
) {
    val end: LocalDate get() = start.plusDays(duration - 1L)
    val measurement get() = measure ?: ChallengeMeasure.forMetric(metric)
    val goalReferences get() = goalIds.ifEmpty { goals.map(Goals::legacyId).toSet() }
    val weeklyGoals get() = goalCadence == GoalCadence.WEEKLY || goalCadence == null && goals.isNotEmpty() && goals.all(Goals::weekly)
    val periodUnit get() = if (weeklyGoals) "weeks" else "days"
    fun linkedGoals(p: Preferences): List<PersonalGoal> {
        val cadence = if (weeklyGoals) GoalCadence.WEEKLY else GoalCadence.DAILY
        return Goals.active(p).filter { it.id in goalReferences && it.completionCadence == cadence }
    }
    val goal: Double get() = when {
        rule.countsDays -> requiredDays.toDouble()
        tracking.automatic && condition != ChallengeCondition.AT_LEAST -> 1.0
        else -> target
    }
    fun weekStarts(): List<LocalDate> = generateSequence(start.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))) { it.plusWeeks(1) }
        .takeWhile { it.plusDays(6) <= end }.toList()
    val rewardId: String get() = "challenge_$id"

    fun validationIssues(): List<ChallengeIssue> = buildList {
        if (name.isBlank()) add(ChallengeIssue.NAME)
        if (category.isBlank()) add(ChallengeIssue.CATEGORY)
        if (tracking == ChallengeTracking.AUTOMATIC) {
            val m = measurement
            if (m == null || m.metric != metric) add(ChallengeIssue.METRIC)
            else {
                if (condition !in m.conditions || rule !in m.rules(condition)) add(
                    if (metric == Metric.SLEEP && rule == ChallengeRule.CUMULATIVE) ChallengeIssue.SLEEP_RULE else ChallengeIssue.CONDITION)
                if (m == ChallengeMeasure.SLEEP_STAGE && qualifier !in ChallengeMeasure.stages ||
                    m == ChallengeMeasure.WORKOUT_TYPE && qualifier.isNullOrBlank()) add(ChallengeIssue.QUALIFIER)
                if (m.timeBased && ((timeMinutes == null || timeMinutes !in 0..1439) || toleranceMinutes !in 5..180)) add(ChallengeIssue.TIME)
                if (condition == ChallengeCondition.PERSONAL_RANGE &&
                    (rangeMin == null || rangeMax == null || !rangeMin.isFinite() || !rangeMax.isFinite() || rangeMin > rangeMax)) add(ChallengeIssue.RANGE)
            }
        }
        if (tracking == ChallengeTracking.PERSONAL_GOALS &&
            (goalReferences.isEmpty() || goalIds.isNotEmpty() && goalCadence == null || goals.any { it !in Goals.supported } ||
                goals.any(Goals::weekly) && !weeklyGoals || !rule.countsDays)) add(ChallengeIssue.GOALS)
        if (unit.isBlank()) add(ChallengeIssue.UNIT)
        if (!target.isFinite() || target <= 0) add(ChallengeIssue.TARGET)
        if (duration !in 1..366) add(ChallengeIssue.DURATION)
        if (rule.countsDays && requiredDays !in 1..(if (tracking == ChallengeTracking.PERSONAL_GOALS && weeklyGoals &&
            duration in 1..366) weekStarts().size else duration)) add(ChallengeIssue.REQUIRED_DAYS)
    }

    fun validation(): String? = validationIssues().firstOrNull()?.message

    fun summary(): String {
        val action = when {
            tracking == ChallengeTracking.PERSONAL_GOALS -> {
                val names = goalTitles.ifEmpty { goals.map(Goals::label) }.joinToString(", ")
                if (goalReferences.size == 1) "Reach your $names goal" else "Reach ${goalMatch.name.lowercase()} of your selected goals ($names)"
            }
            tracking != ChallengeTracking.AUTOMATIC -> null
            measurement == ChallengeMeasure.WORKOUT_TYPE -> "Complete a ${qualifier.orEmpty().lowercase()} workout"
            measurement == ChallengeMeasure.WORKOUT_START -> "Start a workout by ${timeText()}"
            measurement == ChallengeMeasure.SLEEP_STAGE -> "Record ${qualifier.orEmpty().lowercase()} sleep"
            measurement == ChallengeMeasure.SLEEP_SCHEDULE -> "Wake within $toleranceMinutes minutes of ${timeText()}"
            condition == ChallengeCondition.RECORDED -> "Record ${measurement?.title ?: metric.label}"
            condition == ChallengeCondition.PERSONAL_RANGE -> "Record ${measurement?.title ?: metric.label} within your recent range"
            condition == ChallengeCondition.BELOW -> "Stay below ${formatChallengeAmount(target)} $unit of ${measurement?.title?.lowercase() ?: metric.label}"
            else -> null
        }
        if (action != null) {
            val period = if (requiredDays == 1) periodUnit.dropLast(1) else periodUnit
            return when (rule) {
                ChallengeRule.FLEXIBLE -> "$action on $requiredDays $period."
                ChallengeRule.STREAK -> "$action for $requiredDays consecutive $period."
                else -> "$action on a day within the challenge."
            }
        }
        val amount = formatChallengeAmount(target)
        val quantity = if (target == 1.0) {
            when (unit) {
                "times" -> "1 time"
                "pages" -> "1 page"
                "minutes" -> "1 minute"
                "glasses" -> "1 glass"
                "steps" -> "1 step"
                else -> "1 $unit"
            }
        } else "$amount $unit"
        val period = if (duration == 1) "1 day" else "$duration days"
        val days = if (requiredDays == 1) "1 day" else "$requiredDays days"
        val descriptor = measure?.title?.lowercase()?.takeIf { tracking == ChallengeTracking.AUTOMATIC }
        val measured = if (descriptor == null || quantity.endsWith(descriptor)) quantity else "$quantity ($descriptor)"
        return when (rule) {
            ChallengeRule.FLEXIBLE -> "Reach $measured on $days out of $period."
            ChallengeRule.STREAK -> if (requiredDays == 1) "Reach $measured on one day within $period."
                else "Reach $measured on $requiredDays days in a row within $period."
            ChallengeRule.CUMULATIVE -> if (tracking == ChallengeTracking.CHECKBOX && unit == "times")
                "Check off $quantity within $period." else "Record a total of $measured within $period."
            ChallengeRule.ONE_TIME -> "Reach $measured on a single day within $period."
        }
    }
    private fun timeText() = timeMinutes?.let { LocalTime.of(it / 60, it % 60).toString() }.orEmpty()

    fun reward() = UserAchievement(AchievementDefinition(rewardId, AchievementCategory.SPECIAL, name,
        "Challenge reward · $category", summary(), goal, if (rule.countsDays) periodUnit else unit,
        icon, category), progress, completed != null, completed, "${formatChallengeAmount(progress)} / ${formatChallengeAmount(goal)}")
}

object ChallengeEvaluator {
    val metrics = ChallengeMeasure.entries.map { it.metric }.toSet()

    fun personalRange(snapshot: HealthSnapshot, measure: ChallengeMeasure, today: LocalDate, zone: ZoneId): Pair<Double, Double>? {
        val values = HistoryMath.unique(snapshot.days).filter { it.date >= today.minusDays(28) && it.closedFor(measure.metric, today, zone) }
            .mapNotNull { it.readings[measure.metric].challengeValue() }.sorted()
        if (values.size < 7) return null
        return values[(values.lastIndex * .25).toInt()] to values[kotlin.math.ceil(values.lastIndex * .75).toInt()]
    }

    private fun compatible(c: PersonalChallenge, s: HealthSnapshot): Boolean {
        if (s.mode != c.source || s.mode == DataMode.NONE || s.loading || s.error != null && !s.access.verified) return false
        if (c.sourceScope != null) return c.sourceScope == s.sourceScope
        return when (c.source) {
            DataMode.GARMIN -> s.sourceScope == null || s.sourceScope == "GARMIN_ACCOUNT_LOCAL"
            DataMode.HEALTH_CONNECT -> c.zone == ZoneId.systemDefault().id
            DataMode.GOOGLE_HEALTH -> false
            DataMode.NONE -> false
        }
    }

    private fun hasMetrics(c: PersonalChallenge, s: HealthSnapshot): Boolean =
        !s.access.verified || when (c.tracking) {
            ChallengeTracking.AUTOMATIC -> c.measurement?.metric in s.available
            else -> true
        }

    private fun usableGoals(c: PersonalChallenge, s: HealthSnapshot, p: Preferences) = c.linkedGoals(p)
        .filter { !s.access.verified || it.metric.source in s.available }

    fun periods(c: PersonalChallenge, s: HealthSnapshot, today: LocalDate, p: Preferences): List<ChallengePeriod> {
        if (today < c.start) return emptyList()
        val zone = ZoneId.of(c.zone)
        val source = s.secondary?.takeIf { it.mode == c.source } ?: s
        val linked = if (c.tracking == ChallengeTracking.PERSONAL_GOALS) usableGoals(c, source, p) else emptyList()
        val days = if (compatible(c, source) && hasMetrics(c, source))
            HistoryMath.unique(source.days).associateBy { it.date } else emptyMap()
        if (c.tracking == ChallengeTracking.PERSONAL_GOALS && c.weeklyGoals) {
            return c.weekStarts().filter { it <= today }.map { date ->
                goalPeriod(c, linked, days, date, today, zone)
            }
        }
        val last = minOf(today, c.end)
        return (0..ChronoUnit.DAYS.between(c.start, last)).map { offset ->
            val date = c.start.plusDays(offset)
            when (c.tracking) {
                ChallengeTracking.PERSONAL_GOALS -> goalPeriod(c, linked, days, date, today, zone)
                ChallengeTracking.AUTOMATIC -> measurementPeriod(c, days[date], date, today, zone)
                else -> {
                    val amount = c.events.distinctBy { it.id }.filter { it.date == date }.sumOf { it.amount }
                    ChallengePeriod(date, amount, amount >= c.target, date < today)
                }
            }
        }
    }

    private fun measurementPeriod(c: PersonalChallenge, day: DayData?, date: LocalDate, today: LocalDate, zone: ZoneId): ChallengePeriod {
        val measure = c.measurement
        if (day == null || measure == null) return ChallengePeriod(date, null, null, false)
        val base = workoutValue(day, measure.metric)
        val closed = day.closedFor(measure.metric, today, zone)
        val amount = if (base == null) null else when (measure) {
            ChallengeMeasure.SLEEP_STAGE -> day.sleepStages.entries
                .firstOrNull { it.key.equals(c.qualifier, ignoreCase = true) }?.value
            ChallengeMeasure.SLEEP_SCHEDULE -> {
                val wake = day.sleepEnd?.atZone(zone)?.toLocalTime()
                day.sleepWakeMinute?.toDouble() ?: wake?.let { (it.hour * 60 + it.minute).toDouble() }
            }
            ChallengeMeasure.WORKOUT_TYPE -> day.activities.distinctBy { it.id }
                .count { workoutTypeName(it.type) == c.qualifier }.toDouble()
            ChallengeMeasure.WORKOUT_START -> {
                val times = day.activities.mapNotNull { it.minuteOfDay() }
                if (times.isEmpty() && base > 0) null else times.minOrNull()?.toDouble() ?: 1440.0
            }
            else -> base
        }
        val met = amount?.let {
            when (measure) {
                ChallengeMeasure.WORKOUT_TYPE, ChallengeMeasure.SLEEP_STAGE -> it > 0
                ChallengeMeasure.WORKOUT_START -> c.timeMinutes?.let { time -> it <= time }
                ChallengeMeasure.SLEEP_SCHEDULE -> c.timeMinutes?.let { time ->
                    val difference = kotlin.math.abs(it - time)
                    minOf(difference, 1440 - difference) <= c.toleranceMinutes
                }
                else -> when (c.condition) {
                    ChallengeCondition.AT_LEAST -> it >= c.target
                    ChallengeCondition.BELOW -> if (closed) it < c.target else null
                    ChallengeCondition.RECORDED -> true
                    ChallengeCondition.PERSONAL_RANGE -> if (closed) {
                        val lower = c.rangeMin
                        val upper = c.rangeMax
                        if (lower == null || upper == null) null else it in lower..upper
                    } else null
                }
            }
        }
        return ChallengePeriod(date, amount, met, closed)
    }

    private fun workoutValue(day: DayData, metric: Metric): Double? {
        val reading = day.readings[metric]
        return reading.challengeValue() ?: 0.0.takeIf {
            day.workoutsRead && day.activities.isEmpty() && reading?.state == DataState.EMPTY &&
                metric in setOf(Metric.ACTIVITIES, Metric.WORKOUT_DURATION)
        }
    }

    private fun goalPeriod(c: PersonalChallenge, goals: List<PersonalGoal>, days: Map<LocalDate, DayData>,
        start: LocalDate, today: LocalDate, zone: ZoneId): ChallengePeriod {
        val outcomes = goals.map { Goals.progress(it, days, start, today, zone) }
        val results = outcomes.map { it.met }
        val allAvailable = outcomes.size == c.goalReferences.size && outcomes.isNotEmpty()
        val met = if (c.goalMatch == GoalMatch.ALL) when {
            allAvailable && results.all { it == true } -> true
            results.any { it == false } -> false
            else -> null
        } else when {
            results.any { it == true } -> true
            allAvailable && results.all { it == false } -> false
            else -> null
        }
        return ChallengePeriod(start, results.count { it == true }.toDouble().takeIf { outcomes.any { it.value != null } },
            met, allAvailable && outcomes.all { it.closed })
    }

    fun evaluate(c: PersonalChallenge, snapshot: HealthSnapshot, today: LocalDate, p: Preferences = Preferences()): PersonalChallenge {
        if (c.completed != null || c.status in setOf(ChallengeStatus.PAUSED, ChallengeStatus.ARCHIVED)) return c
        if (today < c.start) return c.copy(status = ChallengeStatus.SCHEDULED, progress = 0.0)
        val source = snapshot.secondary?.takeIf { it.mode == c.source } ?: snapshot
        if (c.tracking.automatic && snapshot.loading) return c
        val usable = if (c.tracking == ChallengeTracking.PERSONAL_GOALS) usableGoals(c, source, p).size else 0
        if (c.tracking.automatic && (!compatible(c, source) || !hasMetrics(c, source) ||
            c.tracking == ChallengeTracking.PERSONAL_GOALS && (usable == 0 || c.goalMatch == GoalMatch.ALL && usable != c.goalReferences.size))) {
            return c.copy(status = ChallengeStatus.AWAITING_DATA)
        }
        val values = periods(c, snapshot, today, p)
        var run = 0
        var best = 0
        values.forEach {
            run = if (it.met == true) run + 1 else 0
            best = maxOf(best, run)
        }
        val progress = when (c.rule) {
            ChallengeRule.FLEXIBLE -> values.count { it.met == true }.toDouble()
            ChallengeRule.STREAK -> best.toDouble()
            ChallengeRule.CUMULATIVE -> values.mapNotNull { it.value }.sum()
            ChallengeRule.ONE_TIME -> if (c.tracking.automatic && c.condition != ChallengeCondition.AT_LEAST) {
                if (values.any { it.met == true }) 1.0 else 0.0
            } else values.mapNotNull { it.value }.maxOrNull() ?: 0.0
        }
        val success = progress >= c.goal
        val unknown = c.tracking.automatic && (values.any { it.met == null && (it.date < today || it.value == null) } ||
            today > c.end && values.any { !it.closed })
        return c.copy(progress = progress, completed = if (success) today else null, status = when {
            success -> ChallengeStatus.COMPLETED
            unknown -> ChallengeStatus.AWAITING_DATA
            today > c.end -> ChallengeStatus.EXPIRED
            else -> ChallengeStatus.ACTIVE
        })
    }
}
