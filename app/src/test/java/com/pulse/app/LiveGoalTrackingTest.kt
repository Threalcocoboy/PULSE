package com.pulse.app

import com.pulse.app.core.*
import com.pulse.app.data.PreferencesCodec
import com.pulse.app.integration.LiveGoalNotifications
import org.junit.Assert.*
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class LiveGoalTrackingTest {
    private val zone = ZoneId.of("Europe/Brussels")
    private val today = LocalDate.of(2026, 3, 29)
    private val steps = PersonalGoal(id = "steps", metricId = "STEPS", target = 5530.0, aggregation = GoalAggregation.TOTAL)
    private val sleep = PersonalGoal(id = "sleep", metricId = "SLEEP", target = 8.0, aggregation = GoalAggregation.TOTAL)
    private val recorded = Instant.parse("2026-03-29T10:32:00Z")

    private fun snapshot(vararg days: DayData) = HealthSnapshot(days.toList(), mode = DataMode.HEALTH_CONNECT,
        access = ProviderAccess.CONNECTED, available = setOf(Metric.STEPS), synced = recorded.plusSeconds(3600))

    private fun day(value: Double, date: LocalDate = today) =
        DayData(date, mapOf(Metric.STEPS to Reading(value, DataState.READY, recorded)))

    @Test fun oneSelectionReplacesOldActionsAndPersistsAfterRestart() {
        val initial = LiveGoalPreference().select(steps.id).enable(true)
        val replaced = initial.select(sleep.id)
        assertEquals(sleep.id, replaced.goalId)
        assertTrue(replaced.enabled)
        assertEquals(replaced, replaced.act(initial.token, stop = true))
        val saved = Preferences(personalGoals = listOf(steps, sleep), liveGoal = replaced)
        assertEquals(replaced, PreferencesCodec.decode(PreferencesCodec.encode(saved)).liveGoal)
        assertEquals(sleep, LiveGoalNotifications.selected(saved))
        assertFalse(replaced.act(replaced.token, stop = true).enabled)
    }

    @Test fun dismissalPersistsUntilUserResumesAndOldActionsAreIgnored() {
        val choice = LiveGoalPreference().select(steps.id).enable(true)
        val dismissed = choice.act(choice.token, stop = false)
        val restored = PreferencesCodec.decode(PreferencesCodec.encode(Preferences(liveGoal = dismissed))).liveGoal
        assertTrue(restored.dismissed)
        val resumed = restored.enable(true)
        assertFalse(resumed.dismissed)
        assertEquals(resumed, resumed.act(choice.token, stop = false))
    }

    @Test fun noSavedGoalsArchivedGoalsAndInvalidGoalsCannotBeTracked() {
        val choice = LiveGoalPreference().select(steps.id).enable(true)
        val none = Preferences(enabledGoals = emptySet(), liveGoal = choice)
        assertNull(LiveGoalNotifications.selected(none))
        assertNull(LiveGoalNotifications.selected(none.copy(personalGoals = listOf(steps.copy(archived = true)))))
        assertFalse(LiveGoalNotifications.compatible(steps.copy(metricId = "unknown")))
        assertFalse(LiveGoalPreference().enable(true).enabled)
    }

    @Test fun addingTrackingPreferencesPreservesExistingSettings() {
        val legacy = "v4|true|HEALTH_CONNECT|6300|8.0|DARK|true|true|true|false|5.0|30|3|STEPS||true|0|false|STEPS|HEALTH_CONNECT|COMPLETE|COMPLETE|SKIPPED|false||150|"
        val decoded = PreferencesCodec.decode(legacy)
        assertTrue(decoded.onboarded)
        assertEquals(6300, decoded.stepGoal)
        assertEquals(LiveGoalPreference(), decoded.liveGoal)
    }

    @Test fun unknownAndMeasuredZeroAreDistinct() {
        assertEquals(LiveGoalStatus.NO_READINGS, steps.liveGoalReading(snapshot(), DataMode.HEALTH_CONNECT, today, zone).status)
        val zero = steps.liveGoalReading(snapshot(day(0.0)), DataMode.HEALTH_CONNECT, today, zone)
        assertEquals(0.0, zero.progress.value)
        assertEquals(0, zero.percent)
        assertEquals(LiveGoalStatus.SYNCING, steps.liveGoalReading(null, DataMode.HEALTH_CONNECT, today, zone).status)
    }

    @Test fun canonicalProgressUpdatesWithoutInventedMeasurementsOrRepeatedCompletion() {
        val mode = DataMode.HEALTH_CONNECT
        val before = steps.liveGoalReading(snapshot(day(3200.0)), mode, today, zone)
        val synced = snapshot(day(4000.0))
        val after = steps.liveGoalReading(synced, mode, today, zone)
        assertTrue(after.percent!! > before.percent!!)
        assertEquals(72, after.percent)
        assertEquals(1530.0, after.remaining)
        assertEquals(recorded, after.updated)
        assertEquals(Goals.current(steps, synced, today, zone), after.progress)
        val complete = steps.liveGoalReading(snapshot(day(5600.0)), mode, today, zone)
        assertEquals(LiveGoalStatus.COMPLETED, complete.status)
        assertEquals(5600.0, complete.progress.value)
        assertEquals(100, complete.percent)
        assertEquals(0.0, complete.remaining)
        assertEquals(complete, steps.liveGoalReading(snapshot(day(5600.0)), mode, today, zone))
    }

    @Test fun rangeAndMaximumTargetsDoNotGetAPercentage() {
        val current = snapshot(day(6000.0))
        val maximum = steps.copy(condition = GoalCondition.MAXIMUM)
            .liveGoalReading(current, DataMode.HEALTH_CONNECT, today, zone)
        val range = steps.copy(condition = GoalCondition.RANGE, upper = 7500.0)
            .liveGoalReading(current, DataMode.HEALTH_CONNECT, today, zone)
        assertEquals(LiveGoalStatus.LIMIT_EXCEEDED, maximum.status)
        assertNull(maximum.percent)
        assertNull(range.percent)
    }

    @Test fun deniedPermissionAndDisconnectedProviderDoNotExposeCachedValues() {
        val cached = snapshot(day(4000.0))
        val denied = steps.liveGoalReading(cached.copy(access = ProviderAccess.DENIED), DataMode.HEALTH_CONNECT, today, zone)
        assertEquals(LiveGoalStatus.PERMISSION_REQUIRED, denied.status)
        assertNull(denied.progress.value)
        val disconnected = steps.liveGoalReading(cached, DataMode.NONE, today, zone)
        assertEquals(LiveGoalStatus.CONNECT_SOURCE, disconnected.status)
        assertNull(disconnected.progress.value)
    }

    @Test fun dailyAndWeeklyRolloversUseLocalCalendarBoundaries() {
        val mode = DataMode.HEALTH_CONNECT
        val tomorrow = today.plusDays(1)
        val original = snapshot(day(4000.0))
        val weekly = steps.copy(cadence = GoalCadence.WEEKLY)
        assertEquals(today.minusDays(6), weekly.liveGoalPeriod(today))
        assertEquals(tomorrow, weekly.liveGoalPeriod(tomorrow))
        assertNull(steps.liveGoalReading(original, mode, tomorrow, zone).progress.value)
        assertNull(weekly.liveGoalReading(original, mode, tomorrow, zone).progress.value)
        val midnight = today.atStartOfDay(zone)
        assertEquals(23L, Duration.between(midnight, steps.nextLiveGoalPeriod(midnight)).toHours())
        assertEquals(tomorrow.atStartOfDay(zone), weekly.nextLiveGoalPeriod(midnight))
        val sameInstantInCalifornia = midnight.withZoneSameInstant(ZoneId.of("America/Los_Angeles"))
        assertEquals(today, steps.nextLiveGoalPeriod(sameInstantInCalifornia).toLocalDate())
    }
}
