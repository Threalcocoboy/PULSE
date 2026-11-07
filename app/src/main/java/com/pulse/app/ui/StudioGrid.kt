package com.pulse.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.pulse.app.core.*
import kotlinx.coroutines.isActive
import kotlin.math.*

@Composable fun StudioGrid(
    layout: List<Widget>, editing: Boolean, selected: String?, scroll: ScrollState,
    viewportTop: Float, viewportHeight: Float, select: (String)->Unit, beginEdit: ()->Unit,
    commit: (List<Widget>)->Unit, configure: (String)->Unit, openMetric: (Metric)->Unit,
    content: @Composable (Widget)->Unit
) {
    val editRef = rememberUpdatedState(editing)
    val selectedRef = rememberUpdatedState(selected)
    var active by remember { mutableStateOf<String?>(null) }
    var kind by remember { mutableStateOf(StudioGesture.MOVE) }
    var delta by remember { mutableStateOf(Offset.Zero) }
    var preview by remember { mutableStateOf<List<Widget>?>(null) }
    var lastSize by remember { mutableStateOf(Footprint(2, 2)) }
    var pointerY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(editing) {
        if (!editing) {
            active = null
            preview = null
            delta = Offset.Zero
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val gap = PulseTokens.gridGap
        val cell = (maxWidth - gap * 3) / 4
        val rowCell = maxOf(cell, 68.dp * density.fontScale.coerceAtMost(2f))
        val stepX = with(density) { (cell + gap).toPx() }
        val stepY = with(density) { (rowCell + gap).toPx() }
        val gapPx = with(density) { gap.toPx() }
        val target = with(density) { PulseTokens.touchTarget.toPx() }

        fun update(d: Offset) {
            delta += d
            val original = layout.firstOrNull { it.id == active } ?: return
            if (kind == StudioGesture.MOVE) {
                preview = AdaptiveFlow.pack(
                    layout, original.id,
                    (original.column + delta.x / stepX).roundToInt(),
                    (original.row + delta.y / stepY).roundToInt()
                )
            } else {
                val live = StudioGestureRules.calculateLive(
                    original, delta.x, delta.y, stepX, stepY, gapPx, lastSize
                )
                lastSize = live.targetFootprint
                preview = AdaptiveFlow.pack(
                    layout.map { if (it.id == original.id) it.copy(width = lastSize.columns, height = lastSize.rows) else it },
                    original.id, original.column, original.row
                )
            }
        }

        val updateRef = rememberUpdatedState(::update)
        LaunchedEffect(active) {
            while (active != null && isActive) {
                withFrameNanos { }
                val edge = with(density) { 64.dp.toPx() }
                val speed = when {
                    pointerY < viewportTop + edge -> -min(14f, (viewportTop + edge - pointerY) * .18f)
                    pointerY > viewportTop + viewportHeight - edge -> min(14f, (pointerY - viewportTop - viewportHeight + edge) * .18f)
                    else -> 0f
                }
                if (speed != 0f) {
                    val consumed = scroll.scrollBy(speed)
                    if (consumed != 0f) updateRef.value(Offset(0f, consumed))
                }
            }
        }

        val shown = preview ?: layout
        val rows = shown.maxOfOrNull { it.row + it.height } ?: 0

        Box(Modifier.fillMaxWidth().height((rowCell + gap) * rows)) {
            // Render potential grid destination ghost if actively resizing
            if (active != null && kind == StudioGesture.RESIZE) {
                val targetW = preview?.firstOrNull { it.id == active }
                if (targetW != null) {
                    val isCircle = targetW.shape == WidgetShape.CIRCLE
                    val isPill = targetW.shape == WidgetShape.PILL
                    val ghostW = (cell + gap) * targetW.width - gap
                    val ghostH = if (isCircle) ghostW else ((rowCell + gap) * targetW.height - gap)
                    val ghostShape = when {
                        isCircle -> CircleShape
                        isPill -> RoundedCornerShape(50)
                        else -> RoundedCornerShape(PulseTokens.corner)
                    }
                    Box(
                        Modifier
                            .offset((cell + gap) * targetW.column, (rowCell + gap) * targetW.row)
                            .size(ghostW, ghostH)
                            .border(1.5.dp, Red.copy(alpha = 0.6f), ghostShape)
                    )
                }
            }

            shown.forEach { w ->
                key(w.id) {
                    val original = layout.firstOrNull { it.id == w.id } ?: w
                    val isCurrentActive = active == w.id
                    val moving = isCurrentActive && kind == StudioGesture.MOVE
                    val resizing = isCurrentActive && kind == StudioGesture.RESIZE
                    val chosen = editing && selected == w.id

                    val isCircle = w.shape == WidgetShape.CIRCLE
                    val isPill = w.shape == WidgetShape.PILL
                    val widgetShape = when {
                        isCircle -> CircleShape
                        isPill -> RoundedCornerShape(50)
                        else -> RoundedCornerShape(PulseTokens.corner)
                    }

                    val duration = if (LocalAnimations.current) PulseTokens.reflowMillis else 0
                    val ax by animateDpAsState((cell + gap) * w.column, tween(duration), label = "col")
                    val ay by animateDpAsState((rowCell + gap) * w.row, tween(duration), label = "row")

                    // Live coordinates
                    val x = if (moving) {
                        with(density) { (original.column * stepX + delta.x).coerceIn(0f, (4 - original.width) * stepX).toDp() }
                    } else if (resizing) {
                        (cell + gap) * original.column
                    } else ax

                    val y = if (moving) {
                        with(density) { (original.row * stepY + delta.y).coerceAtLeast(0f).toDp() }
                    } else if (resizing) {
                        (rowCell + gap) * original.row
                    } else ay

                    // Live dimensions during resize
                    val liveDims = if (resizing) {
                        StudioGestureRules.calculateLive(original, delta.x, delta.y, stepX, stepY, gapPx, lastSize)
                    } else null

                    val currentWidth = if (resizing && liveDims != null) {
                        with(density) { liveDims.widthPx.toDp() }
                    } else {
                        (cell + gap) * w.width - gap
                    }

                    val currentHeight = if (isCircle) {
                        currentWidth // A CIRCLE MUST ALWAYS MAINTAIN 1:1 SQUARE DIMENSIONS
                    } else if (resizing && liveDims != null) {
                        with(density) { liveDims.heightPx.toDp() }
                    } else {
                        (rowCell + gap) * w.height - gap
                    }

                    var rootY by remember { mutableFloatStateOf(0f) }

                    if (moving) {
                        // Ghost placement indicator
                        val moveW = (cell + gap) * w.width - gap
                        val moveH = if (isCircle) moveW else ((rowCell + gap) * w.height - gap)
                        Box(
                            Modifier
                                .offset((cell + gap) * w.column, (rowCell + gap) * w.row)
                                .size(moveW, moveH)
                                .border(1.dp, Red.copy(alpha = .45f), widgetShape)
                        )
                    }

                    val input = Modifier.pointerInput(w.id, layout) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = true)
                            val resize = StudioGestureRules.resizeHit(
                                editRef.value, selectedRef.value == w.id,
                                down.position.x, down.position.y,
                                size.width.toFloat(), size.height.toFloat(), target
                            )
                            var activated = false
                            var finished = false
                            try {
                                var first = Offset.Zero
                                if (resize) {
                                    down.consume()
                                } else if (editRef.value) {
                                    val slop = awaitTouchSlopOrCancellation(down.id) { change, over ->
                                        first = over
                                        change.consume()
                                    }
                                    if (slop == null) {
                                        val up = currentEvent.changes.firstOrNull { it.id == down.id }
                                        if (up != null && !up.pressed && !up.isConsumed) select(w.id)
                                        return@awaitEachGesture
                                    }
                                } else {
                                    val held = awaitLongPressOrCancellation(down.id)
                                    if (held == null) {
                                        val up = currentEvent.changes.firstOrNull { it.id == down.id }
                                        if (up != null && !up.pressed && !up.isConsumed) openMetric(w.metric)
                                        return@awaitEachGesture
                                    }
                                    held.consume()
                                    beginEdit()
                                }

                                select(w.id)
                                active = w.id
                                kind = if (resize) StudioGesture.RESIZE else StudioGesture.MOVE
                                delta = Offset.Zero
                                lastSize = Footprint(original.width, original.height)
                                preview = layout
                                pointerY = rootY + down.position.y
                                activated = true

                                if (first != Offset.Zero) updateRef.value(first)
                                finished = drag(down.id) { change ->
                                    pointerY = rootY + change.position.y
                                    updateRef.value(change.positionChange())
                                    change.consume()
                                }

                                if (finished) {
                                    preview?.let(commit)
                                }
                            } finally {
                                if (activated) {
                                    active = null
                                    preview = null
                                    delta = Offset.Zero
                                }
                            }
                        }
                    }

                    Box(
                        Modifier
                            .offset(x, y)
                            .size(currentWidth, currentHeight)
                            .zIndex(if (isCurrentActive) 5f else if (chosen) 2f else 0f)
                            .onGloballyPositioned { rootY = it.positionInRoot().y }
                            .graphicsLayer {
                                shadowElevation = if (chosen || isCurrentActive) 4.dp.toPx() else 0f
                                shape = widgetShape
                                clip = true
                            }
                            .then(input)
                            .semantics {
                                contentDescription = "${w.metric.label} widget"
                                onClick(if (editing) "Select widget" else "Open ${w.metric.label} details") {
                                    if (editRef.value) select(w.id) else openMetric(w.metric)
                                    true
                                }
                                onLongClick("Edit and move widget") {
                                    beginEdit()
                                    select(w.id)
                                    true
                                }
                                if (editing) {
                                    customActions = listOf(
                                        CustomAccessibilityAction("Configure widget") {
                                            configure(w.id)
                                            true
                                        },
                                        CustomAccessibilityAction("Move earlier") {
                                            val i = layout.indexOfFirst { it.id == w.id }
                                            val next = layout.toMutableList()
                                            next.removeAt(i)
                                            next.add((i - 1).coerceAtLeast(0), original)
                                            commit(AdaptiveFlow.pack(next))
                                            true
                                        }
                                    )
                                }
                            }
                    ) {
                        // Pass live footprint into content during resize if needed
                        val widgetToDisplay = if (resizing && liveDims != null) {
                            w.copy(width = liveDims.targetFootprint.columns, height = liveDims.targetFootprint.rows)
                        } else w
                        content(widgetToDisplay)

                        if (chosen) {
                            Canvas(Modifier.matchParentSize()) {
                                if (isCircle) {
                                    val r = (size.minDimension / 2f) - 1.dp.toPx()
                                    drawCircle(
                                        color = Red.copy(alpha = .85f),
                                        radius = r.coerceAtLeast(1f),
                                        style = Stroke(
                                            1.5.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
                                        )
                                    )
                                } else if (isPill) {
                                    drawRoundRect(
                                        color = Red.copy(alpha = .85f),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
                                        style = Stroke(
                                            1.5.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx()))
                                        )
                                    )
                                } else {
                                    drawRoundRect(
                                        color = Red.copy(alpha = .85f),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(PulseTokens.corner.toPx()),
                                        style = Stroke(
                                            1.5.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx()))
                                        )
                                    )
                                }
                            }
                            Box(
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(PulseTokens.touchTarget)
                                    .semantics {
                                        contentDescription = "Resize ${w.metric.label}"
                                        onClick("Set exact size") {
                                            configure(w.id)
                                            true
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    Modifier
                                        .size(30.dp)
                                        .background(MaterialTheme.colorScheme.onSurface, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Glyph("resize", Modifier.size(17.dp), MaterialTheme.colorScheme.surface)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
