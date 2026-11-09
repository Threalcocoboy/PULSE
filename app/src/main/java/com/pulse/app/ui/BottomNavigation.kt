package com.pulse.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.*
import kotlin.math.*
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
                var tapSerial by remember{mutableIntStateOf(0)}
                val selected = current == route
                val emphasis by animateFloatAsState(if(selected) 1.10f else 1f,tween(if(LocalAnimations.current) 180 else 0),label="Navigation emphasis")
                val hand=remember {Animatable(0f)}
                val motion=LocalAnimations.current
                LaunchedEffect(selected,motion,tapSerial){if(selected&&motion){hand.snapTo(-360f);hand.animateTo(0f,tween(650,easing=FastOutSlowInEasing))}else hand.snapTo(0f)}
                val color = if (selected) Red else MaterialTheme.colorScheme.onSurfaceVariant

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { tapSerial++;navigate(route) }
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
                        if(route=="history") Canvas(Modifier.size(PulseTokens.iconSize)){
                            drawCircle(color,size.minDimension*.35f,style=Stroke(1.5.dp.toPx()))
                            rotate(hand.value){drawLine(color,center,center+Offset(0f,-size.height*.24f),1.5.dp.toPx());drawLine(color,center,center+Offset(size.width*.18f,size.height*.09f),1.5.dp.toPx())}
                        } else Glyph(icon,Modifier.size(PulseTokens.iconSize).graphicsLayer{scaleX=emphasis;scaleY=emphasis;rotationZ=if(route=="achievements") hand.value*.07f else 0f;translationY=-kotlin.math.sin(hand.value/360f*kotlin.math.PI).toFloat()*3.dp.toPx()},color)
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
