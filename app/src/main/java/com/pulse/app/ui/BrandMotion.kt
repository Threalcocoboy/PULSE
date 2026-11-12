package com.pulse.app.ui
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.*
import kotlinx.coroutines.delay
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
fun BrandLogo(modifier: Modifier = Modifier, animate: Boolean = false, finished: () -> Unit = {}) {
    val motion = animate && LocalAnimations.current
    val progress = remember { Animatable(if (motion) 0f else 1f) }
    val done by rememberUpdatedState(finished)
    LaunchedEffect(motion) {
        if (motion) progress.animateTo(1f, tween(650, easing = LinearEasing))
        else progress.snapTo(1f)
        done()
    }
    val ink = MaterialTheme.colorScheme.onBackground
    Canvas(modifier.clipToBounds().semantics { contentDescription = "PULSE logo" }) {
        val cell = LogoGeometry.cell(size.width, size.height)
        val origin = Offset((size.width - LogoGeometry.width * cell) / 2, (size.height - LogoGeometry.height * cell) / 2)
        val radius = cell * LogoGeometry.dotRadius
        fun position(point: Offset) = origin + point * cell

        LogoGeometry.letters.forEachIndexed { letter, rows ->
            rows.forEachIndexed { row, line ->
                line.forEachIndexed { column, dot ->
                    if (dot == '1') drawCircle(ink, radius, position(LogoGeometry.letterDot(letter, row, column)))
                }
            }
        }

        val reveal = progress.value * LogoGeometry.width
        LogoGeometry.baseline.forEach { dot ->
            if (dot.x <= reveal) drawCircle(Red, radius, position(dot))
        }
        if (reveal > LogoGeometry.anchor.x) {
            val path = Path().apply {
                val start = position(LogoGeometry.anchor)
                moveTo(start.x, start.y)
                LogoGeometry.wave.drop(2).forEach { point ->
                    val end = position(point)
                    lineTo(end.x, end.y)
                }
            }
            clipRect(right = origin.x + reveal * cell) {
                drawPath(path, Red, style = Stroke(radius * 2, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

@Composable
fun BrandHeader(modifier: Modifier = Modifier) {
    var played by rememberSaveable { mutableStateOf(false) }
    val cold = LocalColdLaunch.current
    val animate = remember { cold && !played }
    LaunchedEffect(Unit) { played = true }
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        val height = 32.dp
        BrandLogo(Modifier.width(height * (LogoGeometry.width / LogoGeometry.height)).height(height), animate)
    }
}

@Composable
fun FirstLaunchIntro(animate: Boolean, finish: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BrandLogo(Modifier.fillMaxWidth().height(180.dp).padding(24.dp), animate, finish)
    }
}
@Composable fun Modifier.reveal(index:Int=0):Modifier {val motion=LocalAnimations.current&&index<8;val p=remember{Animatable(if(motion)0f else 1f)};LaunchedEffect(Unit){if(motion){delay((index.coerceAtMost(5)*35).toLong());p.animateTo(1f,tween(220))}};return graphicsLayer{alpha=p.value;translationY=(1-p.value)*8.dp.toPx()}}
