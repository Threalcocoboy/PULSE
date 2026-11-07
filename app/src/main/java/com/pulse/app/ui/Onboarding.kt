package com.pulse.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.pulse.app.R
import com.pulse.app.core.*

@Composable fun Onboarding(p: Preferences,snapshot: HealthSnapshot,update: (((Preferences)->Preferences))->Unit,connect:()->Unit,finish:()->Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(page>0) { page-- }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(24.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) { DotWordmark(); Label("0${page+1} / 05") }
        LinearProgressIndicator(progress={ (page+1)/5f },modifier=Modifier.fillMaxWidth().semantics {contentDescription="Onboarding step ${page+1} of 5"},color=Red)
        when(page) {
            0 -> {
                PulseIntro(Modifier.fillMaxWidth().height(140.dp))
                Spacer(Modifier.height(20.dp))
                Text("Your health.\nYour way.",style=MaterialTheme.typography.displayLarge)
                Text("One place for your health, activity and recovery.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                PulseButton("Get Started",Modifier.fillMaxWidth()) { page=1 }
                OutlinedButton({update {it.copy(mode=DataMode.DEMO)};page=2},Modifier.fillMaxWidth().heightIn(min=48.dp),shape=RoundedCornerShape(8.dp)) { Text("Continue in Demo Mode") }
            }
            1 -> {
                SectionTitle("A clear connection.","You choose what PULSE can read.")
                PulseCard { Label("ANDROID HEALTH CONNECT",color=Red); Text("Steps, sleep, heart rate and distance",style=MaterialTheme.typography.titleLarge); Text("Read only. Permission can be changed at any time. Only records shared by your health apps are available."); PulseButton(if(snapshot.mode==DataMode.HEALTH_CONNECT && com.pulse.app.integration.HealthConnectRepository.permissions.any {it in snapshot.granted}) "✓ Health Connect connected" else "Authorize Health Connect",onClick=connect)
                    if(snapshot.mode==DataMode.HEALTH_CONNECT) {if(snapshot.loading) EcgSync() else Text(snapshot.error ?: snapshot.status,style=MaterialTheme.typography.bodySmall)} }
                PulseCard {Label("Garmin Personal API");Text("Import your own key securely in Settings after setup. Garmin is an independent read-only source; its data is kept separate from Health Connect.",style=MaterialTheme.typography.bodySmall)}
                SmallAction("Use clearly labeled demo data") { update {it.copy(mode=DataMode.DEMO)} }
                PulseButton("Continue",Modifier.fillMaxWidth()) { page=2 }
            }
            2 -> {
                SectionTitle("Set your own pace.","Personal targets. Change them whenever you like.")
                GoalEditor(p,update)
                Text("These starting values are personal preferences, not medical recommendations.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                PulseButton("Continue",Modifier.fillMaxWidth()) {page=3}
            }
            3 -> {
                SectionTitle("Make room for you.","Meet PULSE Studio.")
                Box(Modifier.fillMaxWidth().height(180.dp).background(MaterialTheme.colorScheme.surface,RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center) { DotRing(.72,Modifier.size(150.dp)); Text("72%",fontSize=32.sp) }
                Text("Long press a widget to edit. Drag it into place. Pull its corner to resize. Adaptive Flow makes space automatically.",style=MaterialTheme.typography.titleMedium)
                Text("Add metrics, change shapes, and undo any layout change.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                PulseButton("Continue",Modifier.fillMaxWidth()) {page=4}
            }
            else -> {
                SuccessMark()
                SectionTitle("All set.","A little more awareness. A space of your own.")
                PulseCard { Label(if(p.mode==DataMode.DEMO) "DEMO MODE" else "HEALTH CONNECT",color=Red); Text(if(p.mode==DataMode.DEMO) "Explore with deterministic sample data. Your real health records stay separate." else "Your dashboard shows only available, permitted measurements. Missing data stays empty.") }
                PulseButton("Open PULSE",Modifier.fillMaxWidth(),onClick=finish)
            }
        }
        if(page>0) SmallAction("Back") {page--}
    }
}
@Composable fun GoalEditor(p: Preferences,update: (((Preferences)->Preferences))->Unit) {
    var draft by remember(p) { mutableStateOf(p) }
    var saved by remember { mutableStateOf(false) }
    Goals.available(p.mode).forEach { metric ->
        val enabled=metric in draft.enabledGoals
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Checkbox(enabled,{ checked -> draft=draft.copy(enabledGoals=if(checked) draft.enabledGoals+metric else draft.enabledGoals-metric);saved=false })
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
            },valueRange=range,modifier=Modifier.semantics {contentDescription="${metric.label} goal"})
        }
    }
    PulseButton(if(saved) "✓ Goals saved" else "Save goals",enabled=draft.enabledGoals.isNotEmpty()) {
        val goals=draft
        update { it.copy(stepGoal=goals.stepGoal,sleepGoal=goals.sleepGoal,distanceGoal=goals.distanceGoal,activeGoal=goals.activeGoal,workoutGoal=goals.workoutGoal,enabledGoals=goals.enabledGoals) };saved=true
    }
}
