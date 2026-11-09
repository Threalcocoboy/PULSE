package com.pulse.app.core

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

enum class AchievementCategory(val label: String) {
    ALL("All"),
    STEPS("Steps"),
    SLEEP("Sleep"),
    WORKOUTS("Workouts"),
    TIMING("Workout timing"),
    DISTANCE("Distance"),
    ACTIVE("Active minutes"),
    CONSISTENCY("Consistency"),
    SPECIAL("Special")
}

data class AchievementDefinition(
    val id: String,
    val category: AchievementCategory,
    val name: String,
    val description: String,
    val requirement: String,
    val target: Double,
    val unit: String = "",
    val challengeIcon: String? = null,
    val challengeCategory: String? = null
)

data class UserAchievement(
    val definition: AchievementDefinition,
    val progress: Double,
    val unlocked: Boolean,
    val unlockedDate: LocalDate? = null,
    val progressText: String,
    val period: java.time.YearMonth? = null
)

object AchievementRegistry {
    val all: List<AchievementDefinition> = listOf(
        // A. STEPS
        AchievementDefinition(
            id = "first_steps",
            category = AchievementCategory.STEPS,
            name = "First Steps",
            description = "Log your first day with recorded steps.",
            requirement = "Record steps on at least 1 day",
            target = 1.0,
            unit = "day"
        ),
        AchievementDefinition(
            id = "steps_5k",
            category = AchievementCategory.STEPS,
            name = "5K Club",
            description = "Walk at least 5,000 steps in a single day.",
            requirement = "Reach 5,000 steps in one day",
            target = 5000.0,
            unit = "steps"
        ),
        AchievementDefinition(
            id = "steps_10k",
            category = AchievementCategory.STEPS,
            name = "10K Club",
            description = "Hit the landmark 10,000 steps in a single day.",
            requirement = "Reach 10,000 steps in one day",
            target = 10000.0,
            unit = "steps"
        ),
        AchievementDefinition(
            id = "goal_crusher",
            category = AchievementCategory.STEPS,
            name = "Goal Crusher",
            description = "Complete your personalized daily step goal.",
            requirement = "Hit your step goal on at least 1 day",
            target = 1.0,
            unit = "day"
        ),
        AchievementDefinition(
            id = "step_consistency",
            category = AchievementCategory.STEPS,
            name = "Consistency",
            description = "Achieve your daily step goal on 5 separate days.",
            requirement = "Hit step goal on 5 separate days",
            target = 5.0,
            unit = "days"
        ),
        AchievementDefinition(
            id = "walking_streak",
            category = AchievementCategory.STEPS,
            name = "Walking Streak",
            description = "Reach your step goal on 3 consecutive days.",
            requirement = "Hit step goal 3 days in a row",
            target = 3.0,
            unit = "days"
        ),

        // B. SLEEP
        AchievementDefinition(
            id = "sleep_explorer",
            category = AchievementCategory.SLEEP,
            name = "Sleep Explorer",
            description = "Record your first complete sleep session.",
            requirement = "Log at least 1 sleep session",
            target = 1.0,
            unit = "night"
        ),
        AchievementDefinition(
            id = "well_rested",
            category = AchievementCategory.SLEEP,
            name = "Well Rested",
            description = "Sleep for at least 8 hours in a single night.",
            requirement = "Record 8.0 or more hours of sleep",
            target = 8.0,
            unit = "hours"
        ),
        AchievementDefinition(
            id = "dream_team",
            category = AchievementCategory.SLEEP,
            name = "Dream Team",
            description = "Reach your sleep duration goal on 7 nights.",
            requirement = "Reach sleep goal on 7 nights",
            target = 7.0,
            unit = "nights"
        ),
        AchievementDefinition(
            id = "consistent_sleeper",
            category = AchievementCategory.SLEEP,
            name = "Consistent Sleeper",
            description = "Reach your sleep duration goal 3 nights in a row.",
            requirement = "Reach sleep goal on 3 consecutive nights",
            target = 3.0,
            unit = "nights"
        ),
        AchievementDefinition(
            id = "sleep_routine",
            category = AchievementCategory.SLEEP,
            name = "Sleep Routine",
            description = "Maintain stable rest duration (within 45 min) on 4 recorded nights.",
            requirement = "Stable sleep duration on 4 nights",
            target = 4.0,
            unit = "nights"
        ),

        // C. WORKOUTS
        AchievementDefinition(
            id = "first_workout",
            category = AchievementCategory.WORKOUTS,
            name = "First Workout",
            description = "Record your first completed workout activity.",
            requirement = "Complete 1 workout session",
            target = 1.0,
            unit = "workout"
        ),
        AchievementDefinition(
            id = "getting_active",
            category = AchievementCategory.WORKOUTS,
            name = "Getting Active",
            description = "Complete 5 workout sessions.",
            requirement = "Complete 5 workout sessions",
            target = 5.0,
            unit = "workouts"
        ),
        AchievementDefinition(
            id = "dedicated",
            category = AchievementCategory.WORKOUTS,
            name = "Dedicated",
            description = "Complete 25 workout sessions.",
            requirement = "Complete 25 workout sessions",
            target = 25.0,
            unit = "workouts"
        ),
        AchievementDefinition(
            id = "half_hour_hero",
            category = AchievementCategory.WORKOUTS,
            name = "Half-Hour Hero",
            description = "Complete a workout session lasting at least 30 minutes.",
            requirement = "Workout lasting >= 30 min",
            target = 30.0,
            unit = "min"
        ),
        AchievementDefinition(
            id = "endurance",
            category = AchievementCategory.WORKOUTS,
            name = "Endurance",
            description = "Complete a workout session lasting at least 60 minutes.",
            requirement = "Workout lasting >= 60 min",
            target = 60.0,
            unit = "min"
        ),
        AchievementDefinition(
            id = "variety",
            category = AchievementCategory.WORKOUTS,
            name = "Variety",
            description = "Complete workouts across at least 3 distinct activity categories.",
            requirement = "3 different activity types",
            target = 3.0,
            unit = "types"
        ),

        // D. WORKOUT TIMING
        AchievementDefinition(
            id = "early_bird",
            category = AchievementCategory.TIMING,
            name = "Early Bird",
            description = "Start a workout between 05:00 and 08:00 local time.",
            requirement = "Start workout between 05:00 and 08:00",
            target = 1.0,
            unit = "workout"
        ),
        AchievementDefinition(
            id = "sunrise_session",
            category = AchievementCategory.TIMING,
            name = "Sunrise Session",
            description = "Complete 3 early-morning workouts (05:00–08:00).",
            requirement = "3 workouts between 05:00 and 08:00",
            target = 3.0,
            unit = "workouts"
        ),
        AchievementDefinition(
            id = "lunchtime_move",
            category = AchievementCategory.TIMING,
            name = "Lunchtime Move",
            description = "Start a workout between 12:00 and 14:00 local time.",
            requirement = "Start workout between 12:00 and 14:00",
            target = 1.0,
            unit = "workout"
        ),
        AchievementDefinition(
            id = "after_hours",
            category = AchievementCategory.TIMING,
            name = "After Hours",
            description = "Complete an evening workout between 18:00 and 23:59.",
            requirement = "Start workout after 18:00",
            target = 1.0,
            unit = "workout"
        ),
        AchievementDefinition(
            id = "weekend_warrior",
            category = AchievementCategory.TIMING,
            name = "Weekend Warrior",
            description = "Complete workouts on at least 3 weekend days (Saturday or Sunday).",
            requirement = "Workouts on 3 weekend days",
            target = 3.0,
            unit = "days"
        ),

        // E. DISTANCE
        AchievementDefinition(
            id = "first_km",
            category = AchievementCategory.DISTANCE,
            name = "First Kilometer",
            description = "Reach 1 kilometer of walking or running in a single day.",
            requirement = "Cover >= 1.0 km in one day",
            target = 1.0,
            unit = "km"
        ),
        AchievementDefinition(
            id = "five_km_day",
            category = AchievementCategory.DISTANCE,
            name = "Five Kilometer Day",
            description = "Cover at least 5 kilometers on foot in a single day.",
            requirement = "Cover >= 5.0 km in one day",
            target = 5.0,
            unit = "km"
        ),
        AchievementDefinition(
            id = "distance_explorer",
            category = AchievementCategory.DISTANCE,
            name = "Distance Explorer",
            description = "Accumulate 50 km of total recorded distance.",
            requirement = "Accumulate 50 km total distance",
            target = 50.0,
            unit = "km"
        ),
        AchievementDefinition(
            id = "long_haul",
            category = AchievementCategory.DISTANCE,
            name = "Long Haul",
            description = "Accumulate 100 km of total recorded distance.",
            requirement = "Accumulate 100 km total distance",
            target = 100.0,
            unit = "km"
        ),

        // F. ACTIVE MINUTES
        AchievementDefinition(
            id = "on_the_move",
            category = AchievementCategory.ACTIVE,
            name = "On the Move",
            description = "Reach 30 active minutes in a single day.",
            requirement = "30 active minutes in one day",
            target = 30.0,
            unit = "min"
        ),
        AchievementDefinition(
            id = "active_week",
            category = AchievementCategory.ACTIVE,
            name = "Active Week",
            description = "Accumulate 150 active minutes in a 7-day period.",
            requirement = "150 active minutes in one week",
            target = 150.0,
            unit = "min"
        ),
        AchievementDefinition(
            id = "moving_regularly",
            category = AchievementCategory.ACTIVE,
            name = "Moving Regularly",
            description = "Record active minutes on at least 5 separate days.",
            requirement = "Active minutes on 5 days",
            target = 5.0,
            unit = "days"
        ),

        // G. CONSISTENCY & TRACKING
        AchievementDefinition(
            id = "first_checkin",
            category = AchievementCategory.CONSISTENCY,
            name = "First Check-In",
            description = "First day with recorded health measurements.",
            requirement = "Record valid health data for 1 day",
            target = 1.0,
            unit = "day"
        ),
        AchievementDefinition(
            id = "data_explorer",
            category = AchievementCategory.CONSISTENCY,
            name = "Data Explorer",
            description = "Track at least 3 distinct health metrics on a single day.",
            requirement = "Log 3 metrics on the same day",
            target = 3.0,
            unit = "metrics"
        ),
        AchievementDefinition(
            id = "one_week_in",
            category = AchievementCategory.CONSISTENCY,
            name = "One Week In",
            description = "Record usable health data on 7 distinct days.",
            requirement = "7 days with recorded health data",
            target = 7.0,
            unit = "days"
        ),
        AchievementDefinition(
            id = "consistent_tracker",
            category = AchievementCategory.CONSISTENCY,
            name = "Consistent Tracker",
            description = "Maintain 14 distinct days of recorded health records.",
            requirement = "14 days of recorded data",
            target = 14.0,
            unit = "days"
        ),

        // H. SPECIAL
        AchievementDefinition(
            id = "triple_threat",
            category = AchievementCategory.SPECIAL,
            name = "Triple Threat",
            description = "Hit your step goal, sleep goal, and 30 active minutes on the same day.",
            requirement = "Meet 3 activity goals on one day",
            target = 1.0,
            unit = "day"
        ),
        AchievementDefinition(
            id = "habit_builder",
            category = AchievementCategory.SPECIAL,
            name = "Habit Builder",
            description = "Reach your daily step goal on 15 separate days.",
            requirement = "Hit step goal 15 times",
            target = 15.0,
            unit = "days"
        ),
        AchievementDefinition(
            id = "the_comeback",
            category = AchievementCategory.SPECIAL,
            name = "The Comeback",
            description = "Complete a workout after a gap of at least 7 days between sessions.",
            requirement = "Workout after a 7-day break",
            target = 1.0,
            unit = "workout"
        )
    )
}

object AchievementEngine {

    /** Parses the local hour from an activity start string like "2026-11-05T06:30:00" or "06:30:00". */
    fun parseLocalHour(localStart: String): Int? {
        val trimmed = localStart.trim()
        val timeStr = if (trimmed.contains("T")) trimmed.substringAfter("T") else trimmed
        return runCatching {
            val parts = timeStr.split(":")
            parts.firstOrNull()?.toInt()?.takeIf { it in 0..23 && parts.getOrNull(1)?.toIntOrNull() in 0..59 }
        }.getOrNull()
    }

    private fun number(v: Double): String = java.text.NumberFormat.getIntegerInstance().format(v.toLong())

    /**
     * Evaluates all registered achievements against historical days and preferences.
     * Pure and deterministic: never fabricates metrics, treats missing data as absent.
     */
    fun evaluate(snapshot: HealthSnapshot, preferences: Preferences, today: LocalDate = LocalDate.now()): List<UserAchievement> {
        val days = HistoryMath.unique(snapshot.days).filter { it.date <= today }.map { day ->
            day.copy(readings = day.readings.filterValues { r -> r.state in setOf(DataState.READY, DataState.STALE) && r.value?.let { it.isFinite() && it >= 0 } == true })
        }.sortedBy { it.date }
        val stepGoal = preferences.stepGoal.toDouble()
        val sleepGoal = preferences.sleepGoal

        // Extract all recorded activities across history
        val allActivities = days.flatMap { day ->
            day.activities.map { act -> day.date to act }
        }.distinctBy { it.second.id }.sortedBy { it.first }

        return AchievementRegistry.all.map { def ->
            when (def.id) {
                // Steps
                "first_steps" -> {
                    val first = days.firstOrNull { (it.value(Metric.STEPS) ?: 0.0) > 0.0 }
                    val current = if (first != null) 1.0 else 0.0
                    UserAchievement(def, current, current >= def.target, first?.date, if (first != null) "Unlocked" else "0 / 1 day")
                }
                "steps_5k" -> {
                    val maxSteps = days.mapNotNull { it.value(Metric.STEPS) }.maxOrNull() ?: 0.0
                    val unlockedDay = days.firstOrNull { (it.value(Metric.STEPS) ?: 0.0) >= 5000.0 }?.date
                    UserAchievement(def, maxSteps.coerceAtMost(def.target), maxSteps >= 5000.0, unlockedDay, "${number(maxSteps)} / 5,000 steps")
                }
                "steps_10k" -> {
                    val maxSteps = days.mapNotNull { it.value(Metric.STEPS) }.maxOrNull() ?: 0.0
                    val unlockedDay = days.firstOrNull { (it.value(Metric.STEPS) ?: 0.0) >= 10000.0 }?.date
                    UserAchievement(def, maxSteps.coerceAtMost(def.target), maxSteps >= 10000.0, unlockedDay, "${number(maxSteps)} / 10,000 steps")
                }
                "goal_crusher" -> {
                    val hitDay = days.firstOrNull { (it.value(Metric.STEPS) ?: 0.0) >= stepGoal }?.date
                    val count = if (hitDay != null) 1.0 else 0.0
                    UserAchievement(def, count, count >= 1.0, hitDay, if (count >= 1.0) "Goal achieved" else "0 / 1 day")
                }
                "step_consistency" -> {
                    val count = days.count { (it.value(Metric.STEPS) ?: 0.0) >= stepGoal }.toDouble()
                    val unlocked = count >= def.target
                    UserAchievement(def, count.coerceAtMost(def.target), unlocked, null, "${count.toInt()} / ${def.target.toInt()} days")
                }
                "walking_streak" -> {
                    var maxStreak = 0
                    var currentStreak = 0
                    var previous: LocalDate? = null
                    days.forEach { d ->
                        if (previous != d.date.minusDays(1)) currentStreak = 0
                        previous = d.date
                        if ((d.value(Metric.STEPS) ?: 0.0) >= stepGoal) {
                            currentStreak++
                            if (currentStreak > maxStreak) maxStreak = currentStreak
                        } else {
                            currentStreak = 0
                        }
                    }
                    val unlocked = maxStreak >= def.target.toInt()
                    UserAchievement(def, maxStreak.toDouble().coerceAtMost(def.target), unlocked, null, "$maxStreak / ${def.target.toInt()} consecutive days")
                }

                // Sleep
                "sleep_explorer" -> {
                    val first = days.firstOrNull { (it.value(Metric.SLEEP) ?: 0.0) > 0.0 }
                    val current = if (first != null) 1.0 else 0.0
                    UserAchievement(def, current, current >= 1.0, first?.date, if (current >= 1.0) "Unlocked" else "0 / 1 night")
                }
                "well_rested" -> {
                    val maxSleep = days.mapNotNull { it.value(Metric.SLEEP) }.maxOrNull() ?: 0.0
                    val unlocked = maxSleep >= 8.0
                    val unlockDay = days.firstOrNull { (it.value(Metric.SLEEP) ?: 0.0) >= 8.0 }?.date
                    val displayHours = String.format(java.util.Locale.getDefault(), "%.1f", maxSleep)
                    UserAchievement(def, maxSleep.coerceAtMost(def.target), unlocked, unlockDay, "$displayHours / 8.0 hours")
                }
                "dream_team" -> {
                    val count = days.count { (it.value(Metric.SLEEP) ?: 0.0) >= sleepGoal }.toDouble()
                    val unlocked = count >= def.target
                    UserAchievement(def, count.coerceAtMost(def.target), unlocked, null, "${count.toInt()} / ${def.target.toInt()} nights")
                }
                "consistent_sleeper" -> {
                    var maxStreak = 0
                    var currentStreak = 0
                    var previous: LocalDate? = null
                    days.forEach { d ->
                        if (previous != d.date.minusDays(1)) currentStreak = 0
                        previous = d.date
                        if ((d.value(Metric.SLEEP) ?: 0.0) >= sleepGoal) {
                            currentStreak++
                            if (currentStreak > maxStreak) maxStreak = currentStreak
                        } else {
                            currentStreak = 0
                        }
                    }
                    val unlocked = maxStreak >= def.target.toInt()
                    UserAchievement(def, maxStreak.toDouble().coerceAtMost(def.target), unlocked, null, "$maxStreak / ${def.target.toInt()} consecutive nights")
                }
                "sleep_routine" -> {
                    val sleepValues = days.mapNotNull { it.value(Metric.SLEEP)?.takeIf { hours -> hours > 0 } }
                    val avg = sleepValues.takeIf { it.isNotEmpty() }?.average() ?: 0.0
                    val stableCount = if (avg > 0) sleepValues.count { abs(it - avg) <= 0.75 }.toDouble() else 0.0
                    val unlocked = stableCount >= def.target
                    UserAchievement(def, stableCount.coerceAtMost(def.target), unlocked, null, "${stableCount.toInt()} / ${def.target.toInt()} nights")
                }

                // Workouts
                "first_workout" -> {
                    val first = allActivities.firstOrNull()
                    val count = if (first != null) 1.0 else 0.0
                    UserAchievement(def, count, count >= 1.0, first?.first, if (count >= 1.0) "Unlocked" else "0 / 1 workout")
                }
                "getting_active" -> {
                    val count = allActivities.size.toDouble()
                    val unlocked = count >= def.target
                    UserAchievement(def, count.coerceAtMost(def.target), unlocked, null, "${count.toInt()} / ${def.target.toInt()} workouts")
                }
                "dedicated" -> {
                    val count = allActivities.size.toDouble()
                    val unlocked = count >= def.target
                    UserAchievement(def, count.coerceAtMost(def.target), unlocked, null, "${count.toInt()} / ${def.target.toInt()} workouts")
                }
                "half_hour_hero" -> {
                    val maxDur = allActivities.mapNotNull { it.second.minutes }.maxOrNull() ?: 0.0
                    val unlocked = maxDur >= 30.0
                    val unlockDay = allActivities.firstOrNull { (it.second.minutes ?: 0.0) >= 30.0 }?.first
                    UserAchievement(def, maxDur.coerceAtMost(def.target), unlocked, unlockDay, "${maxDur.toInt()} / 30 min")
                }
                "endurance" -> {
                    val maxDur = allActivities.mapNotNull { it.second.minutes }.maxOrNull() ?: 0.0
                    val unlocked = maxDur >= 60.0
                    val unlockDay = allActivities.firstOrNull { (it.second.minutes ?: 0.0) >= 60.0 }?.first
                    UserAchievement(def, maxDur.coerceAtMost(def.target), unlocked, unlockDay, "${maxDur.toInt()} / 60 min")
                }
                "variety" -> {
                    val types = allActivities.map { it.second.type }.distinct().size.toDouble()
                    val unlocked = types >= def.target
                    UserAchievement(def, types.coerceAtMost(def.target), unlocked, null, "${types.toInt()} / ${def.target.toInt()} types")
                }

                // Timing
                "early_bird" -> {
                    val morning = allActivities.firstOrNull { (_, act) ->
                        val h = parseLocalHour(act.localStart)
                        h != null && h in 5..7
                    }
                    val count = if (morning != null) 1.0 else 0.0
                    UserAchievement(def, count, count >= 1.0, morning?.first, if (count >= 1.0) "Unlocked" else "0 / 1 workout")
                }
                "sunrise_session" -> {
                    val morningCount = allActivities.count { (_, act) ->
                        val h = parseLocalHour(act.localStart)
                        h != null && h in 5..7
                    }.toDouble()
                    val unlocked = morningCount >= def.target
                    UserAchievement(def, morningCount.coerceAtMost(def.target), unlocked, null, "${morningCount.toInt()} / ${def.target.toInt()} workouts")
                }
                "lunchtime_move" -> {
                    val lunch = allActivities.firstOrNull { (_, act) ->
                        val h = parseLocalHour(act.localStart)
                        h != null && h in 12..13
                    }
                    val count = if (lunch != null) 1.0 else 0.0
                    UserAchievement(def, count, count >= 1.0, lunch?.first, if (count >= 1.0) "Unlocked" else "0 / 1 workout")
                }
                "after_hours" -> {
                    val evening = allActivities.firstOrNull { (_, act) ->
                        val h = parseLocalHour(act.localStart)
                        h != null && h in 18..23
                    }
                    val count = if (evening != null) 1.0 else 0.0
                    UserAchievement(def, count, count >= 1.0, evening?.first, if (count >= 1.0) "Unlocked" else "0 / 1 workout")
                }
                "weekend_warrior" -> {
                    val weekendDays = allActivities.map { it.first }.filter {
                        it.dayOfWeek == java.time.DayOfWeek.SATURDAY || it.dayOfWeek == java.time.DayOfWeek.SUNDAY
                    }.distinct().size.toDouble()
                    val unlocked = weekendDays >= def.target
                    UserAchievement(def, weekendDays.coerceAtMost(def.target), unlocked, null, "${weekendDays.toInt()} / ${def.target.toInt()} weekend days")
                }

                // Distance
                "first_km" -> {
                    val maxD = days.mapNotNull { it.value(Metric.DISTANCE) }.maxOrNull() ?: 0.0
                    val unlocked = maxD >= 1.0
                    val unlockDay = days.firstOrNull { (it.value(Metric.DISTANCE) ?: 0.0) >= 1.0 }?.date
                    UserAchievement(def, maxD.coerceAtMost(def.target), unlocked, unlockDay, "${String.format(java.util.Locale.getDefault(), "%.1f", maxD)} / 1.0 km")
                }
                "five_km_day" -> {
                    val maxD = days.mapNotNull { it.value(Metric.DISTANCE) }.maxOrNull() ?: 0.0
                    val unlocked = maxD >= 5.0
                    val unlockDay = days.firstOrNull { (it.value(Metric.DISTANCE) ?: 0.0) >= 5.0 }?.date
                    UserAchievement(def, maxD.coerceAtMost(def.target), unlocked, unlockDay, "${String.format(java.util.Locale.getDefault(), "%.1f", maxD)} / 5.0 km")
                }
                "distance_explorer" -> {
                    val totalKm = days.mapNotNull { it.value(Metric.DISTANCE) }.sum()
                    val unlocked = totalKm >= def.target
                    UserAchievement(def, totalKm.coerceAtMost(def.target), unlocked, null, "${totalKm.toInt()} / ${def.target.toInt()} km")
                }
                "long_haul" -> {
                    val totalKm = days.mapNotNull { it.value(Metric.DISTANCE) }.sum()
                    val unlocked = totalKm >= def.target
                    UserAchievement(def, totalKm.coerceAtMost(def.target), unlocked, null, "${totalKm.toInt()} / ${def.target.toInt()} km")
                }

                // Active Minutes
                "on_the_move" -> {
                    val maxAct = days.mapNotNull { it.value(Metric.ACTIVE) }.maxOrNull() ?: 0.0
                    val unlocked = maxAct >= 30.0
                    val unlockDay = days.firstOrNull { (it.value(Metric.ACTIVE) ?: 0.0) >= 30.0 }?.date
                    UserAchievement(def, maxAct.coerceAtMost(def.target), unlocked, unlockDay, "${maxAct.toInt()} / 30 min")
                }
                "active_week" -> {
                    // Seven calendar days, including gaps with no records.
                    val maxWeekAct = days.maxOfOrNull { end ->
                        days.filter { it.date in end.date.minusDays(6)..end.date }.mapNotNull { it.value(Metric.ACTIVE) }.sum()
                    } ?: 0.0
                    val unlocked = maxWeekAct >= def.target
                    UserAchievement(def, maxWeekAct.coerceAtMost(def.target), unlocked, null, "${maxWeekAct.toInt()} / ${def.target.toInt()} min")
                }
                "moving_regularly" -> {
                    val count = days.count { (it.value(Metric.ACTIVE) ?: 0.0) > 0.0 }.toDouble()
                    val unlocked = count >= def.target
                    UserAchievement(def, count.coerceAtMost(def.target), unlocked, null, "${count.toInt()} / ${def.target.toInt()} days")
                }

                // Consistency
                "first_checkin" -> {
                    val first = days.firstOrNull { it.readings.values.any { r -> r.value != null } }
                    val count = if (first != null) 1.0 else 0.0
                    UserAchievement(def, count, count >= 1.0, first?.date, if (count >= 1.0) "Unlocked" else "0 / 1 day")
                }
                "data_explorer" -> {
                    val maxMetrics = days.maxOfOrNull { d -> d.readings.values.count { it.value != null } } ?: 0
                    val unlocked = maxMetrics >= 3
                    UserAchievement(def, maxMetrics.toDouble().coerceAtMost(def.target), unlocked, null, "$maxMetrics / 3 metrics")
                }
                "one_week_in" -> {
                    val count = days.count { d -> d.readings.values.any { it.value != null } }.toDouble()
                    val unlocked = count >= def.target
                    UserAchievement(def, count.coerceAtMost(def.target), unlocked, null, "${count.toInt()} / ${def.target.toInt()} days")
                }
                "consistent_tracker" -> {
                    val count = days.count { d -> d.readings.values.any { it.value != null } }.toDouble()
                    val unlocked = count >= def.target
                    UserAchievement(def, count.coerceAtMost(def.target), unlocked, null, "${count.toInt()} / ${def.target.toInt()} days")
                }

                // Special
                "triple_threat" -> {
                    val hitDay = days.firstOrNull { d ->
                        val st = (d.value(Metric.STEPS) ?: 0.0) >= stepGoal
                        val sl = (d.value(Metric.SLEEP) ?: 0.0) >= sleepGoal
                        val ac = (d.value(Metric.ACTIVE) ?: 0.0) >= 30.0
                        st && sl && ac
                    }?.date
                    val count = if (hitDay != null) 1.0 else 0.0
                    UserAchievement(def, count, count >= 1.0, hitDay, if (count >= 1.0) "Unlocked" else "0 / 1 day")
                }
                "habit_builder" -> {
                    val count = days.count { (it.value(Metric.STEPS) ?: 0.0) >= stepGoal }.toDouble()
                    val unlocked = count >= def.target
                    UserAchievement(def, count.coerceAtMost(def.target), unlocked, null, "${count.toInt()} / ${def.target.toInt()} days")
                }
                "the_comeback" -> {
                    var comebackDay: LocalDate? = null
                    if (allActivities.size >= 2) {
                        for (i in 1 until allActivities.size) {
                            val gap = java.time.temporal.ChronoUnit.DAYS.between(allActivities[i - 1].first, allActivities[i].first)
                            if (gap >= 7) {
                                comebackDay = allActivities[i].first
                                break
                            }
                        }
                    }
                    val count = if (comebackDay != null) 1.0 else 0.0
                    UserAchievement(def, count, count >= 1.0, comebackDay, if (count >= 1.0) "Unlocked" else "0 / 1 workout")
                }

                else -> error("Unimplemented achievement: ${def.id}")
            }
        }.map { item ->
            if(!item.unlocked || item.unlockedDate != null) item else {
                val target=item.definition.target
                fun counted(predicate: (DayData)->Boolean) = days.filter(predicate).getOrNull(target.toInt()-1)?.date
                fun cumulative(metric: Metric): LocalDate? {
                    var total=0.0
                    return days.firstOrNull { total += it.value(metric) ?: 0.0; total >= target }?.date
                }
                fun streak(metric: Metric, goal: Double): LocalDate? {
                    var length=0;var prior: LocalDate?=null
                    return days.firstOrNull {
                        if(prior!=it.date.minusDays(1)) length=0
                        prior=it.date
                        length=if((it.value(metric) ?: 0.0)>=goal) length+1 else 0
                        length>=target
                    }?.date
                }
                val date=when(item.definition.id) {
                    "step_consistency", "habit_builder" -> counted { (it.value(Metric.STEPS) ?: 0.0)>=stepGoal }
                    "walking_streak" -> streak(Metric.STEPS,stepGoal)
                    "dream_team" -> counted { (it.value(Metric.SLEEP) ?: 0.0)>=sleepGoal }
                    "consistent_sleeper" -> streak(Metric.SLEEP,sleepGoal)
                    "sleep_routine" -> {
                        val nights=mutableListOf<Double>()
                        days.firstOrNull { day ->
                            day.value(Metric.SLEEP)?.takeIf { it>0 }?.let(nights::add)
                            val avg=nights.takeIf { it.isNotEmpty() }?.average() ?: 0.0
                            nights.count { abs(it-avg)<=.75 }>=target
                        }?.date
                    }
                    "getting_active", "dedicated" -> allActivities.getOrNull(target.toInt()-1)?.first
                    "variety" -> { val types=mutableSetOf<String>();allActivities.firstOrNull { types.add(it.second.type);types.size>=target }?.first }
                    "sunrise_session" -> allActivities.filter { parseLocalHour(it.second.localStart) in 5..7 }.getOrNull(target.toInt()-1)?.first
                    "weekend_warrior" -> allActivities.map {it.first}.distinct().filter {it.dayOfWeek.value>=6}.getOrNull(target.toInt()-1)
                    "distance_explorer", "long_haul" -> cumulative(Metric.DISTANCE)
                    "active_week" -> days.firstOrNull { end -> days.filter {it.date in end.date.minusDays(6)..end.date}.sumOf {it.value(Metric.ACTIVE) ?: 0.0}>=target }?.date
                    "moving_regularly" -> counted { (it.value(Metric.ACTIVE) ?: 0.0)>0 }
                    "data_explorer" -> days.firstOrNull { it.readings.values.count {r->r.value!=null}>=target }?.date
                    "one_week_in", "consistent_tracker" -> counted {it.readings.isNotEmpty()}
                    else -> null
                }
                item.copy(unlockedDate=date)
            }
        }
    }
}
