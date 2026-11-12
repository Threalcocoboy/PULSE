package com.pulse.app.core

import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.text.NumberFormat

enum class ChallengeTracking { AUTOMATIC, CHECKBOX, NUMERIC }
enum class ChallengeRule {
    FLEXIBLE, STREAK, CUMULATIVE, ONE_TIME;

    val countsDays: Boolean get() = this == FLEXIBLE || this == STREAK
}

enum class ChallengeIssue(val message: String) {
    NAME("Enter a challenge name."),
    CATEGORY("Enter a category."),

    METRIC("Choose a supported health metric."),
    UNIT("Enter a unit."),
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
    val progress: Double = 0.0, val completed: LocalDate? = null, val rewardAcknowledged: Boolean = false
) {
    val end: LocalDate get() = start.plusDays(duration - 1L)
    val goal: Double get() = if (rule.countsDays) requiredDays.toDouble() else target
    val rewardId: String get() = "challenge_$id"

    fun validationIssues(): List<ChallengeIssue> = buildList {
        if (name.isBlank()) add(ChallengeIssue.NAME)
        if (category.isBlank()) add(ChallengeIssue.CATEGORY)
        if (tracking == ChallengeTracking.AUTOMATIC && metric !in ChallengeEvaluator.metrics) add(ChallengeIssue.METRIC)
        if (unit.isBlank()) add(ChallengeIssue.UNIT)
        if (tracking == ChallengeTracking.AUTOMATIC && metric == Metric.SLEEP && rule == ChallengeRule.CUMULATIVE) add(ChallengeIssue.SLEEP_RULE)
        if (!target.isFinite() || target <= 0) add(ChallengeIssue.TARGET)
        if (duration !in 1..366) add(ChallengeIssue.DURATION)
        if (rule.countsDays && requiredDays !in 1..duration) add(ChallengeIssue.REQUIRED_DAYS)
    }

    fun validation(): String? = validationIssues().firstOrNull()?.message

    fun summary(): String {
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
        return when (rule) {
            ChallengeRule.FLEXIBLE -> "Reach $quantity on $days out of $period."
            ChallengeRule.STREAK -> if (requiredDays == 1) "Reach $quantity on one day within $period."
                else "Reach $quantity on $requiredDays days in a row within $period."
            ChallengeRule.CUMULATIVE -> if (tracking == ChallengeTracking.CHECKBOX && unit == "times")
                "Check off $quantity within $period." else "Record a total of $quantity within $period."
            ChallengeRule.ONE_TIME -> "Reach $quantity on a single day within $period."
        }
    }
    fun reward() = UserAchievement(AchievementDefinition(rewardId, AchievementCategory.SPECIAL, name,
        "Challenge reward · $category", summary(), goal, if (rule.countsDays) "days" else unit,
        icon, category), progress, completed != null, completed, "${formatChallengeAmount(progress)} / ${formatChallengeAmount(goal)}")
}

/** Uses provider-deduplicated daily measurements; unknown days remain unknown indefinitely.
 * One-time means a daily milestone, never an unverified individual activity. Pausing stops
 * evaluation and input; resuming retains the original calendar window and health history. */
object ChallengeEvaluator {
    val metrics = setOf(Metric.STEPS, Metric.DISTANCE, Metric.SLEEP)
    fun evaluate(c: PersonalChallenge, snapshot: HealthSnapshot, today: LocalDate): PersonalChallenge {
        if(c.completed != null || c.status in setOf(ChallengeStatus.PAUSED, ChallengeStatus.ARCHIVED)) return c
        if(today < c.start) return c.copy(status = ChallengeStatus.SCHEDULED, progress = 0.0)
        val last = minOf(today, c.end)
        val auto = c.tracking == ChallengeTracking.AUTOMATIC
        val usable = snapshot.mode == c.source && snapshot.mode != DataMode.DEMO && !snapshot.loading && snapshot.error == null && c.zone == ZoneId.systemDefault().id
        val days = HistoryMath.unique(snapshot.days).associateBy { it.date }
        val values = (0L..java.time.temporal.ChronoUnit.DAYS.between(c.start, last)).map { offset ->
            val date = c.start.plusDays(offset)
            if(auto) {
                if(!usable) null else days[date]?.readings?.get(c.metric)?.takeIf { it.state == DataState.READY }?.value?.takeIf { it.isFinite() && it >= 0 }
            } else c.events.distinctBy { it.id }.filter { it.date == date }.sumOf { it.amount }
        }
        var run = 0; var best = 0
        values.forEach { if(it != null && it >= c.target) { run++; best = maxOf(best, run) } else run = 0 }
        val progress = when(c.rule) {
            ChallengeRule.FLEXIBLE -> values.count { it != null && it >= c.target }.toDouble()
            ChallengeRule.STREAK -> best.toDouble()
            ChallengeRule.CUMULATIVE -> values.filterNotNull().sum()
            ChallengeRule.ONE_TIME -> values.filterNotNull().maxOrNull() ?: 0.0
        }
        val success = progress >= c.goal
        val incompletePastDays = auto && today > c.end && (0L until c.duration.toLong()).any { offset ->
            val date = c.start.plusDays(offset)
            val day = days[date]
            val updated = day?.readings?.get(c.metric)?.updated
            day?.complete != true && (updated == null || updated < date.plusDays(1).atStartOfDay(ZoneId.of(c.zone)).toInstant())
        }
        val unknown = auto && (values.any { it == null } || !usable || incompletePastDays)
        return c.copy(progress = progress, completed = if(success) today else null, status = when {
            success -> ChallengeStatus.COMPLETED
            unknown -> ChallengeStatus.AWAITING_DATA
            today > c.end -> ChallengeStatus.EXPIRED
            else -> ChallengeStatus.ACTIVE
        })
    }
}
