package com.pulse.app.core

import java.time.Instant
import java.util.Base64
import java.util.UUID

enum class ModeIcon(val title: String, val glyph: String) {
    EVERYDAY("Dashboard", "dots"), FITNESS("Training", "dumbbell"), RECOVERY("Sleep", "moon"),
    HEART("Heart", "heart"), TARGET("Target", "target"), STEPS("Steps", "steps")
}

enum class ModePreset(val title: String, val icon: ModeIcon, private vararg val metrics: Metric) {
    EVERYDAY("Everyday", ModeIcon.EVERYDAY, Metric.STEPS, Metric.DISTANCE, Metric.SLEEP, Metric.HEART, Metric.GOAL, Metric.WEEKLY),
    FITNESS("Fitness", ModeIcon.FITNESS, Metric.WORKOUT_DURATION, Metric.ACTIVITIES, Metric.ACTIVE, Metric.STEPS, Metric.CALORIES, Metric.HEART, Metric.GOAL),
    RECOVERY("Recovery", ModeIcon.RECOVERY, Metric.SLEEP, Metric.SLEEP_STAGES, Metric.RESTING_HEART, Metric.HRV, Metric.STRESS, Metric.BATTERY);

    fun layout(modeId: String): List<Widget> = AdaptiveFlow.pack(metrics.map { metric ->
        val wide = metric == metrics.first() || metric in setOf(Metric.GOAL, Metric.WEEKLY)
        Widget("${modeId}_${metric.name.lowercase(java.util.Locale.ROOT)}", metric, width = if (wide) 4 else 2)
    })
}

data class PulseMode(
    val id: String, val name: String, val icon: ModeIcon, val createdAt: Instant,
    val preset: ModePreset?, val widgets: List<Widget>, val showObservations: Boolean = preset == ModePreset.EVERYDAY
) {
    fun reset() = copy(widgets = preset?.layout(id).orEmpty())
}

data class PulseModes(val modes: List<PulseMode>, val activeId: String, val removed: RemovedMode? = null) {
    val active get() = modes.first { it.id == activeId }

    fun update(id: String, change: (PulseMode) -> PulseMode) = copy(modes = modes.map { if (it.id == id) change(it) else it })
    fun select(id: String) = if (modes.any { it.id == id }) copy(activeId = id) else this
    fun move(id: String, offset: Int): PulseModes {
        val index = modes.indexOfFirst { it.id == id }
        if (index < 0 || index + offset !in modes.indices) return this
        return copy(modes = modes.toMutableList().apply { add(index + offset, removeAt(index)) })
    }
    fun create(name: String, icon: ModeIcon, preset: ModePreset?, duplicateId: String?, showObservations: Boolean? = null): PulseModes {
        val id = UUID.randomUUID().toString()
        val original = modes.firstOrNull { it.id == duplicateId }
        val widgets = original?.widgets?.map { it.copy(id = UUID.randomUUID().toString()) } ?: preset?.layout(id).orEmpty()
        val mode = PulseMode(id, name, icon, Instant.now(), original?.preset ?: preset, widgets,
            showObservations ?: original?.showObservations ?: (preset == ModePreset.EVERYDAY))
        return copy(modes = modes + mode, activeId = id)
    }
    fun remove(id: String): PulseModes {
        val index = modes.indexOfFirst { it.id == id }
        if (modes.size <= 1 || index < 0) return this
        val remaining = modes.filterNot { it.id == id }
        return copy(modes = remaining, activeId = if (activeId == id) remaining[index.coerceAtMost(remaining.lastIndex)].id else activeId,
            removed = RemovedMode(modes[index], index))
    }
    fun restore(): PulseModes = removed?.let { deleted ->
        copy(modes = modes.toMutableList().apply { add(deleted.index.coerceIn(0, size), deleted.mode) }, removed = null)
    } ?: this

    companion object {
        fun initial(legacyLayout: List<Widget>? = null, now: Instant = Instant.now(), observations: Boolean = true): PulseModes {
            val modes = ModePreset.entries.map { preset ->
                val id = preset.name.lowercase(java.util.Locale.ROOT)
                val layout = if (preset == ModePreset.EVERYDAY && legacyLayout != null) legacyLayout else preset.layout(id)
                PulseMode(id, preset.title, preset.icon, now, preset, layout, observations && preset == ModePreset.EVERYDAY)
            }
            return PulseModes(modes, modes.first().id)
        }
        fun cleanName(name: String) = name.filterNot(Char::isISOControl).trim().take(40)
    }
}

data class RemovedMode(val mode: PulseMode, val index: Int)

object PulseModesCodec {
    fun encode(state: PulseModes): String = buildString {
        appendLine("v1|${state.activeId}")
        state.modes.forEach { appendLine("mode|${encodeMode(it)}") }
        state.removed?.let { appendLine("removed|${it.index}|${encodeMode(it.mode)}") }
    }

    fun decode(value: String): PulseModes? = runCatching {
        val records = value.lineSequence().filter(String::isNotBlank).map { it.split('|') }.toList()
        val header = records.first()
        require(header.size == 2 && header.first() == "v1")
        val modes = records.filter { it.first() == "mode" }.map { decodeMode(it.drop(1)) }
        require(modes.isNotEmpty() && modes.map { it.id }.distinct().size == modes.size)
        val removed = records.firstOrNull { it.first() == "removed" }?.let {
            RemovedMode(decodeMode(it.drop(2)), it[1].toInt())
        }?.takeUnless { removed -> modes.any { it.id == removed.mode.id } }
        PulseModes(modes, header[1].takeIf { active -> modes.any { it.id == active } } ?: modes.first().id, removed)
    }.getOrNull()

    private fun encodeMode(mode: PulseMode) = listOf(mode.id, b64(mode.name), mode.icon.name, mode.createdAt.toString(),
        mode.preset?.name.orEmpty(), b64(LayoutCodec.encode(mode.widgets)), mode.showObservations).joinToString("|")

    private fun decodeMode(fields: List<String>): PulseMode {
        val (id, name, icon, created, preset) = fields
        require(id.matches(Regex("[A-Za-z0-9_-]{1,40}")))
        val source = preset.takeIf(String::isNotEmpty)?.let(ModePreset::valueOf)
        return PulseMode(id, unb64(name), ModeIcon.valueOf(icon), Instant.parse(created), source,
            LayoutCodec.decode(unb64(fields[5])), fields.getOrNull(6)?.toBooleanStrictOrNull() ?: (source == ModePreset.EVERYDAY))
    }

    private fun b64(value: String) = Base64.getUrlEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
    private fun unb64(value: String) = String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
}
