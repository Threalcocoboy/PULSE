package com.pulse.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

internal val PulseNavHeight = 64.dp

@Composable
fun PulseBottomNavigation(current: String, navigate: (String) -> Unit) {
    var lastTab by remember { mutableStateOf(current.takeIf { it in pulseTabs } ?: "home") }
    SideEffect { if (current in pulseTabs) lastTab = current }
    val active = current.takeIf { it in pulseTabs } ?: lastTab
    val motion = LocalAnimations.current
    val position = animateFloatAsState(pulseTabs.indexOf(active).toFloat(), PulseMotion.settle(motion), label = "Bottom navigation indicator")
    val width = animateDpAsState(if (active == "home") 14.dp else 10.dp, PulseMotion.settle(motion), label = "Indicator width")

    Surface(
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .4f))
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(PulseNavHeight).selectableGroup()) {
            val slotWidth = maxWidth / pulseTabs.size
            Box(
                Modifier.align(Alignment.BottomStart)
                    .offset {
                        IntOffset((slotWidth.toPx() * (position.value + .5f) - width.value.toPx() / 2).roundToInt(), -5.dp.roundToPx())
                    }
                    .size(width.value, 3.dp)
                    .testTag("pulse-tab-indicator")
                    .background(Red, CircleShape)
            )
            Row(Modifier.fillMaxSize()) {
                pulseTabs.forEach { route ->
                    val label = when (route) { "history" -> "History"; "home" -> "Home"; else -> "Collection" }
                    val selected = active == route
                    val phase = remember { Animatable(1f) }
                    val source = remember { MutableInteractionSource() }
                    val color by animateColorAsState(
                        if (selected) Red else MaterialTheme.colorScheme.onSurfaceVariant,
                        PulseMotion.tween(motion, PulseMotion.Selection), label = "Tab color"
                    )
                    val lift = animateDpAsState(if (selected) 2.dp else 0.dp, PulseMotion.settle(motion), label = "Tab label lift")
                    var wasSelected by remember { mutableStateOf(selected) }
                    LaunchedEffect(selected, motion) {
                        if (selected && !wasSelected && motion) {
                            wasSelected = true
                            phase.snapTo(0f)
                            phase.animateTo(1f, tween(PulseMotion.Tab, easing = PulseMotion.Arrive))
                        } else {
                            wasSelected = selected
                            phase.snapTo(1f)
                        }
                    }
                    Box(
                        Modifier.weight(1f).fillMaxHeight()
                            .selectable(selected, enabled = current in pulseTabs, role = Role.Tab, interactionSource = source) { navigate(route) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            Modifier.pulsePress(source, .92f).padding(bottom = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (route == "history") {
                                Canvas(Modifier.size(PulseTokens.iconSize)) {
                                    drawCircle(color, size.minDimension * .35f, style = Stroke(1.5.dp.toPx()))
                                    rotate(-105f * (1f - phase.value)) {
                                        drawLine(color, center, center + Offset(0f, -size.height * .24f), 1.5.dp.toPx())
                                        drawLine(color, center, center + Offset(size.width * .18f, size.height * .09f), 1.5.dp.toPx())
                                    }
                                }
                            } else {
                                Glyph(if (route == "home") "home" else "medal", Modifier.size(PulseTokens.iconSize).graphicsLayer {
                                    val wave = sin(phase.value * PI).toFloat()
                                    if (route == "home") {
                                        val bounce = sin(phase.value * PI * 2).toFloat()
                                        val scale = 1f - bounce * .12f + wave * .10f
                                        scaleX = scale
                                        scaleY = scale
                                    } else {
                                        rotationZ = wave * -12f
                                        translationY = wave * -5.dp.toPx()
                                    }
                                }, color)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                label, Modifier.graphicsLayer { translationY = -lift.value.toPx() },
                                style = MaterialTheme.typography.labelSmall, color = color,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
