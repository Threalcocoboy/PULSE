package com.pulse.app.integration.googlehealth

import android.content.Context
import com.pulse.app.core.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal enum class GoogleHealthStatus(val title: String) {
    DISCONNECTED("Not connected"), AUTHORIZING("Connecting"), CONSENT("Consent required"),
    VERIFYING("Verifying Google Health profile"), UNLINKED("Google Health profile required"),
    FIRST_SYNC("Connected · waiting for first sync"), SYNCING("Synchronizing"),
    SYNCED("Connected · synchronized"), NO_DATA("Connected · no data available"),
    REAUTHORIZE("Reauthorization required"), ERROR("Sync error")
}

internal data class GoogleHealthResult(
    val status: GoogleHealthStatus = GoogleHealthStatus.DISCONNECTED,
    val connection: GoogleHealthConnection? = null,
    val snapshot: HealthSnapshot = HealthSnapshot(mode = DataMode.GOOGLE_HEALTH),
    val problem: GoogleHealthProblem? = null
) {
    val initialSyncFinished get() = status in setOf(GoogleHealthStatus.SYNCED, GoogleHealthStatus.NO_DATA)
}

internal class GoogleHealthProvider(
    private val store: GoogleHealthStore,
    private val authorization: GoogleHealthAuthorization,
    private val api: GoogleHealthApi = GoogleHealthApi(),
    private val clock: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault
) {
    constructor(context: Context) : this(GoogleHealthStorage(context), GoogleHealthAuth(context))

    suspend fun connection(): GoogleHealthConnection? = withContext(Dispatchers.IO) { store.connection() }

    suspend fun pausedSnapshot(today: LocalDate): HealthSnapshot = withContext(Dispatchers.IO) {
        val empty = HealthSnapshot(mode = DataMode.GOOGLE_HEALTH)
        try {
            val local = store.connection()?.let { cached(it, today) } ?: empty
            local.copy(status = GoogleHealthAvailability.paused, access = ProviderAccess.DISCONNECTED)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            empty.copy(status = "Google Health sync is paused. The local cache could not be opened.")
        }
    }

    suspend fun verify(account: String, token: GoogleHealthToken): GoogleHealthConnection = withContext(Dispatchers.IO) {
        val identity = api.identity(token)
        val lastSync = store.connection()?.takeIf { it.id == identity }?.synced
        GoogleHealthConnection(account, identity, token.scopes, lastSync).also(store::setConnection)
    }

    suspend fun connectSync(connection: GoogleHealthConnection, token: GoogleHealthToken, today: LocalDate): GoogleHealthResult =
        withContext(Dispatchers.IO) { safely(today) { import(connection, token, today, background = false, force = true) } }

    suspend fun load(today: LocalDate, background: Boolean = false, force: Boolean = false): GoogleHealthResult =
        withContext(Dispatchers.IO) {
            safely(today) {
                val current = store.connection() ?: return@safely result(GoogleHealthStatus.DISCONNECTED)
                suspend fun sync(token: GoogleHealthToken): GoogleHealthResult {
                    val identity = api.identity(token)
                    if (identity != current.id) throw GoogleHealthFailure(GoogleHealthProblem.ACCOUNT_CHANGED)
                    val refreshed = GoogleHealthConnection(current.account, identity, token.scopes, current.synced)
                    store.setConnection(refreshed)
                    return import(refreshed, token, today, background, force)
                }
                val token = fresh(current)
                try {
                    sync(token)
                } catch (e: GoogleHealthFailure) {
                    if (e.problem != GoogleHealthProblem.EXPIRED) throw e
                    authorization.clear(token)
                    try {
                        sync(fresh(current))
                    } catch (renewal: GoogleHealthFailure) {
                        if (renewal.problem == GoogleHealthProblem.EXPIRED) throw GoogleHealthFailure(GoogleHealthProblem.REAUTHORIZE)
                        throw renewal
                    }
                }
            }
        }

    suspend fun disconnect(removeData: Boolean): Pair<GoogleHealthConnection?, Boolean> = withContext(Dispatchers.IO) {
        val connection = store.connection() ?: return@withContext null to true
        val revoked = try {
            authorization.revoke(connection.account, connection.scopes)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
        if (removeData) store.remove(connection)
        store.setConnection(null)
        connection to revoked
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        store.removeAll()
    }

    private suspend fun fresh(connection: GoogleHealthConnection): GoogleHealthToken {
        val prior = connection.scopes
        if (prior.isEmpty()) throw GoogleHealthFailure(GoogleHealthProblem.REAUTHORIZE)
        try {
            val response = authorization.request(connection.account, prior)
            if (response is GoogleAuthorization.Granted) return response.token
        } catch (e: GoogleHealthFailure) {
            if (e.problem !in setOf(GoogleHealthProblem.CONSENT, GoogleHealthProblem.REAUTHORIZE)) throw e
        }
        // A previously granted scope can be withdrawn independently in Google's account settings.
        if (prior.size > 1) for (scope in prior) {
            try {
                val response = authorization.request(connection.account, setOf(scope))
                if (response is GoogleAuthorization.Granted) return response.token
            } catch (e: GoogleHealthFailure) {
                if (e.problem !in setOf(GoogleHealthProblem.CONSENT, GoogleHealthProblem.REAUTHORIZE)) throw e
            }
        }
        throw GoogleHealthFailure(GoogleHealthProblem.REAUTHORIZE)
    }

    private suspend fun import(
        connection: GoogleHealthConnection, token: GoogleHealthToken, today: LocalDate, background: Boolean, force: Boolean
    ): GoogleHealthResult {
        val now = clock()
        val current = GoogleHealthWindow.containing(today)
        val previous = GoogleHealthWindow.containing(current.start.minusDays(1))
        val types = GoogleHealthMetrics.granted(connection.scopes)
        val limiter = Semaphore(4)
        val failures = coroutineScope {
            types.map { type ->
                async {
                    limiter.withPermit {
                        try {
                            val cached = store.batch(connection, type.id, current.start)
                            if (force || cached == null || cached.through <= today ||
                                Duration.between(cached.updated, now) >= Duration.ofMinutes(15)) {
                                fetch(connection, type, token, current.start, today.plusDays(1), now)
                            }
                            val prior = store.batch(connection, type.id, previous.start)
                            if ((background || connection.synced != null || cached == null) && (prior == null || prior.through < previous.end ||
                                current.start.plusDays(3) > today && Duration.between(prior.updated, now) >= Duration.ofHours(6))) {
                                fetch(connection, type, token, previous.start, previous.end, now)
                            }
                            if (background) {
                                val existing = store.dates(connection, type.id)
                                val older = generateSequence(previous.start.minusDays(GoogleHealthWindow.DAYS)) {
                                    it.minusDays(GoogleHealthWindow.DAYS)
                                }.takeWhile { it.plusDays(GoogleHealthWindow.DAYS) > today.minusDays(HISTORY_DAYS) }
                                    .firstOrNull { it !in existing }
                                if (older != null) fetch(connection, type, token, older, older.plusDays(GoogleHealthWindow.DAYS), now)
                                existing.filter { it.plusDays(GoogleHealthWindow.DAYS) <= today.minusDays(HISTORY_DAYS) }
                                    .forEach { store.delete(connection, type.id, it) }
                            }
                            null
                        } catch (e: GoogleHealthFailure) {
                            if (e.problem in setOf(GoogleHealthProblem.EXPIRED, GoogleHealthProblem.NOT_LINKED)) throw e
                            type to e.problem
                        }
                    }
                }
            }.awaitAll().filterNotNull().toMap()
        }.toMutableMap()
        if ((background || connection.synced != null) && Metric.HEART in types.flatMap { it.metrics } &&
            failures.keys.none { Metric.HEART in it.metrics }) {
            val heartType = types.first { Metric.HEART in it.metrics }
            val sampleDay = if (background && store.batch(connection, HEART_SAMPLES, today.minusDays(1)) == null) today.minusDays(1) else today
            val cached = store.batch(connection, HEART_SAMPLES, sampleDay)
            if (cached == null || Duration.between(cached.updated, now) >= Duration.ofHours(1)) {
                try {
                    val samples = api.records(heartType, token, sampleDay, sampleDay.plusDays(1), GoogleHealthMethod.RECONCILE)
                    store.save(connection, HEART_SAMPLES, GoogleHealthBatch(sampleDay, sampleDay.plusDays(1), now, samples))
                    store.dates(connection, HEART_SAMPLES).filter { it < today.minusDays(6) }
                        .forEach { store.delete(connection, HEART_SAMPLES, it) }
                } catch (e: GoogleHealthFailure) {
                    if (e.problem in setOf(GoogleHealthProblem.EXPIRED, GoogleHealthProblem.NOT_LINKED)) throw e
                    // Daily heart-rate averages remain available when intraday telemetry fails.
                }
            }
        }
        val problem = failures.values.firstOrNull { it != GoogleHealthProblem.UNSUPPORTED }
        val effective = if (problem == null) {
            val syncTime = types.mapNotNull { store.batch(connection, it.id, current.start)?.updated }.maxOrNull()
            GoogleHealthConnection(connection.account, connection.id, connection.scopes, syncTime)
                .also(store::setConnection)
        } else connection
        val snapshot = cached(effective, today, failures)
        val hasData = snapshot.heartSamples.isNotEmpty() ||
            snapshot.days.any { day -> day.readings.values.any { it.value != null } || day.recordDetails.isNotEmpty() }
        val status = when {
            problem != null -> GoogleHealthStatus.ERROR
            !hasData -> GoogleHealthStatus.NO_DATA
            else -> GoogleHealthStatus.SYNCED
        }
        return result(status, effective, snapshot, problem)
    }

    private suspend fun fetch(
        connection: GoogleHealthConnection, type: GoogleHealthType, token: GoogleHealthToken,
        start: LocalDate, end: LocalDate, now: Instant
    ) {
        val points = api.records(type, token, start, end)
        if (type.id in setOf("exercise", "blood-glucose")) {
            points.forEach { it.optJSONObject(type.payloadKey)?.remove("notes") }
        }
        val rawLogs = type.id in setOf("nutrition-log", "hydration-log")
        val details = if (rawLogs) GoogleHealthMapping.unique(api.records(type, token, start, end, GoogleHealthMethod.LIST)) else emptyList()
        if (type.id == "nutrition-log") enrichFoodReferences(details, token)
        store.save(connection, type.id, GoogleHealthBatch(start, end, now, points, details))
    }

    private suspend fun enrichFoodReferences(details: List<org.json.JSONObject>, token: GoogleHealthToken) {
        val names = mutableMapOf<Pair<String, String>, String?>()
        suspend fun name(reference: String, type: String, key: String): String? {
            val index = reference to type
            if (index in names) return names[index]
            if (names.size >= 24) return null
            val result = try {
                api.foodReference(reference, type, token)?.optJSONObject(key).string("displayName")
            } catch (e: GoogleHealthFailure) {
                if (e.problem in setOf(GoogleHealthProblem.EXPIRED, GoogleHealthProblem.NOT_LINKED)) throw e
                null
            }
            names[index] = result
            return result
        }
        for (point in details) {
            val log = point.optJSONObject("nutritionLog") ?: continue
            if (log.string("foodDisplayName") == null) {
                val reference = log.string("food")
                if (reference != null) name(reference, "food", "food")?.let { log.put("foodDisplayName", it) }
            }
            val serving = log.optJSONObject("serving") ?: continue
            if (serving.string("foodMeasurementUnitDisplayName") == null) {
                val reference = serving.string("foodMeasurementUnit")
                if (reference != null) name(reference, "food-measurement-unit", "foodMeasurementUnit")
                    ?.let { serving.put("foodMeasurementUnitDisplayName", it) }
            }
        }
    }

    private fun cached(connection: GoogleHealthConnection, today: LocalDate,
        failures: Map<GoogleHealthType, GoogleHealthProblem> = emptyMap(), stale: Boolean = false): HealthSnapshot {
        val types = GoogleHealthMetrics.granted(connection.scopes)
        val denied = types.filter { failures[it] == GoogleHealthProblem.PERMISSION }.flatMap { it.metrics }.toSet()
        val unsupported = types.filter { failures[it] == GoogleHealthProblem.UNSUPPORTED }.flatMap { it.metrics }.toSet()
        val available = types.flatMap { it.metrics }.toSet() - denied - unsupported
        val localZone = zone()
        val oldest = today.minusDays(HISTORY_DAYS - 1)
        val dates = generateSequence(today) { it.minusDays(1).takeUnless { previous -> previous < oldest } }.toList()
        val readings = mutableMapOf<LocalDate, MutableMap<Metric, Reading>>()
        val days = mutableMapOf<LocalDate, GoogleHealthDay>()
        for (type in types) {
            if (failures[type] in setOf(GoogleHealthProblem.PERMISSION, GoogleHealthProblem.UNSUPPORTED)) continue
            for (start in store.dates(connection, type.id).sorted()) {
                if (start > today || start.plusDays(GoogleHealthWindow.DAYS) <= oldest) continue
                val batch = store.batch(connection, type.id, start) ?: continue
                val byDate = GoogleHealthMapping.unique(batch.points).groupBy {
                    GoogleHealthMapping.date(it, type, localZone, type.method == GoogleHealthMethod.DAILY_ROLLUP)
                }
                val details = batch.details.groupBy { GoogleHealthMapping.date(it, type, localZone) }
                for (date in dates.filter { it >= batch.date && it < batch.through }) {
                    val day = days.getOrPut(date, ::GoogleHealthDay)
                    GoogleHealthMapping.reduce(type, byDate[date].orEmpty(), details[date].orEmpty(), day, localZone)
                    for (metric in type.metrics) {
                        val value = day.values[metric]
                        val state = when {
                            value == null && day.details[metric].isNullOrEmpty() -> DataState.EMPTY
                            stale || type in failures || date == today && Duration.between(batch.updated, clock()) > Duration.ofHours(2) -> DataState.STALE
                            else -> DataState.READY
                        }
                        readings.getOrPut(date, ::mutableMapOf)[metric] = Reading(value, state, batch.updated, DataMode.GOOGLE_HEALTH)
                    }
                }
            }
        }
        val records = dates.filter { it == today || it in days }.map { date ->
            val day = days[date] ?: GoogleHealthDay()
            val values = readings[date].orEmpty()
            val workoutsRead = values[Metric.ACTIVITIES]?.let { it.state in setOf(DataState.READY, DataState.EMPTY) } == true &&
                !stale && types.none { Metric.ACTIVITIES in it.metrics && it in failures }
            val complete = date < today && values[Metric.STEPS]?.let {
                it.state == DataState.READY && it.updated?.isBefore(date.plusDays(1).atStartOfDay(localZone).toInstant()) == false
            } == true
            DayData(date, GoogleHealthMetrics.metrics.associateWith { metric -> values[metric] ?: Reading(state = when {
                metric in unsupported -> DataState.UNSUPPORTED
                metric !in available -> DataState.PERMISSION_DENIED
                else -> DataState.EMPTY
            }, provider = DataMode.GOOGLE_HEALTH) },
                complete = complete, sleepStages = day.sleepStages, activities = day.activities, sleepStart = day.sleepStart, sleepEnd = day.sleepEnd,
                workoutsRead = workoutsRead, sleepWakeMinute = day.sleepWakeMinute, recordDetails = day.details)
        }
        val samples = if (Metric.HEART in available) store.dates(connection, HEART_SAMPLES)
            .filter { it in today.minusDays(6)..today }.sorted().flatMap { date ->
                store.batch(connection, HEART_SAMPLES, date)?.points.orEmpty().mapNotNull(GoogleHealthMapping::heartSample)
            }.distinctBy { it.time } else emptyList()
        return HealthSnapshot(days = records, synced = connection.synced, available = available, granted = connection.scopes,
            mode = DataMode.GOOGLE_HEALTH, sourceScope = connection.sourceScope, heartSamples = samples,
            access = if (connection.scopes.containsAll(GoogleHealthMetrics.scopes)) ProviderAccess.CONNECTED else ProviderAccess.PARTIAL)
    }

    private suspend fun safely(today: LocalDate, action: suspend () -> GoogleHealthResult): GoogleHealthResult {
        try {
            return action()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val problem = (e as? GoogleHealthFailure)?.problem ?: GoogleHealthProblem.STORAGE
            val current = runCatching { store.connection() }.getOrNull()
            val status = when (problem) {
                GoogleHealthProblem.NOT_LINKED -> GoogleHealthStatus.UNLINKED
                GoogleHealthProblem.CONSENT -> GoogleHealthStatus.CONSENT
                GoogleHealthProblem.REAUTHORIZE, GoogleHealthProblem.EXPIRED, GoogleHealthProblem.PERMISSION,
                GoogleHealthProblem.ACCOUNT_CHANGED -> GoogleHealthStatus.REAUTHORIZE
                else -> GoogleHealthStatus.ERROR
            }
            val snapshot = if (current != null && problem in setOf(GoogleHealthProblem.NETWORK, GoogleHealthProblem.RETRY)) {
                runCatching { cached(current, today, stale = true).copy(access = ProviderAccess.ERROR) }.getOrNull()
            } else null
            return result(status, current, snapshot, problem)
        }
    }

    private fun result(status: GoogleHealthStatus, connection: GoogleHealthConnection? = null,
        snapshot: HealthSnapshot? = null, problem: GoogleHealthProblem? = null): GoogleHealthResult {
        val access = when (status) {
            GoogleHealthStatus.DISCONNECTED -> ProviderAccess.DISCONNECTED
            GoogleHealthStatus.CONSENT, GoogleHealthStatus.UNLINKED, GoogleHealthStatus.REAUTHORIZE -> ProviderAccess.DENIED
            else -> ProviderAccess.ERROR
        }
        val health = (snapshot ?: HealthSnapshot(mode = DataMode.GOOGLE_HEALTH, access = access))
            .copy(status = "Google Health · ${status.title}", error = problem?.description)
        return GoogleHealthResult(status, connection, health, problem)
    }

    companion object {
        private const val HEART_SAMPLES = "heart-rate-samples"
        internal const val HISTORY_DAYS = 365L
    }
}
