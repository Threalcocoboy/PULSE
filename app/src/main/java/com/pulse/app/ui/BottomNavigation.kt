package com.pulse.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun PulseBottomNavigation(current: String, navigate: (String) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        // 3 equal-weighted columns guarantee the middle item ("home") is mathematically centered at exactly W / 2
        val items = listOf(
            Triple("history", "History", "history"),
            Triple("home", "Home", "home"),
            Triple("achievements", "Collection", "medal")
        )

        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(64.dp)
        ) {
            items.forEach { (route, label, icon) ->
                val selected = current == route
                val emphasis by animateFloatAsState(if(selected) 1.10f else 1f,tween(if(LocalAnimations.current) 180 else 0),label="Navigation emphasis")
                val color = if (selected) Red else MaterialTheme.colorScheme.onSurfaceVariant

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { navigate(route) }
                        .semantics {
                            this.selected = selected
                            role = Role.Tab
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Glyph(
                            name = icon,
                            modifier = Modifier.size(PulseTokens.iconSize).graphicsLayer {scaleX=emphasis;scaleY=emphasis;translationY=if(selected) -1.dp.toPx() else 0f},
                            color = color
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = color,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(3.dp))
                        Box(
                            Modifier
                                .size(3.dp)
                                .background(
                                    if (selected) Red else androidx.compose.ui.graphics.Color.Transparent,
                                    CircleShape
                                )
                        )
                    }
                }
            }
        }
    }
}
