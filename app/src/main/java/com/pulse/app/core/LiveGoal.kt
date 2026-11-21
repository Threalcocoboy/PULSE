package com.pulse.app.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.math.floor

data class LiveGoalPreference(
    val goalId: String? = null,
    val enabled: Boolean = false,
    val dismissed: Boolean = false,
    val hideOnLockScreen: Boolean = false,
    val token: String = "",
    val permissionRequested: Boolean = false
) {
    fun select(id: String) = if (id == goalId && !dismissed) this else
        copy(goalId = id, dismissed = false, token = UUID.randomUUID().toString())

    fun enable(value: Boolean) = copy(enabled = value && goalId != null, dismissed = false,
        token = UUID.randomUUID().toString())

    fun act(expectedToken: String, stop: Boolean) = when {
        !enabled || token != expectedToken -> this
        stop -> copy(enabled = false, dismissed = false)
        else -> copy(dismissed = true)
    }
}

internal enum class LiveGoalStatus(val label: String) {
    SYNCING("Waiting for sync"),
    CONNECT_SOURCE("Connect a health source"),
    PERMISSION_REQUIRED("Health read access needed"),
    UNAVAILABLE("Goal data unavailable"),
    NO_READINGS("No readings this period"),
    TRACKING("Tracking"),
    LIMIT_EXCEEDED("Limit exceeded"),
    OUTSIDE_TARGET("Outside target"),
    COMPLETED("Goal reached")
}

internal data class LiveGoalReading(
    val status: LiveGoalStatus,
    val progress: GoalProgress = GoalProgress(null, null, false),
    val updated: Instant? = null,
    val percent: Int? = null,
    val remaining: Double? = null
)

internal fun PersonalGoal.liveGoalPeriod(today: LocalDate): LocalDate =
    if (completionCadence == GoalCadence.WEEKLY) today.with(DayOfWeek.MONDAY) else today

internal fun PersonalGoal.nextLiveGoalPeriod(now: ZonedDateTime): ZonedDateTime =
    liveGoalPeriod(now.toLocalDate()).plusDays(if (completionCadence == GoalCadence.WEEKLY) 7 else 1)
        .atStartOfDay(now.zone)

internal fun PersonalGoal.liveGoalReading(snapshot: HealthSnapshot?, mode: DataMode,
    today: LocalDate, zone: ZoneId, permissionMissing: Boolean = false): LiveGoalReading {
    if (mode == DataMode.NONE) return LiveGoalReading(LiveGoalStatus.CONNECT_SOURCE)
    if (snapshot == null || snapshot.mode != mode || snapshot.loading) return LiveGoalReading(LiveGoalStatus.SYNCING)
    val days = HistoryMath.unique(snapshot.days).filter { it.date in liveGoalPeriod(today)..today }
    val records = days.mapNotNull { it.readings[metric.source] }
    if (snapshot.access == ProviderAccess.DENIED || permissionMissing ||
        metric.source !in snapshot.available && records.lastOrNull()?.state == DataState.PERMISSION_DENIED) {
        return LiveGoalReading(LiveGoalStatus.PERMISSION_REQUIRED)
    }
    if (!snapshot.access.verified || metric.source !in snapshot.available) return LiveGoalReading(LiveGoalStatus.UNAVAILABLE)
    val progress = Goals.current(this, snapshot, today, zone)
    val value = progress.value ?: return LiveGoalReading(
        if (snapshot.error != null || records.any { it.state in setOf(DataState.ERROR, DataState.STALE, DataState.UNAVAILABLE) })
            LiveGoalStatus.UNAVAILABLE else LiveGoalStatus.NO_READINGS
    )
    val progressTarget = when {
        frequency != GoalFrequency.EACH_PERIOD -> requiredDays.toDouble()
        condition == GoalCondition.MINIMUM && target > 0 -> target
        else -> null
    }
    return LiveGoalReading(
        when (progress.met) {
            true -> LiveGoalStatus.COMPLETED
            false -> if (condition == GoalCondition.MAXIMUM) LiveGoalStatus.LIMIT_EXCEEDED else LiveGoalStatus.OUTSIDE_TARGET
            null -> LiveGoalStatus.TRACKING
        },
        progress,
        days.filter { metric.value(it, zone) != null }.mapNotNull { it.readings[metric.source]?.updated }
            .maxOrNull() ?: snapshot.synced,
        progressTarget?.let { floor((value / it).coerceIn(0.0, 1.0) * 100).toInt() },
        progressTarget?.minus(value)?.coerceAtLeast(0.0)
    )
}
