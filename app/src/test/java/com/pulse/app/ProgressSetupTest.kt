package com.pulse.app

import com.pulse.app.core.*
import com.pulse.app.data.ChallengeCodec
import com.pulse.app.data.PreferencesCodec
import com.pulse.app.integration.ProgressNotifications
import com.pulse.app.integration.garmin.*
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.util.Base64

class ProgressSetupTest {
    private val monday = LocalDate.of(2026, 11, 9)
    private val zone = ZoneId.systemDefault()
    private val timestamp = monday.atTime(12, 0).atZone(zone).toInstant()
    private val p = Preferences(mode = DataMode.HEALTH_CONNECT)

    private fun snapshot(vararg days: DayData, scope: String = "test") =
        HealthSnapshot(days.toList(), mode = p.mode, access = ProviderAccess.CONNECTED,
            available = days.flatMap { it.readings.keys }.toSet(), sourceScope = scope)

    private fun day(date: LocalDate, vararg values: Pair<Metric, Double>) =
        DayData(date, values.associate { (metric, value) -> metric to Reading(value, DataState.READY, timestamp) }, complete = true)

    private fun challenge(measure: ChallengeMeasure, condition: ChallengeCondition = measure.conditions.first()) =
        PersonalChallenge(name = "Track ${measure.title}", tracking = ChallengeTracking.AUTOMATIC,
            metric = measure.metric, measure = measure, condition = condition, sourceScope = "test",
            rule = ChallengeRule.FLEXIBLE, target = 1.0, requiredDays = 1, start = monday)

    @Test fun everyNumericOrRecordedMetricCompletesFromRealReadingsAndRejectsMissingValues() {
        ChallengeMeasure.entries.filterNot { it.presence }.forEach { measure ->
            val c = challenge(measure)
            assertNull(measure.title, c.validation())
            val valid = snapshot(day(monday, measure.metric to 12.0))
            assertEquals(measure.title, ChallengeStatus.COMPLETED, ChallengeEvaluator.evaluate(c, valid, monday).status)
            val stale = snapshot(DayData(monday, mapOf(measure.metric to Reading(12.0, DataState.STALE))))
            assertEquals(measure.title, ChallengeStatus.AWAITING_DATA, ChallengeEvaluator.evaluate(c, stale, monday).status)
            assertEquals(measure.title, c, ChallengeCodec.decode(ChallengeCodec.encode(listOf(c))).single())
        }
    }

    @Test fun sleepStagesAndWakeTimeUseRecordedValuesAndAllowAWindowAcrossMidnight() {
        val records = day(monday, Metric.SLEEP to 7.0, Metric.SLEEP_STAGES to 7.0).copy(
            sleepStages = mapOf("deep" to 1.5, "awake" to 0.0),
            sleepEnd = monday.atTime(0, 5).atZone(zone).toInstant())
        val stage = challenge(ChallengeMeasure.SLEEP_STAGE).copy(qualifier = "Deep")
        val schedule = challenge(ChallengeMeasure.SLEEP_SCHEDULE).copy(timeMinutes = 23 * 60 + 55, toleranceMinutes = 15)
        assertEquals(ChallengeStatus.COMPLETED, ChallengeEvaluator.evaluate(stage, snapshot(records), monday).status)
        assertEquals(0.0, ChallengeEvaluator.evaluate(stage.copy(qualifier = "Awake"), snapshot(records), monday).progress, 0.0)
        assertEquals(ChallengeStatus.COMPLETED, ChallengeEvaluator.evaluate(schedule, snapshot(records), monday).status)
        assertEquals(ChallengeStatus.AWAITING_DATA, ChallengeEvaluator.evaluate(schedule, snapshot(records.copy(sleepEnd = null)), monday).status)
    }

    @Test fun workoutTypeAndStartTimeUseRecordedSessions() {
        val run = RecordedActivity("1", "Run", "treadmill_running", "${monday}T06:45:00", 3.0, 20.0)
        val walk = RecordedActivity("2", "Walk", "walking", "10:00", 2.0, 25.0)
        val records = day(monday, Metric.ACTIVITIES to 2.0).copy(activities = listOf(run, run, walk), workoutsRead = true)
        val type = challenge(ChallengeMeasure.WORKOUT_TYPE).copy(qualifier = "Running")
        val start = challenge(ChallengeMeasure.WORKOUT_START).copy(timeMinutes = 7 * 60)
        assertEquals(1.0, ChallengeEvaluator.periods(type, snapshot(records), monday, p).single().value!!, 0.0)
        assertEquals(ChallengeStatus.COMPLETED, ChallengeEvaluator.evaluate(start, snapshot(records), monday).status)
        assertEquals(0.0, ChallengeEvaluator.evaluate(start.copy(timeMinutes = 6 * 60), snapshot(records), monday).progress, 0.0)
    }

    @Test fun belowAndPersonalRangeOnlyCountClosedDays() {
        val c = challenge(ChallengeMeasure.ACTIVE, ChallengeCondition.BELOW).copy(target = 30.0)
        val records = snapshot(day(monday, Metric.ACTIVE to 20.0))
        assertEquals(0.0, ChallengeEvaluator.evaluate(c, records, monday).progress, 0.0)
        assertEquals(ChallengeStatus.COMPLETED, ChallengeEvaluator.evaluate(c, records, monday.plusDays(1)).status)

        val dates = (1L..7L).map { day(monday.minusDays(it), Metric.HRV to (35.0 + it)) }
        val data = snapshot(*dates.toTypedArray(), day(monday, Metric.HRV to 39.0))
        val range = ChallengeEvaluator.personalRange(data, ChallengeMeasure.HRV, monday, zone)!!
        val personalized = challenge(ChallengeMeasure.HRV, ChallengeCondition.PERSONAL_RANGE)
            .copy(rangeMin = range.first, rangeMax = range.second)
        assertNull(ChallengeEvaluator.personalRange(snapshot(dates.first()), ChallengeMeasure.HRV, monday, zone))
        assertEquals(0.0, ChallengeEvaluator.evaluate(personalized, data, monday).progress, 0.0)
        assertEquals(ChallengeStatus.COMPLETED, ChallengeEvaluator.evaluate(personalized, data, monday.plusDays(1)).status)
    }

    @Test fun dailyGoalsUseAnyOrAllAndRecalculateAfterAnEdit() {
        val c = challenge(ChallengeMeasure.STEPS).copy(tracking = ChallengeTracking.PERSONAL_GOALS,
            measure = null, goals = setOf(Metric.STEPS, Metric.SLEEP), requiredDays = 3)
        val a = day(monday, Metric.STEPS to 6500.0, Metric.SLEEP to 7.0)
        val b = day(monday.plusDays(1), Metric.STEPS to 6000.0, Metric.SLEEP to 8.0)
        val snapshot = snapshot(a, a, b)
        val date = monday.plusDays(1)
        val progress = ChallengeEvaluator.evaluate(c, snapshot, date, p)
        assertEquals(1.0, progress.progress, 0.0)
        assertEquals(2.0, ChallengeEvaluator.evaluate(c.copy(goalMatch = GoalMatch.ANY), snapshot, date, p).progress, 0.0)
        assertEquals(0.0, ChallengeEvaluator.evaluate(progress, snapshot, date, p.copy(stepGoal = 7000)).progress, 0.0)
        val missing = ChallengeEvaluator.evaluate(progress, snapshot, date, p.copy(enabledGoals = setOf(Metric.STEPS)))
        assertEquals(ChallengeStatus.AWAITING_DATA, missing.status)
        assertEquals(1.0, missing.progress, 0.0)
        val reward = ChallengeEvaluator.evaluate(c.copy(requiredDays = 1), snapshot, date, p)
        assertEquals(reward, ChallengeEvaluator.evaluate(reward, snapshot, date, p.copy(enabledGoals = emptySet())))
    }

    @Test fun weeklyGoalCountsOneFullCalendarWeekWithoutBorrowingFromPartialWeeks() {
        val prefs = p.copy(workoutGoal = 2, enabledGoals = setOf(Metric.ACTIVITIES))
        val c = challenge(ChallengeMeasure.WORKOUT_COUNT).copy(tracking = ChallengeTracking.PERSONAL_GOALS,
            measure = null, goals = setOf(Metric.ACTIVITIES), start = monday.minusDays(2), duration = 9)
        assertEquals(listOf(monday), c.weekStarts())
        val prior = day(monday.minusDays(1), Metric.ACTIVITIES to 3.0)
        val a = day(monday, Metric.ACTIVITIES to 1.0)
        val b = day(monday.plusDays(1), Metric.ACTIVITIES to 1.0)
        assertEquals(0.0, ChallengeEvaluator.evaluate(c, snapshot(prior, a), monday, prefs).progress, 0.0)
        assertEquals(ChallengeStatus.COMPLETED, ChallengeEvaluator.evaluate(c, snapshot(prior, a, a, b), monday.plusDays(1), prefs).status)
        assertNotNull(c.copy(duration = 6).validation())
        assertNotNull(c.copy(goals = setOf(Metric.STEPS, Metric.ACTIVITIES)).validation())
    }

    @Test fun sourceSwitchOrReplacementKeepsProgressWithoutMixingMeasurements() {
        val c = challenge(ChallengeMeasure.STEPS).copy(progress = 1.0, requiredDays = 2)
        val rows = snapshot(day(monday, Metric.STEPS to 5000.0), scope = "new-account")
        val result = ChallengeEvaluator.evaluate(c, rows, monday, p)
        assertEquals(ChallengeStatus.AWAITING_DATA, result.status)
        assertEquals(1.0, result.progress, 0.0)
    }

    @Test fun progressNotificationUsesSelectedFreshReadingsAndRollsOverWithoutYesterdayValues() {
        val prefs = p.copy(stepGoal = 5000, notificationMetrics = setOf(Metric.STEPS, Metric.DISTANCE))
        val records = snapshot(day(monday, Metric.STEPS to 3200.0, Metric.ACTIVE_CALORIES to 60.0),
            day(monday.plusDays(1), Metric.STEPS to 800.0))
        val result = ProgressNotifications.progress(prefs, records, monday, zone)
        assertEquals(1, result.lines.size)
        assertTrue(result.lines.single().contains(" / "))
        assertEquals(timestamp, result.updated)
        assertNull(ProgressNotifications.progress(prefs, records, monday.plusDays(1), zone).updated)
        assertTrue(ProgressNotifications.progress(prefs.copy(mode = DataMode.GARMIN), records, monday, zone).lines.isEmpty())
    }

    @Test fun interruptedSetupRoundTripsSelectedProviderAndOutcomes() {
        val draft = p.copy(onboardingStep = SetupStep.NOTIFICATIONS.ordinal, setupSource = DataMode.HEALTH_CONNECT,
            sourceSetup = SetupStatus.COMPLETE, goalsSetup = SetupStatus.SKIPPED, enabledGoals = emptySet())
        assertEquals(draft, PreferencesCodec.decode(PreferencesCodec.encode(draft)))
    }

    @Test fun garminVerificationRequiresBearerDataReadAndAllowsAnAuthorizedEmptyResult() = runTest {
        val claims = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"exp":${timestamp.plusSeconds(3600).epochSecond}}""".toByteArray())
        val token = "header.$claims.signature"
        var checked = false
        val api = GarminApi(GarminTransport { method, path, authorization, _ ->
            if (method == "POST") {
                assertEquals("ApiKey synthetic-test-key", authorization)
                GarminResponse(200, token)
            } else {
                assertTrue(path.startsWith("/usersummary-service/"))
                assertEquals("Bearer $token", authorization)
                checked = true
                GarminResponse(200, """{"data":[]}""")
            }
        }, { null }, { timestamp })
        api.validateKey("synthetic-test-key", monday)
        assertTrue(checked)
    }

    @Test fun garminMapsDistinctEnergyFloorsHydrationAndSleepTimes() {
        val payload = JSONObject("""{"data":[{"source":"garmin","calendarDate":"$monday",
            "calories":{"burnedTotal":1400,"burnedActive":150},"movement":{"floorsAscended":{"value":3}},
            "hydration":{"consumedInFractionalMl":1250}}]}""")
        val result = GarminMapping.summaries(payload, timestamp).getValue(monday)
        assertEquals(1400.0, result.value(Metric.CALORIES)!!, 0.0)
        assertEquals(150.0, result.value(Metric.ACTIVE_CALORIES)!!, 0.0)
        assertEquals(3.0, result.value(Metric.FLOORS)!!, 0.0)
        assertEquals(1.25, result.value(Metric.HYDRATION)!!, 0.0)
        val days = mutableMapOf(monday to result)
        GarminMapping.sleep(org.json.JSONArray("""[{"calendarDate":"$monday","sleepTimeSeconds":25000,
            "sleepEndTimestampGMT":"$timestamp"}]"""), days, timestamp)
        assertEquals(timestamp, GarminMapping.decode(GarminMapping.encode(days.getValue(monday))).sleepEnd)
    }
}
