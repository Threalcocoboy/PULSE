package com.pulse.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulse.app.PulseViewModel
import com.pulse.app.core.*
import java.time.*

@Composable fun CollectionScreen(vm:PulseViewModel,snapshot:HealthSnapshot,badges:List<UserAchievement>,year:(Int)->Unit,month:(YearMonth)->Unit,create:()->Unit,detail:(String)->Unit) {
 var tab by rememberSaveable{mutableIntStateOf(0)}
 val holder=rememberSaveableStateHolder();val challenges by vm.challenges.collectAsStateWithLifecycle()
 Column(Modifier.fillMaxSize()) {
  BoxWithConstraints(Modifier.padding(horizontal=20.dp,vertical=8.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainer)) {
   val half=maxWidth/2
   val x by animateDpAsState(if(tab==0)0.dp else half,tween(if(LocalAnimations.current)220 else 0),label="Collection selection")
   Box(Modifier.offset(x=x).width(half).height(48.dp).padding(4.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface))
   Row{listOf("Badges","Challenges").forEachIndexed{i,label->TextButton({tab=i},Modifier.weight(1f).heightIn(min=48.dp).semantics{selected=tab==i}){Text(label,color=MaterialTheme.colorScheme.onSurface)}}}
  }
  Crossfade(tab,Modifier.weight(1f),animationSpec=tween(if(LocalAnimations.current)220 else 0),label="Collection tab"){selected->holder.SaveableStateProvider(selected){
   if(selected==0)AchievementsScreen(snapshot,badges,year,month)
   else LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
    item{SectionTitle("Challenges","Personal goals, at your pace")};item{PulseButton("Create Challenge",Modifier.fillMaxWidth(),onClick=create)}
    if(challenges.isEmpty())item{PulseCard{Text("Make room for something that matters.");Label("Track a personal habit or use your synchronized health data.")}}
    listOf("Active" to challenges.filter{it.status !in setOf(ChallengeStatus.COMPLETED,ChallengeStatus.EXPIRED,ChallengeStatus.ARCHIVED)},"Completed" to challenges.filter{it.status==ChallengeStatus.COMPLETED},"Expired" to challenges.filter{it.status==ChallengeStatus.EXPIRED},"Archived" to challenges.filter{it.status==ChallengeStatus.ARCHIVED}).forEach{(label,list)->
     if(list.isNotEmpty()){item{Text(label,style=MaterialTheme.typography.titleLarge)};items(list,key={it.id}){c->ChallengeCard(c,{detail(c.id)},vm)}}
    }
   }
  }}
 }
}
@Composable fun ChallengeCard(c:PersonalChallenge,open:(()->Unit)?=null,vm:PulseViewModel?=null){
 PulseCard(Modifier.fillMaxWidth().then(if(open!=null)Modifier.clickable(onClick=open)else Modifier)){
  Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){Glyph(c.icon,Modifier.size(32.dp),Red);Column(Modifier.weight(1f)){Text(c.name.ifBlank{"Your challenge"},style=MaterialTheme.typography.titleMedium);Label("${c.category} · ${if(c.tracking==ChallengeTracking.AUTOMATIC)"Health data"else "Manual"}")}}
  Text(c.summary(),style=MaterialTheme.typography.bodyMedium)
  LinearProgressIndicator(progress={(c.progress/c.goal).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth(),color=Red)
  Label("${formatChallengeAmount(c.progress)} / ${formatChallengeAmount(c.goal)} ${if (c.rule.countsDays) "days" else c.unit} · ${c.status.name.lowercase().replace('_',' ')}")
  Label("${c.start.displayDate()} – ${c.end.displayDate()}")
  if(vm!=null)ManualControls(c,vm)
 }
}
@Composable
private fun ManualControls(challenge: PersonalChallenge, vm: PulseViewModel) {
    if (challenge.tracking == ChallengeTracking.AUTOMATIC || challenge.completed != null ||
        challenge.status != ChallengeStatus.ACTIVE || LocalDate.now() !in challenge.start..challenge.end) return
    var amount by rememberSaveable(challenge.id) { mutableStateOf("1") }
    val haptic = LocalHapticFeedback.current
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val numeric = challenge.tracking == ChallengeTracking.NUMERIC
    val quantity = if (numeric) parseChallengeNumber(amount) else 1.0
    if (numeric) ChallengeField("Amount (${challenge.unit})", amount, { amount = it },
        error = "Enter a number greater than zero.".takeIf { quantity == null },
        keyboard = KeyboardType.Decimal, maxLength = 16)
    Row {
        TextButton({
            if (preferences.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            quantity?.let { vm.addChallengeProgress(challenge.id, it) }
        }, enabled = quantity != null) { Text(if (numeric) "Add" else "Mark completion") }
        TextButton({ vm.undoChallengeProgress(challenge.id) }, enabled = challenge.events.isNotEmpty()) { Text("Undo") }
    }
}
