package com.pulse.app.ui

import androidx.compose.animation.*
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.*

@Composable fun StudioGrid(
    layout: List<Widget>, editing: Boolean, selected: String?, scroll: ScrollState,
    viewportTop: Float, viewportHeight: Float, select: (String)->Unit, beginEdit: ()->Unit,
    commit: (List<Widget>)->Unit, configure: (String)->Unit, openMetric: (Metric)->Unit, interactive: Boolean = true,
    haptic: () -> Unit = {},
    content: @Composable (Widget)->Unit
) {
    val editRef = rememberUpdatedState(editing)
    val selectedRef = rememberUpdatedState(selected)
    // Pointer handlers may outlive a BoxWithConstraints subcomposition. Read current inputs
    // at gesture start, then use one immutable baseline through preview and commit.
    val layoutRef = rememberUpdatedState(layout)
    val configureRef = rememberUpdatedState(configure)
    val commitRef = rememberUpdatedState(commit)
    val selectRef = rememberUpdatedState(select)
    val beginEditRef = rememberUpdatedState(beginEdit)
    val openMetricRef = rememberUpdatedState(openMetric)
    val hapticRef = rememberUpdatedState(haptic)
    val motion = LocalAnimations.current
    var gestureLayout by remember { mutableStateOf<List<Widget>>(emptyList()) }
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
        val gridWidth = maxWidth
        val density = LocalDensity.current
        val gap = PulseTokens.gridGap
        val cell = (maxWidth - gap * 3) / 4
        val rowCell = cell // Square grid cells match circle/square artwork at every font scale.
        val stepX = with(density) { (cell + gap).toPx() }
        val stepY = with(density) { (rowCell + gap).toPx() }
        val gapPx = with(density) { gap.toPx() }
        val target = with(density) { PulseTokens.touchTarget.toPx() }

        fun update(d: Offset) {
            delta += d
            val baseline = gestureLayout
            val original = baseline.firstOrNull { it.id == active } ?: return
            if (kind == StudioGesture.MOVE) {
                val column = (original.column + delta.x / stepX).roundToInt().coerceIn(0, AdaptiveFlow.COLUMNS - original.width)
                val row = (original.row + delta.y / stepY).roundToInt().coerceAtLeast(0)
                preview = if (column == original.column && row == original.row) baseline else AdaptiveFlow.pack(baseline, original.id, column, row)
            } else {
                val live = StudioGestureRules.calculateLive(
                    original, delta.x, delta.y, stepX, stepY, gapPx, lastSize
                )
                lastSize = live.targetFootprint
                preview = if (lastSize == Footprint(original.width, original.height)) baseline else AdaptiveFlow.pack(
                    baseline.map { if (it.id == original.id) it.copy(width = lastSize.columns, height = lastSize.rows) else it },
                    original.id, original.column, original.row
                )
            }
        }

        val updateRef = rememberUpdatedState(::update)
        LaunchedEffect(active) {
            var previous=0L
            while(active!=null) {
                val now=withFrameNanos{it};val dt=if(previous==0L)0f else ((now-previous)/1e9f).coerceAtMost(.04f);previous=now
                if(kind==StudioGesture.MOVE){val edge=with(density){64.dp.toPx()};val velocity=when{pointerY<viewportTop+edge->-((viewportTop+edge-pointerY)/edge).coerceIn(0f,1f)*650f;pointerY>viewportTop+viewportHeight-edge->((pointerY-viewportTop-viewportHeight+edge)/edge).coerceIn(0f,1f)*650f;else->0f};val consumed=scroll.scrollBy(velocity*dt);if(consumed!=0f)updateRef.value(Offset(0f,consumed))}
            }
        }
        val shown = preview ?: layout
        val rows = shown.maxOfOrNull { it.row + it.height } ?: 0
        // Keep removed widgets mounted for their exit. The live layout remains the only input target.
        var previous by remember { mutableStateOf(layout) }
        val shownById = remember(shown) { shown.associateBy { it.id } }
        val retained = if (motion && interactive) shown + previous.filter { it.id !in shownById } else shown
        LaunchedEffect(layout, motion, interactive) {
            if (motion && interactive && previous.any { old -> layout.none { it.id == old.id } }) delay(230)
            previous = layout
        }
        val height by animateDpAsState((rowCell + gap) * rows, PulseMotion.settle(motion && active == null), label = "Grid height")
        val entrance = animateFloatAsState(if (editing) .985f else 1f, PulseMotion.settle(motion), label = "Studio grid emphasis")

        Box(Modifier.fillMaxWidth().height(if (active == null) height else (rowCell + gap) * rows)) {
            // Render potential grid destination ghost if actively resizing
            if (active != null && kind == StudioGesture.RESIZE) {
                val targetW = preview?.firstOrNull { it.id == active }
                if (targetW != null) {
                    val isCircle = targetW.shape in setOf(WidgetShape.CIRCLE, WidgetShape.SQUARE)
                    val isPill = targetW.shape == WidgetShape.PILL
                    val ghostW = (cell + gap) * targetW.width - gap
                    val ghostH = if (isCircle) ghostW else ((rowCell + gap) * targetW.height - gap)
                    val ghostShape = targetW.shape.outline()
                    Box(
                        Modifier
                            .offset((cell + gap) * targetW.column, (rowCell + gap) * targetW.row)
                            .size(ghostW, ghostH)
                            .border(1.5.dp, Red.copy(alpha = 0.6f), ghostShape)
                    )
                }
            }

            retained.forEachIndexed { index, w ->
                key(w.id) {
                    val available = w.id in shownById
                    val visibility = remember { MutableTransitionState(!motion || previous.any { it.id == w.id }) }
                    visibility.targetState = available
                    val original = (if(active==w.id) gestureLayout else layout).firstOrNull { it.id == w.id } ?: w
                    val isCurrentActive = active == w.id
                    val moving = isCurrentActive && kind == StudioGesture.MOVE
                    val resizing = isCurrentActive && kind == StudioGesture.RESIZE
                    val chosen = editing && selected == w.id

                    val isCircle = w.shape == WidgetShape.CIRCLE
                    val isPill = w.shape == WidgetShape.PILL
                    val widgetShape = w.shape.outline()

                    val liveDims = if (resizing) {
                        StudioGestureRules.calculateLive(original, delta.x, delta.y, stepX, stepY, gapPx, lastSize)
                    } else null

                    // Live coordinates
                    val targetX = if (moving) {
                        with(density) { (original.column * stepX + delta.x).coerceIn(0f, (4 - original.width) * stepX).toDp() }
                    } else if (resizing) {
                        minOf((cell + gap) * original.column, gridWidth - with(density) { liveDims!!.widthPx.toDp() })
                    } else (cell + gap) * w.column

                    val targetY = if (moving) {
                        with(density) { (original.row * stepY + delta.y).coerceAtLeast(0f).toDp() }
                    } else if (resizing) {
                        (rowCell + gap) * original.row
                    } else (rowCell + gap) * w.row

                    val targetWidth = if (liveDims != null) {
                        with(density) { liveDims.widthPx.toDp() }
                    } else {
                        (cell + gap) * w.width - gap
                    }

                    val square = w.shape == WidgetShape.CIRCLE || w.shape == WidgetShape.SQUARE
                    val targetHeight = if (square) {
                        targetWidth
                    } else if (liveDims != null) {
                        with(density) { liveDims.heightPx.toDp() }
                    } else {
                        (rowCell + gap) * w.height - gap
                    }
                    val ax = animateDpAsState(targetX, PulseMotion.settle(motion && !isCurrentActive), label = "Widget column")
                    val ay = animateDpAsState(targetY, PulseMotion.settle(motion && !isCurrentActive), label = "Widget row")
                    val animatedWidth by animateDpAsState(targetWidth, PulseMotion.settle(motion && !resizing), label = "Widget width")
                    val animatedHeight by animateDpAsState(targetHeight, PulseMotion.settle(motion && !resizing), label = "Widget height")
                    val currentWidth = if (resizing) targetWidth else animatedWidth
                    val currentHeight = if (square) currentWidth else if (resizing) targetHeight else animatedHeight
                    val raised = animateFloatAsState(if (moving && motion) 1.055f else 1f, PulseMotion.settle(motion), label = "Lifted widget")
                    val selection = animateFloatAsState(if (chosen) 1f else 0f, PulseMotion.tween(motion, PulseMotion.Selection), label = "Widget selection")

                    var rootY by remember { mutableFloatStateOf(0f) }

                    if (moving) {
                        // Ghost placement indicator
                        val moveW = (cell + gap) * w.width - gap
                        val moveH = if (w.shape in setOf(WidgetShape.CIRCLE, WidgetShape.SQUARE)) moveW else ((rowCell + gap) * w.height - gap)
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
                            val currentWidget = layoutRef.value.firstOrNull { it.id == w.id } ?: return@awaitEachGesture
                            val resize = StudioGestureRules.resizeHit(
                                editRef.value, selectedRef.value == w.id,
                                down.position.x, down.position.y,
                                size.width.toFloat(), size.height.toFloat(), target, currentWidget.shape
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
                                        if (up != null && !up.pressed && !up.isConsumed) configureRef.value(w.id)
                                        return@awaitEachGesture
                                    }
                                } else {
                                    val held = awaitLongPressOrCancellation(down.id)
                                    if (held == null) {
                                        val up = currentEvent.changes.firstOrNull { it.id == down.id }
                                        if (up != null && !up.pressed && !up.isConsumed) openMetricRef.value(currentWidget.metric)
                                        return@awaitEachGesture
                                    }
                                    held.consume()
                                    beginEditRef.value()
                                    selectRef.value(w.id)
                                    // A stationary hold only enters edit mode. A later tap configures.
                                    val slop = awaitTouchSlopOrCancellation(down.id) { change, over ->
                                        first = over
                                        change.consume()
                                    }
                                    if (slop == null) {
                                        return@awaitEachGesture
                                    }
                                }

                                selectRef.value(w.id)
                                gestureLayout = layoutRef.value
                                val baselineWidget = gestureLayout.firstOrNull { it.id == w.id } ?: return@awaitEachGesture
                                active = w.id
                                kind = if (resize) StudioGesture.RESIZE else StudioGesture.MOVE
                                delta = Offset.Zero
                                lastSize = Footprint(baselineWidget.width, baselineWidget.height)
                                preview = gestureLayout
                                pointerY = rootY + down.position.y
                                activated = true

                                if (first != Offset.Zero) updateRef.value(first)
                                finished = drag(down.id) { change ->
                                    pointerY = rootY + change.position.y
                                    updateRef.value(change.positionChange())
                                    change.consume()
                                }

                                if (finished) {
                                    preview?.takeIf { it != gestureLayout }?.let { change ->
                                        commitRef.value(change)
                                        hapticRef.value()
                                    }
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

                    AnimatedVisibility(visibility,
                        modifier = Modifier.offset {
                            val x = if (isCurrentActive) targetX else ax.value
                            val y = if (isCurrentActive) targetY else ay.value
                            IntOffset(x.roundToPx(), y.roundToPx())
                        }.zIndex(if (isCurrentActive) 5f else if (chosen) 2f else 0f),
                        enter = if (motion) fadeIn(PulseMotion.tween(true)) + scaleIn(PulseMotion.settle(true), initialScale = .91f) else EnterTransition.None,
                        exit = if (motion) fadeOut(PulseMotion.tween(true, 190)) + scaleOut(PulseMotion.tween(true, 200), targetScale = .91f) else ExitTransition.None
                    ) { Box(
                        Modifier.reveal(index + 3, rise = 16, scale = .97f)
                            .size(currentWidth, currentHeight)
                            .onGloballyPositioned { rootY = it.positionInRoot().y }
                            .graphicsLayer {
                                scaleX = raised.value * entrance.value
                                scaleY = scaleX
                                shadowElevation = (selection.value * 4.dp.toPx()) + (raised.value - 1f) * 100.dp.toPx()
                                shape = widgetShape
                                clip = true
                            }
                            .then(if (interactive && available) input else Modifier.clearAndSetSemantics { })
                            .semantics {
                                if (!interactive || !available) return@semantics
                                contentDescription = "${w.metric.label} widget"
                                onClick(if (editing) "Configure widget" else "Open ${w.metric.label} details") {
                                    if (editRef.value) configure(w.id) else openMetric(w.metric)
                                    true
                                }
                                onLongClick("Edit widget") {
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
                        CompositionLocalProvider(LocalWidgetEditing provides editing) {content(widgetToDisplay)}

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
                                    .offset(
                                        x = -with(density) { StudioGestureRules.handleInset(w.shape, currentWidth.toPx(), target).toDp() },
                                        y = -with(density) { StudioGestureRules.handleInset(w.shape, currentWidth.toPx(), target).toDp() }
                                    )
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
                    } }
                }
            }
        }
    }
}
