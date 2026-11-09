package com.pulse.app.ui
import androidx.compose.foundation.shape.*
import androidx.compose.ui.graphics.Shape
import com.pulse.app.core.WidgetShape
import androidx.compose.ui.unit.dp
fun WidgetShape.outline():Shape=when(this){WidgetShape.CIRCLE->CircleShape;WidgetShape.PILL->RoundedCornerShape(50);else->RoundedCornerShape(12.dp)}
