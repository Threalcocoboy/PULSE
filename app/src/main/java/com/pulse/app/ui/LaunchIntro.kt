package com.pulse.app.ui

import android.content.ContentResolver
import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import com.pulse.app.R
import kotlinx.coroutines.delay

@Composable
fun LaunchIntro(onFinished: () -> Unit) {
    val finish by rememberUpdatedState(onFinished)
    BackHandler { finish() }
    LaunchedEffect(Unit) {
        // Let the app open even if the platform decoder never responds.
        delay(4_000)
        finish()
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
            awaitEachGesture {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            }
        },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { context ->
                VideoView(context).apply {
                    setOnCompletionListener { finish() }
                    setOnErrorListener { _, _, _ -> finish(); true }
                    setVideoURI(Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.pulse_heart_intro}"))
                    start()
                }
            },
            onReset = null,
            onRelease = { it.stopPlayback() }
        )
    }
}
