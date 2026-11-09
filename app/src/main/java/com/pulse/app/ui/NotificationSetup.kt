package com.pulse.app.ui
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pulse.app.core.*
import com.pulse.app.integration.ProgressNotifications
@Composable fun NotificationSetup(p:Preferences,s:HealthSnapshot,update:((Preferences)->Preferences)->Unit){
 var denied by remember{mutableStateOf(false)}
 val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->update{it.copy(progressNotifications=granted)};denied=!granted}
 Text("Periodic progress",style=MaterialTheme.typography.titleLarge)
 Text("Optional, quiet updates after sync. Android schedules background work; this is not continuous live monitoring.",style=MaterialTheme.typography.bodyMedium)
 listOf(Metric.STEPS,Metric.DISTANCE,Metric.CALORIES,Metric.ACTIVE).forEach{m->Row(Modifier.fillMaxWidth().heightIn(min=48.dp)){Checkbox(m in p.notificationMetrics,{selected->update{it.copy(notificationMetrics=if(selected)it.notificationMetrics+m else it.notificationMetrics-m)}});Text(m.label)}}
 Label(if(s.mode==DataMode.DEMO)"SAMPLE PREVIEW · NOT LIVE" else "PREVIEW · LAST IMPORTED VALUES")
 ProgressNotifications.lines(p,s).forEach{Text(it,style=MaterialTheme.typography.bodySmall)}
 PulseButton(if(p.progressNotifications)"Disable progress notification" else "Enable progress notification",enabled=p.notificationMetrics.isNotEmpty()){
 if(p.progressNotifications)update{it.copy(progressNotifications=false)} else if(Build.VERSION.SDK_INT>=33)launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else update{it.copy(progressNotifications=true)}
 }
 if(denied)Text("Notifications were not allowed. You can continue without them.",color=Red)
}
