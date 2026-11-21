package com.pulse.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.pulse.app.core.HealthSnapshot
import kotlinx.coroutines.delay

@Composable
internal fun SyncLogo(modifier: Modifier = Modifier, running: Boolean = true, pulled: (() -> Float)? = null) {
    BrandLogo(
        modifier = modifier.height(34.dp),
        loading = running,
        pulled = pulled,
        description = if (running) "Synchronizing health data" else "Pull to refresh"
    )
}

@Composable
fun SuccessMark() {
    val motion = LocalAnimations.current
    val progress = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(motion) {
        if (motion) progress.animateTo(1f, PulseMotion.tween(true, PulseMotion.Card)) else progress.snapTo(1f)
    }
    Canvas(Modifier.size(38.dp).semantics { contentDescription = "Complete" }) {
        val start = Offset(size.width * .18f, size.height * .53f)
        val joint = Offset(size.width * .41f, size.height * .76f)
        val end = Offset(size.width * .88f, size.height * .24f)
        val value = progress.value
        drawLine(Red, start, start + (joint - start) * (value / .38f).coerceAtMost(1f), 3.dp.toPx(), StrokeCap.Round)
        if (value > .38f) drawLine(Red, joint, joint + (end - joint) * ((value - .38f) / .62f), 3.dp.toPx(), StrokeCap.Round)
    }
}

@Composable fun PulseIntro(modifier: Modifier = Modifier) { BrandLogo(modifier, animate = true) }

private enum class SyncVisualState { IDLE, SYNCING, SUCCESS, ERROR }

@Composable
fun SyncFeedback(snapshot: HealthSnapshot) {
    var synced by remember(snapshot.mode) { mutableStateOf(snapshot.synced) }
    var completed by remember(snapshot.mode) { mutableStateOf(false) }
    LaunchedEffect(snapshot.synced, snapshot.loading, snapshot.error) {
        val confirmed = !snapshot.loading && snapshot.error == null && snapshot.synced != null && snapshot.synced != synced
        if (snapshot.loading || snapshot.error != null) completed = false
        if (confirmed) {
            synced = snapshot.synced
            completed = true
            delay(1750)
        }
        completed = false
    }
    val state = when {
        snapshot.loading -> SyncVisualState.SYNCING
        snapshot.error != null -> SyncVisualState.ERROR
        completed -> SyncVisualState.SUCCESS
        else -> SyncVisualState.IDLE
    }
    PulseSwap(state, Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, label = "Synchronization", distance = 6) {
        when (it) {
            SyncVisualState.SYNCING -> Unit
            SyncVisualState.ERROR -> Text(snapshot.error.orEmpty(), color = Red, style = MaterialTheme.typography.bodySmall)
            SyncVisualState.SUCCESS -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Glyph("check", Modifier.reveal(scale = .65f).size(14.dp), Red)
                Text("Updated", color = Red, style = MaterialTheme.typography.labelSmall)
            }
            SyncVisualState.IDLE -> Unit
        }
    }
}
