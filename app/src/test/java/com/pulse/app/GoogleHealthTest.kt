package com.pulse.app

import com.pulse.app.core.*
import com.pulse.app.data.PreferencesCodec
import com.pulse.app.integration.ProviderPriority
import com.pulse.app.integration.googlehealth.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.URLDecoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

class GoogleHealthTest {
    private val today = LocalDate.parse("2026-11-16")
    private val now = Instant.parse("2026-11-16T12:00:00Z")
    private val zone = ZoneId.of("Europe/Paris")
    private val activity = GoogleHealthCategory.ACTIVITY.scope
    private val sleep = GoogleHealthCategory.SLEEP.scope
    private val vitals = GoogleHealthCategory.VITALS.scope
    private fun token(vararg scopes: String) = GoogleHealthToken("fixture-token", scopes.toSet())
    private fun type(id: String) = GoogleHealthMetrics.types.first { it.id == id }
    private fun ok(json: JSONObject = JSONObject()) = GoogleHealthResponse(200, json)
    private fun error(code: Int, reason: String) = GoogleHealthResponse(code,
        JSONObject().put("error", JSONObject().put("details", JSONArray().put(JSONObject().put("reason", reason)))))
    private fun provider(store: MemoryStore, auth: Authorizer, api: GoogleHealthApi, time: () -> Instant = { now }) =
        GoogleHealthProvider(store, auth, api, time) { zone }

    @Test fun identityUsesTheStableResourceAndRejectsUnlinkedProfiles() = runBlocking {
        val api = GoogleHealthApi({ path, _, body ->
            assertEquals("users/me/identity", path)
            assertNull(body)
            error(400, "ACCOUNT_NOT_LINKED")
        }, {})
        val store = MemoryStore()
        val outcome = runCatching { provider(store, Authorizer(), api).verify("tester@example.test", token(activity)) }.exceptionOrNull()
        assertEquals(GoogleHealthProblem.NOT_LINKED, (outcome as GoogleHealthFailure).problem)
        assertNull(store.connection())
    }

    @Test fun consentWithoutAnIdentityNeverCreatesAConnection() = runBlocking {
        val api = GoogleHealthApi({ _, _, _ -> ok(JSONObject().put("name", "users/me/identity")) }, {})
        val store = MemoryStore()
        val outcome = runCatching { provider(store, Authorizer(), api).verify("tester@example.test", token(activity)) }.exceptionOrNull()
        assertEquals(GoogleHealthProblem.RESPONSE, (outcome as GoogleHealthFailure).problem)
        assertNull(store.connection())
    }

    @Test fun partialConsentExplicitZerosAndMissingRecordsRemainDistinct() = runBlocking {
        val auth = Authorizer(setOf(activity))
        val store = MemoryStore()
        val service = provider(store, auth, GoogleHealthApi({ path, _, body ->
            when {
                path == "users/me/identity" -> ok(identity("verified-id"))
                path.endsWith("/steps/dataPoints:dailyRollUp") ->
                    ok(points("rollupDataPoints", rollup(today, "steps", JSONObject().put("countSum", "0"))))
                else -> {
                    if (path.contains(":dailyRollUp")) assertNotNull(body?.optJSONObject("range"))
                    assertFalse(path.contains("/sleep/"))
                    ok()
                }
            }
        }, {}))
        val credentials = token(activity)
        val verified = service.verify("tester@example.test", credentials)
        val result = service.connectSync(verified, credentials, today)
        assertEquals(GoogleHealthStatus.SYNCED, result.status)
        assertEquals(ProviderAccess.PARTIAL, result.snapshot.access)
        val day = result.snapshot.days.first { it.date == today }
        assertEquals(0.0, day.value(Metric.STEPS)!!, 0.0)
        assertEquals(DataState.READY, day.readings.getValue(Metric.STEPS).state)
        assertNull(day.value(Metric.DISTANCE))
        assertEquals(DataState.EMPTY, day.readings.getValue(Metric.DISTANCE).state)
        assertEquals(DataState.PERMISSION_DENIED, day.readings.getValue(Metric.SLEEP).state)
    }

    @Test fun paginationPreservesFiltersAndEncodesContinuationTokens() = runBlocking {
        val requests = mutableListOf<String>()
        val api = GoogleHealthApi({ path, _, _ ->
            requests += path
            if (requests.size == 1) ok(points("dataPoints", JSONObject().put("dataPointName", "first"))
                .put("nextPageToken", "+ /="))
            else ok(points("dataPoints", JSONObject().put("dataPointName", "second")))
        }, {})
        val records = api.records(type("sleep"), token(sleep), today, today.plusDays(7))
        assertEquals(listOf("first", "second"), records.map { it.getString("dataPointName") })
        val first = URLDecoder.decode(requests[0], "UTF-8")
        assertTrue(first.contains("dataPoints:reconcile?pageSize=25"))
        assertTrue(first.contains("""sleep.interval.civil_end_time >= "2026-11-16" AND sleep.interval.civil_end_time < "2026-11-23""""))
        assertTrue(requests[1].startsWith(requests[0]))
        assertTrue(requests[1].endsWith("pageToken=%2B%20%2F%3D"))
    }

    @Test fun retriesRateLimitAndServerErrorWithBoundedBackoff() = runBlocking {
        val responses = ArrayDeque(listOf(GoogleHealthResponse(429, JSONObject(), 2),
            GoogleHealthResponse(503, JSONObject()), ok(identity("verified-id"))))
        val delays = mutableListOf<Long>()
        val api = GoogleHealthApi({ _, _, _ -> responses.removeFirst() }, { delays += it })
        assertEquals("verified-id", api.identity(token(activity)))
        assertEquals(listOf(2000L, 1000L), delays)
        val heavy = GoogleHealthApi({ _, _, _ -> GoogleHealthResponse(429, JSONObject(), 120) }, { fail("Backoff belongs to WorkManager") })
        assertEquals(GoogleHealthProblem.RETRY, (runCatching { heavy.identity(token(activity)) }.exceptionOrNull() as GoogleHealthFailure).problem)
    }

    @Test fun expiredTokenIsClearedAndRenewedWithoutConsentUi() = runBlocking {
        val store = MemoryStore()
        store.setConnection(GoogleHealthConnection("tester@example.test", "verified-id", setOf(sleep)))
        val auth = Authorizer(setOf(sleep))
        val service = provider(store, auth, GoogleHealthApi({ path, _, _ ->
            if (path == "users/me/identity") {
                if (auth.cleared == 0) error(401, "UNAUTHENTICATED") else ok(identity("verified-id"))
            } else ok()
        }, {}))
        assertEquals(GoogleHealthStatus.NO_DATA, service.load(today).status)
        assertEquals(1, auth.cleared)
        assertEquals(2, auth.requested)
    }

    @Test fun revokedOrSwitchedAccountCannotUseThePreviouslyVerifiedCache() = runBlocking {
        val store = MemoryStore()
        store.setConnection(GoogleHealthConnection("tester@example.test", "verified-id", setOf(sleep)))
        val switched = provider(store, Authorizer(setOf(sleep)), GoogleHealthApi({ path, _, _ ->
            assertEquals("users/me/identity", path)
            ok(identity("different-id"))
        }, {})).load(today)
        assertEquals(GoogleHealthProblem.ACCOUNT_CHANGED, switched.problem)
        assertTrue(switched.snapshot.days.isEmpty())
        val revoked = Authorizer(setOf(sleep)).apply { denied = true }
        val result = provider(store, revoked, GoogleHealthApi({ _, _, _ -> error("No network call for a revoked grant") }, {})).load(today)
        assertEquals(GoogleHealthStatus.REAUTHORIZE, result.status)
        assertTrue(result.snapshot.days.isEmpty())
    }

    @Test fun aRevokedCategoryLeavesTheRemainingCategoryUsable() = runBlocking {
        val store = MemoryStore()
        store.setConnection(GoogleHealthConnection("tester@example.test", "verified-id", setOf(sleep, vitals)))
        val auth = Authorizer(setOf(sleep))
        val result = provider(store, auth, GoogleHealthApi({ path, _, _ ->
            when {
                path == "users/me/identity" -> ok(identity("verified-id"))
                path.contains("/sleep/") -> ok()
                else -> fail("Unpermitted category was requested").let { ok() }
            }
        }, {})).load(today)
        assertEquals(setOf(sleep), result.connection?.scopes)
        assertTrue(Metric.SLEEP in result.snapshot.available)
        assertFalse(Metric.HEART in result.snapshot.available)
    }

    @Test fun refreshUsesCachedWindowsAndWorkerBackfillIsBoundedAndIdempotent() = runBlocking {
        val store = MemoryStore()
        val requests = mutableListOf<String>()
        var time = now
        val service = provider(store, Authorizer(setOf(sleep)), GoogleHealthApi({ path, _, _ ->
            if (path == "users/me/identity") ok(identity("verified-id")) else {
                requests += path
                ok()
            }
        }, {})) { time }
        val credentials = token(sleep)
        val verified = service.verify("tester@example.test", credentials)
        assertEquals(GoogleHealthStatus.NO_DATA, service.connectSync(verified, credentials, today).status)
        assertEquals(2, requests.size)
        service.load(today)
        assertEquals(2, requests.size)
        service.load(today, background = true)
        service.load(today, background = true)
        assertEquals(4, requests.distinct().size)
        time = time.plusSeconds(16 * 60)
        service.load(today)
        assertEquals(5, requests.size)
        assertEquals(requests.first(), requests.last())
    }

    @Test fun anOfflineReadShowsStaleValuesWithoutCompletingGoals() = runBlocking {
        val store = MemoryStore()
        var offline = false
        val service = provider(store, Authorizer(setOf(activity)), GoogleHealthApi({ path, _, _ ->
            if (offline) throw IOException()
            when {
                path == "users/me/identity" -> ok(identity("verified-id"))
                path.endsWith("/steps/dataPoints:dailyRollUp") ->
                    ok(points("rollupDataPoints", rollup(today, "steps", JSONObject().put("countSum", 12000))))
                else -> ok()
            }
        }, {}))
        val credentials = token(activity)
        service.connectSync(service.verify("tester@example.test", credentials), credentials, today)
        offline = true
        val stale = service.load(today).snapshot
        assertEquals(DataState.STALE, stale.days.first().readings[Metric.STEPS]?.state)
        assertEquals(12000.0, stale.days.first().value(Metric.STEPS)!!, 0.0)
        assertNull(Goals.value(Metric.STEPS, stale.days, today))
    }

    @Test fun sleepUsesTheWakeDateAcrossADaylightSavingChange() {
        val row = JSONObject("""{
          "dataPointName":"users/test/dataTypes/sleep/dataPoints/night",
          "sleep":{
            "interval":{"startTime":"2026-10-24T21:30:00Z","endTime":"2026-10-25T06:30:00Z",
                        "startUtcOffset":"7200s","endUtcOffset":"3600s"},
            "summary":{"minutesAsleep":"480","minutesAwake":"60","minutesInSleepPeriod":"540",
              "stagesSummary":[{"type":"DEEP","minutes":"80"}]},
            "outOfBedSegments":[{"startTime":"2026-10-25T03:00:00Z","endTime":"2026-10-25T03:30:00Z"}]
          }
        }""")
        val sleepType = type("sleep")
        val day = GoogleHealthDay()
        assertEquals(LocalDate.parse("2026-10-25"), GoogleHealthMapping.date(row, sleepType, zone))
        GoogleHealthMapping.reduce(sleepType, listOf(row), emptyList(), day, zone)
        assertEquals(8.0, day.values.getValue(Metric.SLEEP), 0.0)
        assertEquals(8.5, day.values.getValue(Metric.TIME_IN_BED), 0.0)
        assertEquals(9.0, day.values.getValue(Metric.SLEEP_PERIOD), 0.0)
        assertEquals(1.0, day.values.getValue(Metric.SLEEP_AWAKE), 0.0)
        assertEquals(450, day.sleepWakeMinute)
        assertEquals(80.0 / 60, day.sleepStages.getValue("deep"), 0.0001)
    }

    @Test fun unitsAndDocumentedWorkoutPayloadMapWithPrecision() {
        val day = GoogleHealthDay()
        fun map(id: String, json: String) = GoogleHealthMapping.reduce(type(id), listOf(JSONObject(json)), emptyList(), day, zone)
        map("distance", """{"distance":{"millimetersSum":"1520400"}}""")
        map("weight", """{"weight":{"weightGramsAvg":71345.5}}""")
        map("hydration-log", """{"hydrationLog":{"amountConsumed":{"millilitersSum":725.5}}}""")
        map("exercise", """{"dataPointName":"users/test/dataTypes/exercise/dataPoints/run",
            "exercise":{"exerciseType":"RUNNING","activeDuration":"1800.5s",
            "interval":{"startTime":"2026-11-16T08:00:00Z","endTime":"2026-11-16T08:45:00Z","startUtcOffset":"3600s"},
            "metricsSummary":{"distanceMillimeters":5000000}}}""")
        assertEquals(1.5204, day.values.getValue(Metric.DISTANCE), 0.00001)
        assertEquals(71.3455, day.values.getValue(Metric.WEIGHT), 0.00001)
        assertEquals(0.7255, day.values.getValue(Metric.HYDRATION), 0.00001)
        assertEquals(30.00833, day.values.getValue(Metric.WORKOUT_DURATION), 0.00001)
        assertEquals("run", day.activities.single().id)
        assertEquals("09:00", day.activities.single().localStart)
    }

    @Test fun workoutIdentityAndOverlapCannotCreateDuplicateProgress() {
        fun workout(id: String, start: String, end: String) = JSONObject("""{
            "dataPointName":"$id","exercise":{"exerciseType":"RUNNING","activeDuration":"1800s","interval":{
            "startTime":"$start","endTime":"$end"}}}
        """)
        val first = workout("a", "2026-11-16T09:00:00Z", "2026-11-16T09:30:00Z")
        val overlap = workout("b", "2026-11-16T09:02:00Z", "2026-11-16T09:32:00Z")
        val day = GoogleHealthDay()
        GoogleHealthMapping.reduce(type("exercise"), listOf(first, first, overlap), emptyList(), day, zone)
        assertEquals(1, day.activities.size)
        assertEquals(1.0, day.values.getValue(Metric.ACTIVITIES), 0.0)
        assertEquals(30.0, day.values.getValue(Metric.WORKOUT_DURATION), 0.0)
    }

    @Test fun sourcePriorityChoosesOneStepsAndWorkoutStreamAndDrivesProgress() {
        val time = now
        fun stream(mode: DataMode, steps: Double, activities: Double) = HealthSnapshot(
            mode = mode, access = ProviderAccess.CONNECTED, sourceScope = mode.name,
            available = setOf(Metric.STEPS, Metric.ACTIVITIES, Metric.ACTIVE), days = listOf(DayData(today,
                mapOf(Metric.STEPS to Reading(steps, DataState.READY, time, mode),
                    Metric.ACTIVITIES to Reading(activities, DataState.READY, time, mode),
                    Metric.ACTIVE to Reading(32.0, DataState.READY, time, mode)), workoutsRead = true)))
        val cloud = stream(DataMode.GOOGLE_HEALTH, 11000.0, 1.0)
        val local = stream(DataMode.HEALTH_CONNECT, 4900.0, 2.0)
        val combined = ProviderPriority.merge(cloud, local, mapOf(Metric.ACTIVITIES to DataMode.HEALTH_CONNECT), zone)
        assertEquals(11000.0, combined.days.single().value(Metric.STEPS)!!, 0.0)
        assertEquals(2.0, combined.days.single().value(Metric.ACTIVITIES)!!, 0.0)
        assertEquals(32.0, Goals.value(Metric.WEEKLY_ACTIVE, combined.days, today)!!, 0.0)
        assertTrue(AchievementEngine.evaluate(combined, Preferences(), today).any { it.definition.id == "steps_10k" && it.unlocked })
        val challenge = PersonalChallenge(name = "Steps", tracking = ChallengeTracking.AUTOMATIC, metric = Metric.STEPS,
            target = 10000.0, unit = "steps", start = today, source = DataMode.GOOGLE_HEALTH, sourceScope = cloud.sourceScope)
        val completed = ChallengeEvaluator.evaluate(challenge, combined, today)
        assertEquals(today, completed.completed)
        assertEquals(completed, ChallengeEvaluator.evaluate(completed, combined, today))
        assertNull(ChallengeEvaluator.evaluate(challenge.copy(sourceScope = "another-account"), combined, today).completed)
    }

    @Test fun disconnectRemovesOnlyTheSelectedAccountWhenRequested() = runBlocking {
        val store = MemoryStore()
        val active = GoogleHealthConnection("tester@example.test", "first", setOf(sleep))
        val other = GoogleHealthConnection("other@example.test", "second", setOf(sleep))
        val sample = GoogleHealthBatch(today, today.plusDays(1), now, emptyList())
        store.save(active, "sleep", sample)
        store.save(other, "sleep", sample)
        store.setConnection(active)
        val auth = Authorizer(setOf(sleep))
        val service = provider(store, auth, GoogleHealthApi({ _, _, _ -> ok() }, {}))
        assertTrue(service.disconnect(false).second)
        assertNotNull(store.batch(active, "sleep", today))
        store.setConnection(active)
        assertTrue(service.disconnect(true).second)
        assertNull(store.connection())
        assertNull(store.batch(active, "sleep", today))
        assertNotNull(store.batch(other, "sleep", today))
        assertEquals(2, auth.revoked)
    }

    @Test fun sourcePrioritiesAndWeeklyGoalsSurvivePreferenceSerialization() {
        val p = Preferences(mode = DataMode.GOOGLE_HEALTH, combineHealthConnect = true, weeklyActiveGoal = 230,
            enabledGoals = setOf(Metric.WEEKLY_ACTIVE),
            providerPriority = mapOf(Metric.STEPS to DataMode.HEALTH_CONNECT, Metric.SLEEP to DataMode.GOOGLE_HEALTH))
        assertEquals(p, PreferencesCodec.decode(PreferencesCodec.encode(p)))
    }

    private fun identity(id: String) = JSONObject().put("healthUserId", id)
    private fun points(key: String, point: JSONObject) = JSONObject().put(key, JSONArray().put(point))
    private fun rollup(date: LocalDate, key: String, value: JSONObject) = JSONObject().put("civilStartTime",
        JSONObject().put("date", JSONObject().put("year", date.year).put("month", date.monthValue).put("day", date.dayOfMonth)))
        .put(key, value)

    private class Authorizer(private val scopes: Set<String> = emptySet()) : GoogleHealthAuthorization {
        var cleared = 0
        var requested = 0
        var revoked = 0
        var denied = false
        override suspend fun request(account: String, scopes: Set<String>): GoogleAuthorization {
            requested++
            if (denied || this.scopes.isEmpty()) throw GoogleHealthFailure(GoogleHealthProblem.CONSENT)
            return GoogleAuthorization.Granted(GoogleHealthToken("fixture-token", this.scopes))
        }
        override suspend fun clear(token: GoogleHealthToken) { cleared++ }
        override suspend fun revoke(account: String, scopes: Set<String>) { revoked++ }
    }

    private class MemoryStore : GoogleHealthStore {
        private var current: GoogleHealthConnection? = null
        private val cache = ConcurrentHashMap<Triple<String, String, LocalDate>, GoogleHealthBatch>()
        override fun connection() = current
        override fun setConnection(connection: GoogleHealthConnection?) { current = connection }
        override fun batch(account: GoogleHealthConnection, type: String, start: LocalDate) = cache[Triple(account.id, type, start)]
        override fun dates(account: GoogleHealthConnection, type: String) =
            cache.keys.filter { it.first == account.id && it.second == type }.map { it.third }.toSet()
        override fun save(account: GoogleHealthConnection, type: String, batch: GoogleHealthBatch) {
            cache[Triple(account.id, type, batch.date)] = batch
        }
        override fun delete(account: GoogleHealthConnection, type: String, start: LocalDate) { cache.remove(Triple(account.id, type, start)) }
        override fun remove(account: GoogleHealthConnection) { cache.keys.removeIf { it.first == account.id } }
        override fun removeAll() { cache.clear(); current = null }
    }
}
