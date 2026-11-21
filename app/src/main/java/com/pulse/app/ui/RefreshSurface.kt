package com.pulse.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshSurface(
    loading: Boolean, refresh: () -> Unit, enabled: Boolean = true, showIndicator: Boolean = true,
    content: @Composable (pull: () -> Float) -> Unit
) {
    val state = rememberPullToRefreshState()
    val motion = LocalAnimations.current
    Box(Modifier.fillMaxSize().pullToRefresh(isRefreshing = loading, state = state, enabled = enabled, onRefresh = refresh)) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            if (motion && enabled && !loading) translationY = 30.dp.toPx() * state.distanceFraction.coerceIn(0f, 1.3f)
        }) { content { state.distanceFraction } }
        val pulled by remember(state) { derivedStateOf { state.distanceFraction > .025f } }
        if (enabled && showIndicator && (loading || pulled)) {
            Surface(Modifier.align(Alignment.TopCenter).graphicsLayer {
                if (!loading && motion) {
                    val distance = state.distanceFraction.coerceIn(0f, 1f)
                    alpha = distance
                    translationY = (distance - 1f) * 12.dp.toPx()
                }
            }, color = MaterialTheme.colorScheme.background) {
                SyncLogo(Modifier.width(180.dp), running = loading, pulled = { state.distanceFraction })
            }
        }
    }
}
