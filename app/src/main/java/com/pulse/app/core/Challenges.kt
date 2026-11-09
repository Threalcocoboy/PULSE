package com.pulse.app.core

import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

enum class ChallengeTracking { AUTOMATIC, CHECKBOX, NUMERIC }
enum class ChallengeRule { FLEXIBLE, STREAK, CUMULATIVE, ONE_TIME }
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
    val goal: Double get() = if(rule in setOf(ChallengeRule.FLEXIBLE, ChallengeRule.STREAK)) requiredDays.toDouble() else target
    val rewardId: String get() = "challenge_$id"
    fun validation(): String? = when {
        name.isBlank() -> "Enter a challenge name."
        category.isBlank() -> "Enter a category."
        !target.isFinite() || target <= 0 -> "Target must be a positive number."
        duration !in 1..366 -> "Duration must be 1–366 days."
        rule in setOf(ChallengeRule.FLEXIBLE, ChallengeRule.STREAK) && requiredDays !in 1..duration -> "Successful days must be between 1 and the duration."
        tracking == ChallengeTracking.AUTOMATIC && metric !in ChallengeEvaluator.metrics -> "Choose a supported health metric."
        tracking == ChallengeTracking.AUTOMATIC && metric == Metric.SLEEP && rule == ChallengeRule.CUMULATIVE -> "Use a daily or one-time sleep target."
        unit.isBlank() -> "Enter a unit."
        else -> null
    }
    fun summary(): String = when(rule) {
        ChallengeRule.FLEXIBLE -> "At least $target $unit on $requiredDays of $duration days"
        ChallengeRule.STREAK -> "At least $target $unit on $requiredDays consecutive days"
        ChallengeRule.CUMULATIVE -> "Accumulate $target $unit in $duration days"
        ChallengeRule.ONE_TIME -> "Reach $target $unit on one day within $duration days"
    }
    fun reward() = UserAchievement(AchievementDefinition(rewardId, AchievementCategory.SPECIAL, name,
        "Challenge reward · $category", summary(), goal, if(rule in setOf(ChallengeRule.STREAK, ChallengeRule.FLEXIBLE)) "days" else unit,
        icon, category), progress, completed != null, completed, "$progress / $goal")
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
