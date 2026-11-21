package com.pulse.app.integration

import com.pulse.app.core.*
import com.pulse.app.integration.garmin.GarminMapping
import com.pulse.app.integration.googlehealth.GoogleHealthMetrics

internal object GoalSources {
    private val capabilities = mapOf(
        DataMode.HEALTH_CONNECT to HealthConnectRepository.supported.associateWith { setOf(HealthConnectRepository.permission(it)) },
        DataMode.GOOGLE_HEALTH to GoogleHealthMetrics.types.flatMap { type -> type.metrics.map { it to type.category.scope } }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() },
        DataMode.GARMIN to GarminMapping.supported.associateWith { emptySet() }
    )

    fun providers(metric: GoalMetric) = capabilities.filterValues { metric.source in it }.keys
    fun metrics(snapshot: HealthSnapshot) = GoalMetrics.entries.filter { providers(it).isNotEmpty() || it.source in snapshot.available }

    fun available(metric: GoalMetric, snapshot: HealthSnapshot, mode: DataMode): Boolean =
        mode != DataMode.NONE && snapshot.mode == mode && snapshot.access.verified && metric.source in snapshot.available

    fun missingReadPermission(metric: GoalMetric, snapshot: HealthSnapshot, mode: DataMode): Boolean {
        val applicable = setOfNotNull(mode, snapshot.secondary?.mode).intersect(providers(metric))
        return applicable.any { provider ->
            val grants = if (provider == mode) snapshot.granted else snapshot.secondary?.granted.orEmpty()
            val required = capabilities[provider]?.get(metric.source).orEmpty()
            required.isNotEmpty() && required.none { it in grants }
        }
    }

    fun availability(metric: GoalMetric, snapshot: HealthSnapshot, mode: DataMode): GoalAvailability {
        val providers = providers(metric)
        if (available(metric, snapshot, mode)) {
            val provider = snapshot.days.lastOrNull { it.readings[metric.source]?.state == DataState.READY }?.readings?.get(metric.source)?.provider
            return GoalAvailability(provider?.let(::setOf) ?: setOf(mode), "Available")
        }
        if (mode == DataMode.NONE) return GoalAvailability(providers, "Connect a source")
        val applicable = if (snapshot.secondary != null) setOf(mode, snapshot.secondary.mode) else setOf(mode)
        val supportedHere = providers.intersect(applicable)
        if (supportedHere.isEmpty()) return GoalAvailability(providers, "Requires another source")
        if (snapshot.mode != mode || snapshot.loading) return GoalAvailability(supportedHere, "Checking access")
        val reads = snapshot.days.lastOrNull()?.readings?.get(metric.source)?.state
        val label = when {
            reads == DataState.UNSUPPORTED -> "Unavailable from this source"
            reads == DataState.PERMISSION_DENIED ||
                snapshot.access.verified && missingReadPermission(metric, snapshot, mode) -> "Read access needed"
            !snapshot.access.verified -> "Connect or review access"
            else -> "Unavailable from this source"
        }
        return GoalAvailability(supportedHere, label)
    }
}

internal data class GoalAvailability(val providers: Set<DataMode>, val label: String)
