package com.pulse.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable fun PulseBottomNavigation(current: String,navigate:(String)->Unit) {
    Surface(color=MaterialTheme.colorScheme.background) {
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).height(64.dp)) {
            listOf("home" to "Home","history" to "History","achievements" to "Achievements").forEach{(route,label)->
                val color=if(current==route)Red else MaterialTheme.colorScheme.onSurfaceVariant
                Column(Modifier.weight(1f).fillMaxHeight().clickable{navigate(route)}.semantics {selected=current==route;role=Role.Tab},
                    horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp,Alignment.CenterVertically)) {
                    Glyph(if(route=="achievements")"medal"else route,Modifier.size(PulseTokens.iconSize),color)
                    Text(label,style=MaterialTheme.typography.labelSmall,color=color,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
            }
        }
    }
}
