package com.pulse.app.ui
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.LocalUriHandler
import com.pulse.app.PulseViewModel
import com.pulse.app.core.*
@Composable fun Onboarding(p:Preferences,snapshot:HealthSnapshot,update:((Preferences)->Preferences)->Unit,connect:()->Unit,vm:PulseViewModel?=null,finish:()->Unit){
 var page by rememberSaveable{mutableIntStateOf(p.onboardingStep)}
 fun go(next:Int){page=next.coerceIn(0,6);update{it.copy(onboardingStep=page)}}
 BackHandler(page>0){go(page-1)}
 val uri=LocalUriHandler.current
 Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
 Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){DotWordmark();Label("${page+1} / 7")}
 Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){repeat(7){i->Box(Modifier.weight(1f).height(3.dp).background(if(i<=page)Red else MaterialTheme.colorScheme.outline))}}
 Text(listOf("Welcome","Your name","Health Connect","Connect Garmin","Personal goals","Progress notifications","Ready")[page],style=MaterialTheme.typography.headlineLarge)
 key(page){Column(Modifier.reveal(),verticalArrangement=Arrangement.spacedBy(14.dp)){when(page){
 0->{BrandLogo(Modifier.fillMaxWidth().height(120.dp));Text("Your health. Your space.",style=MaterialTheme.typography.headlineLarge);Text("Measurements, progress and a dashboard that fits you.");PulseButton("Get started",Modifier.fillMaxWidth()){go(1)};SmallAction("Explore demo data"){update{it.copy(mode=DataMode.DEMO)};go(1)}}
 1->{Text("What should PULSE call you?");DisplayNameEditor(p,update);Text("Optional. Stored only on this device for greetings. Never sent to a health provider or included in exports.",style=MaterialTheme.typography.bodySmall);PulseButton("Continue"){go(2)};SmallAction("Skip name"){update{it.copy(displayName="")};go(2)}}
 2->{Text("Choose the measurements you want to share. Access is read only and can be revoked.");Text("Steps, distance, heart samples, resting heart, HRV, calories, exercise and sleep stages when supplied by your apps.",style=MaterialTheme.typography.bodySmall);PulseButton(if(snapshot.mode==DataMode.HEALTH_CONNECT&&snapshot.granted.isNotEmpty())"✓ Review Health Connect" else "Connect Health Connect",onClick=connect);SyncFeedback(snapshot);Text(snapshot.status,style=MaterialTheme.typography.bodySmall);PulseButton("Continue"){go(3)};SmallAction("Use demo for now"){update{it.copy(mode=DataMode.DEMO)};go(3)}}
 3->{Text("Optional personal API key access. This is separate from delegated Garmin OAuth.");var guide by remember{mutableStateOf(false)};SmallAction("Learn how"){guide=!guide};if(guide){Text("In Garmin Connect Web, open Personal API Key. Choose Create Key, accept the terms, choose expiry, then Create. Copy and protect the key: Garmin displays it once.",style=MaterialTheme.typography.bodySmall);TextButton({uri.openUri("https://developer.garmin.com/gc-developer-program/personal/personal-api/")}){Text("Official Garmin guide")}}
 if(vm!=null)GarminSetup(vm);Text("Keys are checked through the existing real Garmin token endpoint before replacement. A stored key does not guarantee ongoing access.",style=MaterialTheme.typography.bodySmall);PulseButton("Continue"){go(4)};SmallAction("Skip for now"){go(4)}}
 4->{GoalEditor(p,update);PulseButton("Continue"){go(5)}}
 5->{NotificationSetup(p,snapshot,update);PulseButton("Continue"){go(6)};SmallAction("Skip notifications"){update{it.copy(progressNotifications=false)};go(6)}}
 else->{SuccessMark();Text(if(p.displayName.isBlank())"Your PULSE is ready." else "Ready, ${p.displayName}.",style=MaterialTheme.typography.titleLarge);Text("${p.mode.displayName()} · ${p.enabledGoals.size} personal goals");Text("Missing measurements stay unavailable. You can change any choice in Settings.");PulseButton("Open PULSE",Modifier.fillMaxWidth(),onClick=finish)}
 }}}
 if(page>0)SmallAction("Back"){go(page-1)}
 }
}
@Composable fun DisplayNameEditor(p:Preferences,update:((Preferences)->Preferences)->Unit){var name by rememberSaveable{mutableStateOf(p.displayName)};OutlinedTextField(name,{name=it.take(40);val v=name;update{it.copy(displayName=v)}},label={Text("Display name (optional)")},singleLine=true,modifier=Modifier.fillMaxWidth());Row{SmallAction("Save name"){val v=name;update{it.copy(displayName=v)}};SmallAction("Delete name"){name="";update{it.copy(displayName="")}}}}
@Composable fun GoalEditor(p: Preferences,update: (((Preferences)->Preferences))->Unit) {
    var draft by remember(p) { mutableStateOf(p) }
    var saved by remember { mutableStateOf(false) }
    fun persistGoals(){val goals=draft;update{it.copy(stepGoal=goals.stepGoal,sleepGoal=goals.sleepGoal,distanceGoal=goals.distanceGoal,activeGoal=goals.activeGoal,workoutGoal=goals.workoutGoal,enabledGoals=goals.enabledGoals)}}
    var adding by remember{mutableStateOf(false)}
    if(adding)AlertDialog(onDismissRequest={adding=false},title={Text("New goal")},text={Column{(Goals.available(p.mode)-draft.enabledGoals).forEach{m->TextButton({draft=draft.copy(enabledGoals=draft.enabledGoals+m);adding=false;saved=false;persistGoals()}){Text(if(m==Metric.ACTIVITIES)"Workouts per week" else m.label)}}}},confirmButton={TextButton({adding=false}){Text("Cancel")}})
    (draft.enabledGoals.intersect(Goals.available(p.mode))).forEach { metric ->
        val enabled=metric in draft.enabledGoals
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Checkbox(enabled,{ checked -> draft=draft.copy(enabledGoals=if(checked) draft.enabledGoals+metric else draft.enabledGoals-metric);saved=false;persistGoals() })
            Text(if(metric==Metric.ACTIVITIES) "Workouts per week" else metric.label,Modifier.weight(1f))
            if(enabled) Text("${number(Goals.target(metric,draft))} ${if(metric==Metric.ACTIVITIES) "sessions" else metric.unit}")
        }
        if(enabled) {
            val range=when(metric) { Metric.STEPS->1000f..30000f;Metric.SLEEP->4f..12f;Metric.DISTANCE->.5f..30f;Metric.ACTIVE->5f..180f;else->1f..14f }
            Slider(Goals.target(metric,draft).toFloat().coerceIn(range),{ value ->
                draft=when(metric) {
                    Metric.STEPS->draft.copy(stepGoal=(value/100).toInt()*100)
                    Metric.SLEEP->draft.copy(sleepGoal=(value*4).toInt()/4.0)
                    Metric.DISTANCE->draft.copy(distanceGoal=(value*2).toInt()/2.0)
                    Metric.ACTIVE->draft.copy(activeGoal=value.toInt())
                    else->draft.copy(workoutGoal=value.toInt())
                };saved=false
            },onValueChangeFinished={persistGoals()},valueRange=range,modifier=Modifier.semantics {contentDescription="${metric.label} goal"})
        }
    }
    SmallAction("+ New goal",draft.enabledGoals.intersect(Goals.available(p.mode)).size<Goals.available(p.mode).size){adding=true}
    PulseButton(if(saved) "✓ Goals saved" else "Save goals",enabled=draft.enabledGoals.isNotEmpty()) {
        val goals=draft
        update { it.copy(stepGoal=goals.stepGoal,sleepGoal=goals.sleepGoal,distanceGoal=goals.distanceGoal,activeGoal=goals.activeGoal,workoutGoal=goals.workoutGoal,enabledGoals=goals.enabledGoals) };saved=true
    }
}
