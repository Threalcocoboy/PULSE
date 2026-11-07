package com.pulse.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.pulse.app.R
import com.pulse.app.core.*

@Composable fun Onboarding(p: Preferences,update: (((Preferences)->Preferences))->Unit,connect:()->Unit,finish:()->Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(page>0) { page-- }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(24.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) { DotWordmark(); Label("0${page+1} / 05") }
        Spacer(Modifier.height(24.dp))
        when(page) {
            0 -> {
                Image(painterResource(R.drawable.pulse_logo),"Official PULSE logo",Modifier.fillMaxWidth().aspectRatio(3.1f).background(androidx.compose.ui.graphics.Color.Black,RoundedCornerShape(12.dp)),contentScale=ContentScale.Fit)
                Spacer(Modifier.height(20.dp))
                Text("Your health.\nYour way.",style=MaterialTheme.typography.displayLarge)
                Text("One place for your health, activity and recovery.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                PulseButton("Get Started",Modifier.fillMaxWidth()) { page=1 }
                OutlinedButton({update {it.copy(mode=DataMode.DEMO)};page=2},Modifier.fillMaxWidth().heightIn(min=48.dp),shape=RoundedCornerShape(8.dp)) { Text("Continue in Demo Mode") }
            }
            1 -> {
                SectionTitle("A clear connection.","You choose what PULSE can read.")
                PulseCard { Label("ANDROID HEALTH CONNECT",color=Red); Text("Steps, sleep, heart rate and distance",style=MaterialTheme.typography.titleLarge); Text("Read only. Permission can be changed at any time. Only records shared by your health apps are available."); PulseButton("Authorize Health Connect",onClick=connect) }
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
                SectionTitle("All set.","A little more awareness. A space of your own.")
                PulseCard { Label(if(p.mode==DataMode.DEMO) "DEMO MODE" else "HEALTH CONNECT",color=Red); Text(if(p.mode==DataMode.DEMO) "Explore with deterministic sample data. Your real health records stay separate." else "Your dashboard shows only available, permitted measurements. Missing data stays empty.") }
                PulseButton("Open PULSE",Modifier.fillMaxWidth(),onClick=finish)
            }
        }
        if(page>0) SmallAction("Back") {page--}
    }
}
@Composable fun GoalEditor(p: Preferences,update: (((Preferences)->Preferences))->Unit) {
    var steps by rememberSaveable(p.stepGoal) {mutableStateOf(p.stepGoal.toString())}
    var sleep by rememberSaveable(p.sleepGoal) {mutableStateOf(p.sleepGoal.toString())}
    OutlinedTextField(steps,{ steps=it.filter(Char::isDigit).take(6) },label={Text("Daily step goal")},singleLine=true,modifier=Modifier.fillMaxWidth(),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Number))
    OutlinedTextField(sleep,{sleep=it.take(5)},label={Text("Sleep goal · hours")},singleLine=true,modifier=Modifier.fillMaxWidth(),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Decimal))
    val valid=(steps.toIntOrNull() ?: 0) in 100..100000 && (sleep.toDoubleOrNull() ?: 0.0) in 1.0..16.0
    PulseButton("Save goals",enabled=valid) {update {it.copy(stepGoal=steps.toInt(),sleepGoal=sleep.toDouble())}}
    Label("CURRENT: ${number(p.stepGoal.toDouble())} STEPS · ${p.sleepGoal}H SLEEP")
}
