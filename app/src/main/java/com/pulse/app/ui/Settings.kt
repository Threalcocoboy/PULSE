package com.pulse.app.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import com.pulse.app.BuildConfig
import com.pulse.app.PulseViewModel
import com.pulse.app.core.*
import com.pulse.app.data.ShareExport
import com.pulse.app.integration.HealthConnectRepository
import kotlinx.coroutines.launch

@Composable fun PrivacyRationale(close:()->Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
        DotWordmark();SectionTitle("Your data. Your choice.","PULSE privacy & Health Connect access")
        Text("PULSE requests read-only access to steps, sleep sessions, heart rate and distance to show your dashboard, history, goal progress and monthly step collection.")
        Text("Measurements are processed and cached on your device. Garmin requests use only its official HTTPS API. PULSE does not upload health records to analytics or external AI services or sell records. Cloud backup and device transfer are disabled.")
        Text("You can grant only the categories you choose and revoke access through Health Connect at any time. Unsupported or missing measurements remain unavailable. Demo data is labeled and separate.")
        Text("Privacy & Data in PULSE settings lets you export or delete the local cache and preferences. Deleting local PULSE data does not delete original provider records or revoke Android permissions.")
        Text("Health observations describe records and are not diagnoses or medical treatment.",style=MaterialTheme.typography.bodySmall)
        PulseButton("Close",onClick=close)
    }
}

@Composable fun SettingToggle(title: String,checked: Boolean,change:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=56.dp).toggleableCompat(checked,change),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Text(title,Modifier.weight(1f).padding(end=16.dp));Switch(checked,change)}
}
private fun Modifier.toggleableCompat(checked: Boolean,change:(Boolean)->Unit)=this.clickable {change(!checked)}
@Composable fun SettingsScreen(vm: PulseViewModel,p: Preferences,snapshot: HealthSnapshot,connect:()->Unit,back:()->Unit,studio:()->Unit) {
    var category by rememberSaveable {mutableStateOf("")};var delete by rememberSaveable {mutableStateOf(false)};var reset by rememberSaveable {mutableStateOf(false)}
    val context=LocalContext.current;val scope=rememberCoroutineScope();var exportError by remember {mutableStateOf<String?>(null)};var exporting by remember {mutableStateOf(false)}
    BackHandler(category.isNotEmpty()){category=""}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically){IconAction("back","Back"){if(category.isEmpty())back()else category=""};Label(if(category.isEmpty())"MY PULSE"else"SETTINGS")}
        SectionTitle(category.ifEmpty {"Settings"})
        if(category.isEmpty()) {
            PulseCard(Modifier.fillMaxWidth()){DotWordmark();Label(p.mode.displayName())}
            listOf("Profile & Goals","Devices & Data","Appearance","PULSE Studio","PULSE Intelligence","Notifications","Privacy & Data","About PULSE").forEachIndexed {i,title->
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable {category=title}.padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){Label("0${i+1}",Modifier.width(40.dp),Red);Text(title,Modifier.weight(1f));Glyph("next")}

            }
        }
        when(category){
            "Profile & Goals"->{GoalEditor(p,vm::preferences);SettingToggle("Use miles for distance",p.imperial){v->vm.preferences{it.copy(imperial=v)}};Text("Goals are preferences. Historical measurements and fixed monthly medal thresholds stay independent of them.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            "Devices & Data"->{
                Label("Data source")
                listOf(DataMode.GARMIN to "Garmin Personal API",DataMode.HEALTH_CONNECT to "Health Connect",DataMode.DEMO to "Demo Mode").forEach{(mode,title)->
                    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable{vm.preferences{it.copy(mode=mode)}},verticalAlignment=Alignment.CenterVertically){RadioButton(p.mode==mode,{vm.preferences{it.copy(mode=mode)}});Text(title,style=MaterialTheme.typography.bodyMedium)}
                }
                HorizontalDivider(color=MaterialTheme.colorScheme.outline)
                Text(snapshot.status,style=MaterialTheme.typography.bodyMedium)
                snapshot.synced?.let{MetaText("Last sync ${it.atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"))}")}
                snapshot.error?.let{Text(it,color=Red,style=MaterialTheme.typography.bodySmall)}
                PulseButton("Refresh",enabled=!snapshot.loading){vm.refresh()}
                when(p.mode){
                    DataMode.GARMIN->{GarminSetup(vm);Text("Steps, step distance, resting heart rate, Body Battery, stress, calories and intensity minutes come from daily summaries. Sleep, stage totals, HRV, heart samples and activity counts use their documented endpoints.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    DataMode.HEALTH_CONNECT->{
                        Text("Health Connect",style=MaterialTheme.typography.titleMedium)
                        Label(if(vm.healthConnect.availability()==androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE)"Available on this device"else"Unavailable or needs an update")
                        Text("Read-only steps, sleep sessions, average heart rate and distance. Recent history by default; optional history access imports up to 12 months. Hourly background refresh requires background access.",style=MaterialTheme.typography.bodySmall)
                        PulseButton(if(HealthConnectRepository.permissions.any {it in snapshot.granted}) "✓ Connected · review permissions" else "Review / request permissions",onClick=connect)
                        vm.healthConnect.requestedPermissions().forEach{permission->Label("${if(permission in snapshot.granted)"Granted"else"Missing"} · ${permission.substringAfterLast('.').removePrefix("READ_").lowercase().replace('_',' ')}")}
                    }
                    DataMode.DEMO->Text("Deterministic samples only. Demo mode never writes genuine health or medal records.",style=MaterialTheme.typography.bodySmall)
                }
            }
            "Appearance"->{Label("COLOR THEME");listOf("DARK" to "Dark","LIGHT" to "Light","SYSTEM" to "Follow system").forEach{(key,title)->Row(Modifier.fillMaxWidth().heightIn(min=56.dp).clickable{vm.preferences{it.copy(theme=key)}},verticalAlignment=Alignment.CenterVertically){RadioButton(p.theme==key,{vm.preferences{it.copy(theme=key)}});Text(title)}};SettingToggle("Animations",p.animations){v->vm.preferences{it.copy(animations=v)}};SettingToggle("Haptic feedback",p.haptics){v->vm.preferences{it.copy(haptics=v)}};Text("System animation settings take precedence. Charts always include readable numeric summaries.",style=MaterialTheme.typography.bodySmall)}
            "PULSE Studio"->{Text("A four-column canvas. Adaptive Flow makes space as you move and resize.");PulseButton("Edit dashboard",onClick=studio);OutlinedButton({reset=true},shape=RoundedCornerShape(8.dp)){Text("Restore default layout")};Text("Undo and redo are available in Studio. Up to 100 widgets, including duplicate metrics, are supported.",style=MaterialTheme.typography.bodySmall)}
            "PULSE Intelligence"->{SettingToggle("Local observations",p.insights){v->vm.preferences{it.copy(insights=v)}};PulseCard{Label("ON YOUR DEVICE",color=Red);Text("PULSE compares recorded periods and goals using local calculations. No external AI service is connected, and no health information is uploaded.");Text("These observations describe measurements. They do not diagnose conditions or explain their causes.",style=MaterialTheme.typography.bodySmall)}}
            "Notifications"->{PulseCard{Label("IN-APP UPDATES");Text("Background notifications and reminders are not scheduled in this release.");Text("Goal progress and monthly medal status are available inside PULSE.",style=MaterialTheme.typography.bodySmall)}}
            "Privacy & Data"->{
                PulseCard{Label("PRIVATE BY DESIGN",color=Red);Text("Health Connect and Garmin access are read-only. Records stay in the local database. Garmin credentials use Keystore-backed encryption and are excluded from exports and backups. No analytics or external AI service is used.")}
                Text("Exports contain your current dataset, cached real records, goals and dashboard. The file may contain personal health information. Credentials are excluded.",style=MaterialTheme.typography.bodySmall)
                PulseButton(if(exporting)"Preparing export…"else"Export local data",enabled=!exporting){scope.launch{exporting=true;try{ShareExport.data(context,p,vm.layout.value,snapshot,vm.cachedRecords(),vm.cachedGarminRecords())}catch(e:Exception){exportError="Could not create the export. Please retry."}finally{exporting=false}}}
                exportError?.let{Text(it,color=Red)}
                OutlinedButton({delete=true},shape=RoundedCornerShape(8.dp)){Text("Delete local PULSE data",color=Red)}
                Text("Deleting local data resets preferences and onboarding. It does not delete original records held by Health Connect, Garmin or another provider. Revoke access through Android settings to prevent importing records again.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                SmallAction("Open Android app settings"){context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:${context.packageName}")))}
            }
            "About PULSE"->{DotWordmark();Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})");Label("NATIVE ANDROID / KOTLIN + COMPOSE");Text("Built around awareness, personal goals and a modular dashboard. Visual identity inspired by dot-matrix interfaces. Official PULSE logo supplied by the project owner.");Text("PULSE is not a medical device. It does not provide diagnosis or treatment.",style=MaterialTheme.typography.bodySmall);Label("OPEN SOURCE NOTICES");Text("AndroidX (Compose, Lifecycle, Navigation, DataStore, Room, Health Connect) · Apache License 2.0\nKotlin & kotlinx.coroutines · Apache License 2.0\nGradle · Apache License 2.0",style=MaterialTheme.typography.bodySmall);Text("Dependency license links and the full Apache 2.0 notice are included with the source project in NOTICE.md and licenses/.",style=MaterialTheme.typography.bodySmall)}
        }
        Spacer(Modifier.height(20.dp))
    }
    if(delete)AlertDialog(onDismissRequest={delete=false},title={Text("Delete local PULSE data?")},text={Text("Cached measurements, saved medals, the encrypted Garmin key, preferences and your dashboard will be removed. Original provider records will remain.")},confirmButton={TextButton({delete=false;vm.deleteLocal()}){Text("Delete local data",color=Red)}},dismissButton={TextButton({delete=false}){Text("Cancel")}})
    if(reset)AlertDialog(onDismissRequest={reset=false},title={Text("Restore default dashboard?")},text={Text("Your current layout will be replaced. You can undo this in Studio.")},confirmButton={TextButton({vm.resetLayout();reset=false}){Text("Restore")}},dismissButton={TextButton({reset=false}){Text("Cancel")}})
}
