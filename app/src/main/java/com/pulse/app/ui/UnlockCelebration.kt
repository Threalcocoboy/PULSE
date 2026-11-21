package com.pulse.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.pulse.app.core.*
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun UnlockCelebration(items: List<UserAchievement>, acknowledge: (List<String>) -> Unit) {
    var batch by remember { mutableStateOf(emptyList<UserAchievement>()) }
    var acknowledged by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(items, batch) {
        if (batch.isEmpty()) batch = items.distinctBy { it.instanceId }.filterNot { it.instanceId in acknowledged }
        val pending = items.map { it.instanceId }.toSet()
        acknowledged = acknowledged.intersect(pending)
    }
    if (batch.isEmpty()) return

    val motion = LocalAnimations.current
    val scope = rememberCoroutineScope()
    val entrance = remember(batch) { Animatable(if (motion) 0f else 1f) }
    val artwork = remember(batch) { Animatable(if (motion) .72f else 1f) }
    var closing by remember(batch) { mutableStateOf(false) }
    LaunchedEffect(batch, motion) {
        if (motion) launch { artwork.animateTo(1f, spring(dampingRatio = .64f, stiffness = 330f)) }
        else artwork.snapTo(1f)
        if (motion) entrance.animateTo(1f, PulseMotion.tween(true, PulseMotion.Celebration))
        else entrance.snapTo(1f)
    }
    fun finish() {
        val ids = batch.map { it.instanceId }
        acknowledged += ids
        acknowledge(ids)
        batch = emptyList()
    }

    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false,
        dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize().graphicsLayer {
            alpha = (entrance.value * 1.5f).coerceIn(0f, 1f)
            translationY = (1f - entrance.value) * 20.dp.toPx()
        }, color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.reveal()) { DotWordmark() }
                Text(if (batch.size == 1) "Achievement unlocked" else "Achievements unlocked",
                    Modifier.reveal(1), style = MaterialTheme.typography.headlineLarge)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    batch.forEachIndexed { index, item ->
                        key(item.instanceId) {
                            Row(Modifier.reveal(index + 2), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(if (batch.size == 1) 130.dp else 72.dp), contentAlignment = Alignment.Center) {
                                    if (index == 0 && motion) Canvas(Modifier.matchParentSize()) {
                                        val phase = entrance.value
                                        repeat(24) { dot ->
                                            val angle = dot * PI / 12
                                            val unit = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                                            val spread = size.minDimension * (.22f + phase * (.24f + dot % 3 * .06f))
                                            drawCircle(Red.copy(alpha = (1f - phase) * .75f), (2 - dot % 2).dp.toPx(), center + unit * spread)
                                        }
                                    }
                                    PulseBadgeArt(item, Modifier.fillMaxSize().graphicsLayer {
                                        scaleX = artwork.value
                                        scaleY = scaleX
                                    })
                                }
                                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                                    Text(item.definition.name, style = MaterialTheme.typography.titleLarge)
                                    Text(item.definition.description, style = MaterialTheme.typography.bodyMedium)
                                    item.unlockedDate?.let { Label("Unlocked $it") }
                                    if (item.period != null) Label(item.definition.name, color = Red)
                                }
                            }
                        }
                    }
                }
                PulseButton("Continue", Modifier.fillMaxWidth(), enabled = !closing) {
                    if (!motion) finish() else {
                        closing = true
                        scope.launch {
                            entrance.animateTo(0f, PulseMotion.tween(true, 180))
                            finish()
                        }
                    }
                }
            }
        }
    }
}
