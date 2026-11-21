package com.pulse.app.ui

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal object PulseMotion {
    const val Press = 90
    const val Selection = 165
    const val Content = 225
    const val Card = 300
    const val Tab = 380
    const val Page = 350
    const val Celebration = 680
    val Arrive = CubicBezierEasing(.18f, .78f, .25f, 1f)
    val Depart = CubicBezierEasing(.35f, 0f, .75f, .45f)

    fun <T> settle(enabled: Boolean): FiniteAnimationSpec<T> =
        if (enabled) spring(dampingRatio = .82f, stiffness = 460f) else snap()

    fun <T> tween(enabled: Boolean, millis: Int = Content): FiniteAnimationSpec<T> =
        if (enabled) androidx.compose.animation.core.tween(millis, easing = Arrive) else snap()

    fun enter(enabled: Boolean, back: Boolean): EnterTransition {
        if (!enabled) return EnterTransition.None
        return if (back) fadeIn(tween(true, Card), initialAlpha = .35f) +
            scaleIn(tween(true, Page), initialScale = .975f) + slideInVertically(tween(true, Page)) { -it / 36 }
        else fadeIn(tween(true, Card), initialAlpha = .15f) + slideInVertically(tween(true, Page)) { it / 12 }
    }

    fun exit(enabled: Boolean, back: Boolean): ExitTransition {
        if (!enabled) return ExitTransition.None
        val fade = fadeOut(androidx.compose.animation.core.tween(Card, easing = Depart))
        return if (back) fade + slideOutVertically(tween(true, Page)) { it / 12 }
        else fade + scaleOut(tween(true, Page), targetScale = .975f) + slideOutVertically(tween(true, Page)) { -it / 36 }
    }
}

internal val pulseTabs = listOf("history", "home", "achievements")

@Composable
internal fun PulseTabPages(
    active: String,
    outgoing: String?,
    states: SaveableStateHolder,
    onSettled: (String, String) -> Unit,
    content: @Composable (String) -> Unit
) {
    val motion = LocalAnimations.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val from = outgoing?.takeIf { motion && it != active }
    val direction = if (from == null) 0 else pulseTabs.indexOf(active).compareTo(pulseTabs.indexOf(from)) * if (rtl) -1 else 1
    val latestOnSettled by rememberUpdatedState(onSettled)
    LaunchedEffect(outgoing, motion) {
        if (!motion && outgoing != null) latestOnSettled(outgoing, active)
    }

    Box(Modifier.fillMaxSize().padding(bottom = PulseNavHeight).clipToBounds()) {
        listOfNotNull(from, active).forEach { route ->
            key(route) {
                val foreground = route == active
                val offset = remember { Animatable(if (foreground) .19f * direction else 0f) }
                val opacity = remember { Animatable(if (foreground && from != null) .72f else 1f) }
                val scale = remember { Animatable(1f) }
                LaunchedEffect(foreground, from, direction, motion) {
                    if (from == null) {
                        offset.snapTo(0f)
                        opacity.snapTo(1f)
                        scale.snapTo(1f)
                    } else coroutineScope {
                        launch { offset.animateTo(if (foreground) 0f else -.14f * direction, PulseMotion.tween(true, PulseMotion.Tab)) }
                        launch { opacity.animateTo(if (foreground) 1f else .24f, PulseMotion.tween(true, PulseMotion.Card)) }
                        launch { scale.animateTo(if (foreground) 1f else .985f, PulseMotion.tween(true, PulseMotion.Tab)) }
                    }
                    if (!foreground) latestOnSettled(route, active)
                }
                // The layer keyed to the previously selected tab keeps its current transform on interruption.
                // Older layers leave composition immediately, and the holder saves their scroll and filter state.
                val input = if (foreground) Modifier else Modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
                Box(Modifier.fillMaxSize().testTag("pulse-tab-page").graphicsLayer {
                    translationX = if (motion) offset.value * size.width else 0f
                    alpha = if (motion) opacity.value else 1f
                    scaleX = if (motion) scale.value else 1f
                    scaleY = scaleX
                }.background(MaterialTheme.colorScheme.background).then(input).foregroundSemantics(foreground)) {
                    states.SaveableStateProvider(route) { content(route) }
                }
            }
        }
    }
}

@Composable
internal fun rememberSystemMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun enabled() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    var motion by remember(resolver) { mutableStateOf(enabled()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { motion = enabled() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        motion = enabled()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return motion
}

@Composable
internal fun Modifier.reveal(index: Int = 0, rise: Int = 16, scale: Float = 1f): Modifier {
    if (index !in 0..7) return this
    val motion = LocalAnimations.current
    val progress = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(motion) {
        if (motion && progress.value < 1f) {
            progress.animateTo(1f, tween(PulseMotion.Card, index.coerceAtMost(5) * 36, PulseMotion.Arrive))
        } else progress.snapTo(1f)
    }
    if (!motion) return this
    return graphicsLayer {
        val remaining = 1f - progress.value
        alpha = .54f + .46f * progress.value
        translationY = remaining * rise.dp.toPx()
        scaleX = 1f - remaining * (1f - scale)
        scaleY = scaleX
    }
}

@Composable
internal fun Modifier.pulsePress(source: MutableInteractionSource, pressedScale: Float = .97f): Modifier {
    val pressed by source.collectIsPressedAsState()
    val motion = LocalAnimations.current
    val scale = animateFloatAsState(
        if (pressed && motion) pressedScale else 1f,
        if (pressed) PulseMotion.tween(motion, PulseMotion.Press) else PulseMotion.settle(motion),
        label = "Press compression"
    )
    return graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}

@Composable
internal fun Modifier.pulseClickable(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    return pulsePress(source).clickable(source, LocalIndication.current, onClick = onClick)
}

internal fun Modifier.foregroundSemantics(active: Boolean): Modifier =
    if (active) this else clearAndSetSemantics { hideFromAccessibility() }

@Composable
internal fun <T> PulseSwap(
    value: T,
    modifier: Modifier = Modifier,
    label: String = "Content change",
    direction: (T, T) -> Int = { _, _ -> 1 },
    distance: Int = 10,
    key: (T) -> Any? = { it },
    content: @Composable (T) -> Unit
) {
    val motion = LocalAnimations.current
    val offset = with(LocalDensity.current) { distance.dp.roundToPx() }
    AnimatedContent(value, modifier, transitionSpec = {
        if (!motion) (EnterTransition.None togetherWith ExitTransition.None).using(null)
        else {
            val sign = direction(initialState, targetState)
            (fadeIn(PulseMotion.tween(true)) + slideInVertically(PulseMotion.tween(true)) { sign * offset })
                .togetherWith(fadeOut(PulseMotion.tween(true, 125)) + slideOutVertically(PulseMotion.tween(true, 150)) { -sign * offset })
                .using(null)
        }
    }, label = label, contentKey = key) { shown ->
        androidx.compose.foundation.layout.Box(Modifier.foregroundSemantics(key(shown) == key(value))) { content(shown) }
    }
}

@Composable
internal fun AnimatedReading(text: String, reading: Double?, modifier: Modifier = Modifier, content: @Composable (String) -> Unit) {
    data class Reading(val text: String, val value: Double?)
    PulseSwap(Reading(text, reading), modifier, label = "Health reading", distance = 7, key = { it.text }, direction = { a, b ->
        when { a.value == null || b.value == null -> 0; else -> b.value.compareTo(a.value) }
    }) { content(it.text) }
}

internal val LocalSharedMotion = compositionLocalOf<SharedTransitionScope?> { null }
internal val LocalSharedPage = compositionLocalOf<AnimatedVisibilityScope?> { null }

@Composable
internal fun Modifier.pulseSharedBounds(key: String): Modifier {
    val shared = LocalSharedMotion.current
    val page = LocalSharedPage.current
    if (shared == null || page == null || !LocalAnimations.current) return this
    return with(shared) {
        sharedBounds(rememberSharedContentState(key), page,
            boundsTransform = BoundsTransform { _, _ -> PulseMotion.tween(true, 440) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PulseSheet(
    onDismiss: () -> Unit,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    content: @Composable ColumnScope.(close: (() -> Unit) -> Unit) -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val motion = LocalAnimations.current
    var closing by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = { if (!closing) onDismiss() }, sheetState = state, containerColor = containerColor) {
        content { after ->
            if (!closing) {
                closing = true
                if (!motion) after() else scope.launch {
                    try { state.hide() } finally { if (!state.isVisible) after() else closing = false }
                }
            }
        }
    }
}

@Composable
internal fun PulseVisibility(visible: Boolean, content: @Composable AnimatedVisibilityScope.() -> Unit) {
    val motion = LocalAnimations.current
    AnimatedVisibility(visible,
        enter = if (motion) fadeIn(PulseMotion.tween(true)) + expandVertically(PulseMotion.settle(true)) else EnterTransition.None,
        exit = if (motion) fadeOut(PulseMotion.tween(true, 125)) + shrinkVertically(PulseMotion.tween(true)) else ExitTransition.None,
        content = content)
}

@Composable
internal fun PulseConfirmDialog(
    onDismiss: () -> Unit, title: @Composable () -> Unit, text: @Composable () -> Unit,
    confirmLabel: String, onConfirm: () -> Unit
) {
    val motion = LocalAnimations.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(if (motion) 0f else 1f) }
    var leaving by remember { mutableStateOf(false) }
    LaunchedEffect(motion, leaving) {
        if (!leaving) {
            if (motion) progress.animateTo(1f, PulseMotion.settle(true)) else progress.snapTo(1f)
        }
    }
    fun close(action: () -> Unit) {
        if (leaving) return
        leaving = true
        if (!motion) action() else scope.launch {
            progress.animateTo(0f, PulseMotion.tween(true, PulseMotion.Selection))
            action()
        }
    }
    AlertDialog(onDismissRequest = { close(onDismiss) }, title = title, text = text,
        modifier = Modifier.graphicsLayer {
            alpha = progress.value.coerceIn(0f, 1f)
            translationY = (1f - progress.value) * 12.dp.toPx()
            scaleX = .94f + .06f * progress.value
            scaleY = scaleX
        },
        confirmButton = { TextButton({ close(onConfirm) }, enabled = !leaving) { Text(confirmLabel, color = Red) } },
        dismissButton = { TextButton({ close(onDismiss) }, enabled = !leaving) { Text("Cancel") } })
}
