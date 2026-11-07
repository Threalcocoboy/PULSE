package com.pulse.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import kotlin.math.*

@Composable fun SleepStageDots(stages: Map<String,Double>,modifier: Modifier=Modifier) {
    val text=MaterialTheme.colorScheme.onSurface
    val total=stages.values.sum().coerceAtLeast(.001)
    Canvas(modifier.semantics {contentDescription="Stage totals, not a chronological timeline. "+stages.entries.joinToString{"${it.key}: ${(it.value*60).roundToInt()} minutes"}}) {
        val columns=(size.width/8).toInt().coerceAtLeast(1);var offset=0
        stages.entries.forEachIndexed {index,entry->
            val n=(columns*entry.value/total).roundToInt().coerceAtMost(columns-offset)
            repeat(n){i->repeat(3){r->drawCircle(if(index==0)Red else text.copy(alpha=(.8f-index*.17f).coerceAtLeast(.25f)),1.8f,Offset((offset+i+.5f)*size.width/columns,size.height/2+(r-1)*7f))}}
            offset+=n
        }
    }
}
