package com.pulse.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pulse.app.PulseViewModel
import com.pulse.app.core.*

@Composable internal fun ModeSwitcher(mode: PulseMode, open: () -> Unit) {
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    OutlinedButton(onClick = open, modifier = Modifier.pulsePress(press), interactionSource = press,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        PulseSwap(mode.icon to mode.name, label = "Active mode", distance = 7) { (icon, title) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Glyph(icon.glyph, Modifier.size(16.dp), Red)
                Text(title, modifier = Modifier.widthIn(max = 180.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(5.dp))
        Glyph("next", Modifier.size(14.dp).rotate(90f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ModeSheet(state: PulseModes, vm: PulseViewModel, dismiss: () -> Unit) {
    var page by rememberSaveable { mutableStateOf("picker") }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var icon by rememberSaveable { mutableStateOf(ModeIcon.EVERYDAY) }
    var observations by rememberSaveable { mutableStateOf(false) }
    var template by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }
    var actionId by rememberSaveable { mutableStateOf<String?>(null) }
    val motion = LocalAnimations.current
    val haptic = LocalHapticFeedback.current
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    fun edit(mode: PulseMode?, source: PulseMode? = null) {
        editId = mode?.id
        name = mode?.name ?: source?.let { "${it.name.take(35)} copy" }.orEmpty()
        icon = mode?.icon ?: source?.icon ?: ModeIcon.EVERYDAY
        observations = mode?.showObservations ?: source?.showObservations ?: false
        template = source?.let { "copy:${it.id}" }.orEmpty()
        page = "edit"
    }
    fun back() { page = if (page == "edit") "manage" else "picker" }
    PulseSheet(dismiss) { close ->
        BackHandler(page != "picker", onBack = ::back)
        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).imePadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (page != "picker") IconAction("back", "Back", onClick = ::back)
                Text(when (page) { "edit" -> if (editId == null) "New mode" else "Edit mode"; "manage" -> "Manage modes"; else -> "PULSE Modes" },
                    style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconAction("close", "Close modes", onClick = { close(dismiss) })
            }
            PulseSwap(page, label = "Modes sheet page", distance = 15,
                direction = { from, to -> listOf("picker", "manage", "edit").indexOf(to).compareTo(listOf("picker", "manage", "edit").indexOf(from)) }
            ) { shown -> when (shown) {
                "picker" -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.modes.forEachIndexed { index, mode ->
                        val active = mode.id == state.activeId
                        Row(Modifier.reveal(index, scale = .98f).fillMaxWidth().heightIn(min = 48.dp).selectable(active, role = Role.RadioButton) {
                            if (!active && preferences.haptics) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            close { vm.selectMode(mode.id); dismiss() }
                        }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val color by animateColorAsState(if (active) Red else MaterialTheme.colorScheme.onSurfaceVariant,
                                PulseMotion.tween(motion, PulseMotion.Selection), label = "Mode selection")
                            Glyph(mode.icon.glyph, color = color)
                            Text(mode.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            PulseVisibility(active) { Glyph("check", color = Red) }
                        }
                    }
                    HorizontalDivider()
                    SmallAction("Manage modes") { page = "manage" }
                }
                "manage" -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { PulseButton("New mode") { edit(null) } }
                    state.removed?.let { deleted ->
                        item { TextButton(onClick = vm::restoreMode) { Text("Restore ${deleted.mode.name}") } }
                    }
                    itemsIndexed(state.modes, key = { _, mode -> mode.id }) { index, mode ->
                        var menu by remember(mode.id) { mutableStateOf(false) }
                        PulseCard(Modifier.fillMaxWidth().then(if (motion) Modifier.animateItem(placementSpec = PulseMotion.settle(true)) else Modifier)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Glyph(mode.icon.glyph, color = Red)
                                Column(Modifier.weight(1f)) {
                                    PulseSwap(mode.name, label = "Mode name") { Text(it, style = MaterialTheme.typography.titleMedium) }
                                    MetaText("${mode.widgets.size} widgets${if (mode.id == state.activeId) " · Active" else ""}")
                                }
                                Box {
                                    IconAction("dots", "Options for ${mode.name}") { menu = true }
                                    DropdownMenu(menu, { menu = false }) {
                                    DropdownMenuItem({ Text("Edit mode") }, { menu = false; edit(mode) })
                                        DropdownMenuItem({ Text("Duplicate") }, { menu = false; edit(null, mode) })
                                        DropdownMenuItem({ Text("Move up") }, { menu = false; vm.moveMode(mode.id, -1) }, enabled = index > 0)
                                        DropdownMenuItem({ Text("Move down") }, { menu = false; vm.moveMode(mode.id, 1) }, enabled = index < state.modes.lastIndex)
                                        DropdownMenuItem({ Text("Reset layout") }, { menu = false; actionId = mode.id; confirm = "reset" })
                                        DropdownMenuItem({ Text("Remove mode") }, { menu = false; actionId = mode.id; confirm = "remove" }, enabled = state.modes.size > 1)
                                    }
                                }
                            }
                        }
                    }
                }
                else -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { name = it.take(40) }, label = { Text("Mode name") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    Label("ICON")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ModeIcon.entries.forEach { choice ->
                            FilterChip(icon == choice, { icon = choice }, label = { Text(choice.title) },
                                leadingIcon = { Glyph(choice.glyph, Modifier.size(16.dp)) })
                        }
                    }
                    if (editId == null) {
                        var templatesOpen by remember { mutableStateOf(false) }
                        val preset = ModePreset.entries.firstOrNull { it.name == template }
                        val copy = state.modes.firstOrNull { "copy:${it.id}" == template }
                        Box {
                            OutlinedButton({ templatesOpen = true }) { Text(preset?.title ?: copy?.let { "Copy ${it.name}" } ?: "Empty layout") }
                            DropdownMenu(templatesOpen, { templatesOpen = false }, modifier = Modifier.heightIn(max = 280.dp)) {
                                DropdownMenuItem({ Text("Empty layout") }, { template = ""; observations = false; templatesOpen = false })
                                ModePreset.entries.forEach { choice ->
                                    DropdownMenuItem({ Text("${choice.title} preset") }, { template = choice.name; observations = choice == ModePreset.EVERYDAY; templatesOpen = false })
                                }
                                state.modes.forEach { choice ->
                                    DropdownMenuItem({ Text("Copy ${choice.name}") }, { template = "copy:${choice.id}"; observations = choice.showObservations; templatesOpen = false })
                                }
                            }
                        }
                    }
                    SettingToggle("Show weekly observations", observations) { observations = it }
                    PulseButton(if (editId == null) "Create mode" else "Save mode", enabled = PulseModes.cleanName(name).isNotEmpty()) {
                        val save = {
                            vm.saveMode(editId, name, icon, ModePreset.entries.firstOrNull { it.name == template },
                                template.takeIf { it.startsWith("copy:") }?.removePrefix("copy:"), observations)
                        }
                        if (editId == null) close { save(); dismiss() } else { save(); page = "manage" }
                    }
                }
            } }
        }
    }
    state.modes.firstOrNull { it.id == actionId }?.takeIf { confirm != null }?.let { mode ->
        val removing = confirm == "remove"
        AlertDialog(onDismissRequest = { confirm = null },
            title = { Text(if (removing) "Remove ${mode.name}?" else "Reset ${mode.name}?") },
            text = { Text(if (removing) "You can restore this dashboard from Manage modes." else "Replace this dashboard with ${mode.preset?.let { "the ${it.title} preset" } ?: "an empty layout"}. You can undo the reset in Studio.") },
            confirmButton = { TextButton({ if (removing) vm.removeMode(mode.id) else vm.resetLayout(mode.id); confirm = null }) {
                Text(if (removing) "Remove" else "Reset", color = Red)
            } }, dismissButton = { TextButton({ confirm = null }) { Text("Cancel") } })
    }
}
