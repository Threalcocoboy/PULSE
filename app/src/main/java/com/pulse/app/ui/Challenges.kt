package com.pulse.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import kotlinx.coroutines.launch
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
  Label("${number(c.progress)} / ${number(c.goal)} · ${c.status.name.lowercase().replace('_',' ')}")
  Label("${c.start} - ${c.end} · ${c.duration} days")
  if(vm!=null)ManualControls(c,vm)
 }
}
@Composable private fun ManualControls(c:PersonalChallenge,vm:PulseViewModel){
 if(c.tracking==ChallengeTracking.AUTOMATIC||c.completed!=null||c.status!=ChallengeStatus.ACTIVE||LocalDate.now() !in c.start..c.end)return
 var amount by rememberSaveable(c.id){mutableStateOf("1")};val haptic=LocalHapticFeedback.current;val p by vm.preferences.collectAsStateWithLifecycle();val numeric=c.tracking==ChallengeTracking.NUMERIC
 if(numeric)OutlinedTextField(amount,{amount=it},Modifier.fillMaxWidth(),label={Text("Amount (${c.unit})")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
 Row{TextButton({if(p.haptics)haptic.performHapticFeedback(HapticFeedbackType.LongPress);vm.addChallengeProgress(c.id,if(numeric)amount.toDouble()else 1.0)},enabled=!numeric||amount.toDoubleOrNull()?.let{it.isFinite()&&it>0}==true){Text(if(numeric)"Add"else "Mark completion")};TextButton({vm.undoChallengeProgress(c.id)},enabled=c.events.isNotEmpty()){Text("Undo")}}
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ChallengeDetailScreen(vm:PulseViewModel,id:String,back:()->Unit){
 val list by vm.challenges.collectAsStateWithLifecycle();val snapshot by vm.snapshot.collectAsStateWithLifecycle();val c=list.firstOrNull{it.id==id}
 var edit by rememberSaveable{mutableStateOf(false)};var name by rememberSaveable{mutableStateOf("")};var description by rememberSaveable{mutableStateOf("")}
 Scaffold(topBar={TopAppBar(title={Text("Challenge")},navigationIcon={IconAction("back","Back",onClick=back)})}){padding->
  LazyColumn(Modifier.padding(padding).fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
   if(c==null)item{Text("Challenge unavailable")}else{
    item{ChallengeCard(c,vm=vm)};if(c.description.isNotBlank())item{Text(c.description)}
    item{Label("Dates use ${c.zone}. Pausing keeps the original deadline; health history is evaluated when resumed.")}
    item{PulseCard{Row(verticalAlignment=Alignment.CenterVertically){PulseBadgeArt(c.reward(),Modifier.size(72.dp));Column{Text(if(c.completed!=null)"Reward earned"else "Locked reward");Text(c.name);Label("Challenge reward · ${c.category}")}};Label("Earned rewards are permanent. Undo is available until completion.")}}
    if(c.completed==null&&c.status!=ChallengeStatus.ARCHIVED)item{Row{
     TextButton({name=c.name;description=c.description;edit=true}){Text("Edit")}
     if(c.status==ChallengeStatus.PAUSED)TextButton({vm.challengeStatus(id,ChallengeStatus.ACTIVE)}){Text("Resume")}
     else if(c.status!=ChallengeStatus.EXPIRED)TextButton({vm.challengeStatus(id,ChallengeStatus.PAUSED)}){Text("Pause")}
    }}
    if(c.status!=ChallengeStatus.ARCHIVED)item{TextButton({vm.challengeStatus(id,ChallengeStatus.ARCHIVED)}){Text("Archive")}}
    item{Text("Progress history",style=MaterialTheme.typography.titleLarge)}
    if(c.tracking!=ChallengeTracking.AUTOMATIC){if(c.events.isEmpty())item{Label("No entries yet")};items(c.events.asReversed(),key={it.id}){e->Text("${e.date} · +${e.amount} ${c.unit}")}}
    else{
     item{Label("${c.metric.label} · ${c.source.name.lowercase().replace('_',' ')} · missing days remain awaiting data")}
     val days=if(snapshot.mode==c.source&&snapshot.mode!=DataMode.DEMO&&c.zone==ZoneId.systemDefault().id)HistoryMath.unique(snapshot.days).associateBy{it.date}else emptyMap()
     val count=java.time.temporal.ChronoUnit.DAYS.between(c.start,minOf(c.end,LocalDate.now()))
     if(count>=0)items((0L..count).map{c.start.plusDays(it)}){date->val r=days[date]?.readings?.get(c.metric);Text("$date · ${if(r?.state==DataState.READY)r.value?.let{valueText(it,c.metric)}?:"Awaiting data"else "Awaiting data"} ${c.unit}")}
    }
   }
  }
 }
 if(edit)AlertDialog(onDismissRequest={edit=false},title={Text("Edit identity")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Tracking rules stay fixed to preserve progress. Create a new challenge for different rules.",style=MaterialTheme.typography.bodySmall);OutlinedTextField(name,{name=it},label={Text("Name")});OutlinedTextField(description,{description=it},label={Text("Description")})}},confirmButton={TextButton({vm.editChallengeIdentity(id,name,description);edit=false},enabled=name.isNotBlank()){Text("Save")}},dismissButton={TextButton({edit=false}){Text("Cancel")}})
}
@Composable private fun Choice(label:String,value:String,options:List<String>,choose:(Int)->Unit){
 var expanded by remember{mutableStateOf(false)}
 Box{OutlinedButton({expanded=true},Modifier.fillMaxWidth(),enabled=options.isNotEmpty()){Text("$label: $value ▾")};DropdownMenu(expanded,{expanded=false}){options.forEachIndexed{i,s->DropdownMenuItem(text={Text(s)},onClick={choose(i);expanded=false})}}}
}
@Composable private fun EditorSection(icon:String,title:String,summary:String,initial:Boolean=false,content:@Composable ColumnScope.()->Unit){
 var expanded by rememberSaveable(title){mutableStateOf(initial)}
 val duration=if(LocalAnimations.current)220 else 0
 PulseCard(Modifier.fillMaxWidth()){
  Row(Modifier.fillMaxWidth().clickable{expanded=!expanded}.heightIn(min=56.dp).semantics{stateDescription=if(expanded)"Expanded"else "Collapsed"},verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){Glyph(icon,color=Red);Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.titleMedium);Label(summary)};Text(if(expanded)"⌃"else "⌄")}
  AnimatedVisibility(expanded,enter=expandVertically(tween(duration))+fadeIn(tween(duration)),exit=shrinkVertically(tween(duration))+fadeOut(tween(duration))){Column(verticalArrangement=Arrangement.spacedBy(12.dp),content=content)}
 }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ChallengeMaker(vm:PulseViewModel,back:()->Unit){
 var name by rememberSaveable{mutableStateOf("")};var description by rememberSaveable{mutableStateOf("")};var category by rememberSaveable{mutableStateOf("Custom")};var custom by rememberSaveable{mutableStateOf("")}
 var icon by rememberSaveable{mutableStateOf("medal")};var manualIcon by rememberSaveable{mutableStateOf(false)}
 var tracking by rememberSaveable{mutableStateOf(ChallengeTracking.CHECKBOX.name)};var metric by rememberSaveable{mutableStateOf(Metric.STEPS.name)};var rule by rememberSaveable{mutableStateOf(ChallengeRule.CUMULATIVE.name)}
 var target by rememberSaveable{mutableStateOf("5")};var unit by rememberSaveable{mutableStateOf("times")};var duration by rememberSaveable{mutableStateOf("7")};var required by rememberSaveable{mutableStateOf("5")};var start by rememberSaveable{mutableStateOf(LocalDate.now().toString())};var busy by remember{mutableStateOf(false)}
 val snapshot by vm.snapshot.collectAsStateWithLifecycle();val p by vm.preferences.collectAsStateWithLifecycle()
 val metrics=if(snapshot.mode!=DataMode.DEMO)snapshot.available.intersect(ChallengeEvaluator.metrics).toList()else emptyList();val auto=tracking==ChallengeTracking.AUTOMATIC.name
 val date=runCatching{LocalDate.parse(start)}.getOrNull()
 val c=PersonalChallenge(name=name.trim(),description=description.trim(),category=if(category=="Custom")custom.trim().ifBlank{"Custom"}else category,icon=icon,tracking=ChallengeTracking.valueOf(tracking),metric=Metric.valueOf(metric),rule=ChallengeRule.valueOf(rule),target=target.toDoubleOrNull()?:Double.NaN,unit=if(auto)Metric.valueOf(metric).unit else unit.trim(),duration=duration.toIntOrNull()?:0,requiredDays=required.toIntOrNull()?:0,start=date?:LocalDate.now(),source=p.mode)
 val error=when{date==null->"Enter a date as YYYY-MM-DD.";date<LocalDate.now()->"Choose today or a future date.";auto&&c.metric !in metrics->"Synchronize and grant access to a supported health metric first.";else->c.validation()}
 val scope=rememberCoroutineScope();BackHandler(onBack=back)
 Scaffold(modifier=Modifier.imePadding(),topBar={TopAppBar(title={Text("New Challenge")},navigationIcon={IconAction("back","Back",onClick=back)})},bottomBar={Surface{Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
  error?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall,modifier=Modifier.semantics{liveRegion=LiveRegionMode.Polite})}
  PulseButton(if(busy)"Saving…"else "Create Challenge",Modifier.fillMaxWidth(),enabled=error==null&&!busy){busy=true;scope.launch{if(vm.createChallenge(c))back();busy=false}}
 }}}){padding->
  LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   item{ChallengeCard(c.copy(target=c.target.takeIf{it.isFinite()&&it>0}?:1.0,duration=c.duration.coerceAtLeast(1),requiredDays=c.requiredDays.coerceAtLeast(1)))}
   item{EditorSection("edit","Identity",name.ifBlank{"Name your challenge"},true){
    OutlinedTextField(name,{name=it.take(80)},Modifier.fillMaxWidth(),label={Text("Challenge name")},singleLine=true,isError=name.isBlank())
    OutlinedTextField(description,{description=it.take(1000)},Modifier.fillMaxWidth(),label={Text("Description (optional)")})
    val categories=listOf("Sleep","Movement","Heart","Custom")
    Choice("Category",category,categories){category=categories[it];if(!manualIcon)icon=when(category){"Sleep"->"moon";"Movement"->"foot";"Heart"->"heart";else->"medal"}}
    if(category=="Custom")OutlinedTextField(custom,{custom=it.take(40)},Modifier.fillMaxWidth(),label={Text("Custom category (optional)")})
    Label("Choose an icon")
    listOf("moon","foot","heart","book","target","medal","history","home").chunked(4).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){row.forEach{key->IconButton({icon=key;manualIcon=true},Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(if(icon==key)MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface).semantics{contentDescription="$key icon";selected=icon==key}){Glyph(key,color=if(icon==key)Red else MaterialTheme.colorScheme.onSurface)}}}}
   }}
   item{EditorSection("history","Tracking",if(auto)"Automatic · health data"else "Manual · personal goals"){
    Choice("Tracking",when(c.tracking){ChallengeTracking.AUTOMATIC->"Automatic health data";ChallengeTracking.CHECKBOX->"Manual checkbox";else->"Manual numeric"},listOf("Automatic health data","Manual checkbox","Manual numeric")){tracking=ChallengeTracking.entries[it].name;unit=if(it==1)"times"else "minutes"}
    if(auto){Choice("Metric",c.metric.label,metrics.map{it.label}){metric=metrics[it].name};Label("At least the selected target. Daily totals; sleep is assigned to the waking day. One-time verifies a daily milestone, not an individual activity.");if(metrics.isEmpty())Label("Connect a real health source and synchronize to make metrics available.")}
    else{Label(if(c.tracking==ChallengeTracking.CHECKBOX)"Each tap records one completion. Several completions per day are allowed."else "Add positive quantities; each entry is saved separately.");OutlinedTextField(unit,{unit=it.take(24)},Modifier.fillMaxWidth(),label={Text("Unit")},singleLine=true)}
   }}
   item{EditorSection("target","Goal & Rules",c.rule.name.lowercase().replace('_',' ')){
    Choice("Rule",c.rule.name.lowercase().replace('_',' '),listOf("Flexible target","Streak","Cumulative","One-time daily milestone")){rule=ChallengeRule.entries[it].name}
    OutlinedTextField(target,{target=it},Modifier.fillMaxWidth(),label={Text(if(c.rule in setOf(ChallengeRule.FLEXIBLE,ChallengeRule.STREAK))"Daily target (${c.unit})"else "Target (${c.unit})")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)
    OutlinedTextField(duration,{duration=it},Modifier.fillMaxWidth(),label={Text("Duration in days")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true)
    if(c.rule in setOf(ChallengeRule.FLEXIBLE,ChallengeRule.STREAK))OutlinedTextField(required,{required=it},Modifier.fillMaxWidth(),label={Text("Required successful days")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true)
    OutlinedTextField(start,{start=it},Modifier.fillMaxWidth(),label={Text("Start date (YYYY-MM-DD)")},singleLine=true)
   }}
   item{EditorSection("medal","Reward","Automatically awarded once"){
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){PulseBadgeArt(c.reward(),Modifier.size(72.dp));Column{Text(name.ifBlank{"Your challenge"});Label("${c.category} · Challenge reward")}}
    Text("Complete this challenge to unlock its permanent badge in Collection.",style=MaterialTheme.typography.bodyMedium)
   }}
  }
 }
}
