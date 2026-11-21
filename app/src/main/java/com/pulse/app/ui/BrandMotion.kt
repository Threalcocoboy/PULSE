package com.pulse.app.ui
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
val LocalColdLaunch=staticCompositionLocalOf{false}
object LogoGeometry {
    const val width = 45f
    const val height = 10f
    const val dotRadius = .34f
    val letters = listOf(
        listOf("11110", "10001", "10001", "11110", "10000", "10000", "10000"),
        listOf("10001", "10001", "10001", "10001", "10001", "10001", "01110"),
        listOf("10000", "10000", "10000", "10000", "10000", "10000", "11111"),
        listOf("01111", "10000", "10000", "01110", "00001", "00001", "11110"),
        listOf("11111", "10000", "10000", "11110", "10000", "10000", "11111")
    )

    fun letterDot(letter: Int, row: Int, column: Int) = Offset(letter * 6 + column + .5f, row + 2.5f)

    val anchor = letterDot(letters.lastIndex, 3, letters.last()[3].lastIndexOf('1'))
    val baseline = (0..anchor.x.toInt()).map { Offset(it + .5f, anchor.y) }
    val wave = listOf(
        baseline.first(), anchor, Offset(32.5f, anchor.y), Offset(34f, 4.5f),
        Offset(35.5f, 6.8f), Offset(37.5f, 1.5f), Offset(39.5f, 8.5f),
        Offset(41f, anchor.y), Offset(44.5f, anchor.y)
    )

    fun cell(canvasWidth: Float, canvasHeight: Float) = minOf(canvasWidth / width, canvasHeight / height)
}

@Composable
fun BrandLogo(
    modifier: Modifier = Modifier,
    animate: Boolean = false,
    loading: Boolean = false,
    pulled: (() -> Float)? = null,
    description: String = "PULSE logo",
    finished: () -> Unit = {}
) {
    val motion = animate && LocalAnimations.current
    val progress = remember { Animatable(if (motion) 0f else 1f) }
    val done by rememberUpdatedState(finished)
    LaunchedEffect(motion) {
        if (motion) progress.animateTo(1f, tween(650, easing = LinearEasing))
        else progress.snapTo(1f)
        done()
    }
    val phase = remember { Animatable(0f) }
    val animateLoading = loading && LocalAnimations.current
    if (animateLoading) {
        val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
        LaunchedEffect(lifecycle) {
            if (lifecycle.isAtLeast(Lifecycle.State.STARTED)) {
                while (true) {
                    phase.snapTo(0f)
                    phase.animateTo(1f, tween(1150, easing = LinearEasing))
                }
            }
        }
    }
    val ink = MaterialTheme.colorScheme.onBackground
    val motionEnabled = LocalAnimations.current
    Box(modifier.clipToBounds().semantics {
        contentDescription = description
        if (loading) stateDescription = "Loading"
    }.drawWithCache {
        val cell = LogoGeometry.cell(size.width, size.height)
        val origin = Offset((size.width - LogoGeometry.width * cell) / 2, (size.height - LogoGeometry.height * cell) / 2)
        val radius = cell * LogoGeometry.dotRadius
        fun position(point: Offset) = origin + point * cell

        val letters = buildList {
            LogoGeometry.letters.forEachIndexed { letter, rows ->
                rows.forEachIndexed { row, line ->
                    line.forEachIndexed { column, dot ->
                        if (dot == '1') add(position(LogoGeometry.letterDot(letter, row, column)))
                    }
                }
            }
        }
        val baseline = LogoGeometry.baseline.map(::position)
        val path = Path().apply {
            val start = position(LogoGeometry.anchor)
            moveTo(start.x, start.y)
            LogoGeometry.wave.drop(2).forEach { point ->
                val end = position(point)
                lineTo(end.x, end.y)
            }
        }
        val stroke = Stroke(radius * 2, cap = StrokeCap.Round, join = StrokeJoin.Round)
        onDrawBehind {
            letters.forEach { drawCircle(ink, radius, it) }
            val width = LogoGeometry.width * cell
            val pull = pulled?.invoke()?.coerceIn(0f, 1f) ?: 0f
            val reveal = origin.x + progress.value * width
            baseline.forEach { if (it.x <= reveal) drawCircle(Red, radius, it) }
            clipRect(right = reveal) { drawPath(path, Red, style = stroke) }
            if (cell > 0f && (animateLoading || (motionEnabled && pull > 0f))) {
                val span = width * .28f
                val center = origin.x + (if (animateLoading) phase.value else pull) * (width + 2 * span) - span
                val glint = Color(0xFFFF8E9F)
                val brush = Brush.horizontalGradient(
                    listOf(Red, glint, Red), startX = center - span, endX = center + span
                )
                clipRect(right = reveal) {
                    baseline.forEach { drawCircle(brush, radius, it) }
                    drawPath(path, brush, style = stroke)
                }
            }
        }
    })
}

@Composable
fun BrandHeader(modifier: Modifier = Modifier, loading: Boolean = false, pulled: (() -> Float)? = null) {
    var played by rememberSaveable { mutableStateOf(false) }
    val cold = LocalColdLaunch.current
    val animate = remember { cold && !played }
    LaunchedEffect(Unit) { played = true }
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        val height = 32.dp
        BrandLogo(Modifier.width(height * (LogoGeometry.width / LogoGeometry.height)).height(height), animate, loading, pulled)
    }
}

@Composable
fun FirstLaunchIntro(animate: Boolean, finish: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BrandLogo(Modifier.fillMaxWidth().height(180.dp).padding(24.dp), animate, finished = finish)
    }
}
