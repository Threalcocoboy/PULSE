package com.pulse.app.ui
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.pulse.app.core.*
@Composable fun UnlockCelebration(items:List<UserAchievement>,acknowledge:(List<String>)->Unit){if(items.isEmpty())return
 Dialog(onDismissRequest={},properties=DialogProperties(usePlatformDefaultWidth=false,dismissOnBackPress=false,dismissOnClickOutside=false)){
 Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
 DotWordmark();Text(if(items.size==1)"Achievement unlocked" else "Achievements unlocked",style=MaterialTheme.typography.headlineLarge)
 Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(20.dp)){items.forEachIndexed{i,item->Row(Modifier.reveal(i),verticalAlignment=Alignment.CenterVertically){PulseBadgeArt(item,Modifier.size(if(items.size==1)130.dp else 72.dp));Column(Modifier.weight(1f).padding(start=16.dp)){Text(item.definition.name,style=MaterialTheme.typography.titleLarge);Text(item.definition.description,style=MaterialTheme.typography.bodyMedium);item.unlockedDate?.let{Label("Unlocked $it")};if(item.period!=null)Label(item.definition.name,color=Red)}}}}
 PulseButton("Continue",Modifier.fillMaxWidth()){acknowledge(items.map{it.instanceId})}
 }}
 }
}
