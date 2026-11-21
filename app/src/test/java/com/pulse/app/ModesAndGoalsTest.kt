package com.pulse.app

import com.pulse.app.core.*
import com.pulse.app.data.ChallengeCodec
import com.pulse.app.data.PreferencesCodec
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ModesAndGoalsTest {
    @Test fun modesRetainWidgetIdentityGeometryAndEmptyLayoutsAcrossMigration() {
        val widget = Widget("existing-id", Metric.SLEEP, WidgetShape.CIRCLE, 2, 2, 2, 5, false, false, true)
        val legacy = LayoutCodec.decode(LayoutCodec.encode(listOf(widget)))
        val modes = PulseModes.initial(legacy)
        assertEquals(listOf(widget), modes.active.widgets)
        assertEquals("everyday", modes.activeId)
        assertEquals(modes, PulseModesCodec.decode(PulseModesCodec.encode(modes)))
        assertTrue(PulseModes.initial(LayoutCodec.decode("v1\n")).active.widgets.isEmpty())
        assertEquals(widget, LayoutCodec.decode("${LayoutCodec.encode(listOf(widget))}\nbad").single())
        assertEquals("old", LayoutCodec.decode("v1\nold|STEPS").single().id)
    }

    @Test fun copiedDashboardCanBeChangedReorderedDeletedAndRestoredIndependently() {
        val initial = PulseModes.initial()
        val copied = initial.create("Training | Test", ModeIcon.HEART, null, initial.activeId)
        val copy = copied.active
        assertEquals(initial.active.widgets.map { it.metric }, copy.widgets.map { it.metric })
        assertTrue(initial.active.widgets.none { original -> copy.widgets.any { it.id == original.id } })
        val edited = copied.update(copy.id) { it.copy(widgets = emptyList()) }.move(copy.id, -1)
        assertEquals(initial.active, edited.modes.first())
        val removed = edited.remove(copy.id)
        assertNotEquals(copy.id, removed.activeId)
        val restored = PulseModesCodec.decode(PulseModesCodec.encode(removed))!!.restore()
        assertEquals(edited.modes, restored.modes)
        val lastMode = PulseModes(listOf(initial.active), initial.activeId)
        assertEquals(lastMode, lastMode.remove(initial.activeId))
    }

    private val monday = LocalDate.of(2026, 11, 16)
    private val zone = ZoneId.of("Europe/Paris")
    private fun day(offset: Long, metric: Metric, value: Double?, state: DataState = DataState.READY) =
        DayData(monday.plusDays(offset), mapOf(metric to Reading(value, state)), complete = true)

    @Test fun goalsKeepLegacyValuesAndPersistAdvancedConfigurationsWithDeletionAndRestoration() {
        val v3 = "v3|false|NONE|6800|8.0|DARK|true|true|true|false|5.0|30|3|STEPS,WEEKLY_ACTIVE|" +
            "|false|0|false|STEPS,DISTANCE|NONE|NOT_STARTED|NOT_STARTED|NOT_STARTED|false||200"
        val prefs = PreferencesCodec.decode(v3)
        assertEquals(6800.0, Goals.indicator(Metric.STEPS, prefs)!!, 0.0)
        assertEquals(GoalCadence.WEEKLY, Goals.active(prefs).last().completionCadence)
        val original = Goals.active(prefs).first()
        val edited = original.copy(target = 7200.0, frequency = GoalFrequency.STREAK_PER_WEEK, requiredDays = 3)
        val archived = Goals.save(prefs, edited.copy(archived = true))
        val decoded = PreferencesCodec.decode(PreferencesCodec.encode(archived))
        assertFalse(Goals.active(decoded).any { it.id == original.id })
        assertEquals(edited, Goals.active(Goals.save(decoded, decoded.personalGoals.single().copy(archived = false))).first())
    }

    @Test fun weeklyTotalsUseOneReadingPerDateAndDistinguishZeroFromMissing() {
        val goal = PersonalGoal(metricId = Metric.ACTIVITIES.name, target = 4.0, cadence = GoalCadence.WEEKLY, aggregation = GoalAggregation.TOTAL)
        val tuesday = monday.plusDays(1)
        val duplicated = day(0, Metric.ACTIVITIES, 2.0)
        val missing = day(1, Metric.ACTIVITIES, null, DataState.EMPTY)
        val snapshot = HealthSnapshot(days = listOf(duplicated, duplicated, missing), access = ProviderAccess.CONNECTED, available = setOf(Metric.ACTIVITIES))
        val initial = Goals.current(goal, snapshot, tuesday, zone)
        assertEquals(2.0, initial.value!!, 0.0)
        assertNull(initial.met)
        assertNull(Goals.current(goal.copy(cadence = GoalCadence.DAILY), snapshot, tuesday, zone).value)
        assertEquals(0.0, Goals.current(goal.copy(cadence = GoalCadence.DAILY), snapshot.copy(days = listOf(missing.copy(workoutsRead = true))), tuesday, zone).value!!, 0.0)
        assertEquals(true, Goals.current(goal, snapshot.copy(days = listOf(duplicated, day(1, Metric.ACTIVITIES, 2.0))), tuesday, zone).met)
        assertNull(Goals.current(goal, snapshot.copy(available = emptySet()), tuesday, zone).met)
    }

    @Test fun bedtimeWrapsMidnightAndStreakBreaksOnUnknownDays() {
        val goal = PersonalGoal(metricId = GoalMetric.BEDTIME, target = 5.0, condition = GoalCondition.TIME, toleranceMinutes = 15,
            frequency = GoalFrequency.STREAK_PER_WEEK, requiredDays = 2)
        val sleep = day(0, Metric.SLEEP, 8.0).copy(sleepStart = Instant.parse("2026-11-15T22:55:00Z"))
        val wednesday = sleep.copy(date = monday.plusDays(2), sleepStart = sleep.sleepStart!!.plusSeconds(172800))
        val days = listOf(sleep, wednesday).associateBy { it.date }
        assertEquals(1.0, Goals.progress(goal, days, monday, wednesday.date, zone).value!!, 0.0)
        assertNull(Goals.progress(goal, days, monday, wednesday.date, zone).met)
        val tuesday = sleep.copy(date = monday.plusDays(1), sleepStart = sleep.sleepStart!!.plusSeconds(86400))
        assertEquals(true, Goals.progress(goal, days + (tuesday.date to tuesday), monday, wednesday.date, zone).met)
    }

    @Test fun challengeCanFollowAnAdvancedGoalAndWeeklyAverageWaitsForPeriodClose() {
        val metric = Metric.HRV
        val goal = PersonalGoal(metricId = metric.name, target = 30.0, upper = 60.0, cadence = GoalCadence.WEEKLY,
            aggregation = GoalAggregation.AVERAGE, condition = GoalCondition.RANGE)
        val p = Goals.save(Preferences(), goal)
        val snapshot = HealthSnapshot(mode = DataMode.HEALTH_CONNECT, days = listOf(day(0, metric, 40.0), day(2, metric, 50.0)),
            access = ProviderAccess.CONNECTED, available = setOf(metric))
        val challenge = PersonalChallenge(name = "Recovery", tracking = ChallengeTracking.PERSONAL_GOALS, rule = ChallengeRule.STREAK,
            start = monday, duration = 7, requiredDays = 1, goalIds = setOf(goal.id), goalCadence = GoalCadence.WEEKLY,
            goalTitles = listOf(goal.metric.title))
        assertNull(challenge.validation())
        assertEquals(challenge, ChallengeCodec.decode(ChallengeCodec.encode(listOf(challenge))).single())
        assertNull(ChallengeEvaluator.evaluate(challenge, snapshot, monday.plusDays(6), p).completed)
        assertEquals(ChallengeStatus.COMPLETED, ChallengeEvaluator.evaluate(challenge, snapshot, monday.plusWeeks(1), p).status)
        assertEquals(ChallengeStatus.AWAITING_DATA,
            ChallengeEvaluator.evaluate(challenge, snapshot, monday.plusWeeks(1), Goals.save(p, goal.copy(archived = true))).status)
    }
}
