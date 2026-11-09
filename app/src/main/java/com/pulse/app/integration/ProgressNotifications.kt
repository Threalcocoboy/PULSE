package com.pulse.app.integration
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pulse.app.*
import com.pulse.app.core.*
import java.time.*
import java.time.format.DateTimeFormatter
object ProgressNotifications {
 const val CHANNEL="pulse_progress"
 fun cancel(c:Context){NotificationManagerCompat.from(c).cancel(401)}
 fun lines(p:Preferences,s:HealthSnapshot):List<String>{val today=s.days.firstOrNull{it.date==LocalDate.now()};return p.notificationMetrics.map{m->val reading=today?.readings?.get(m);val v=reading?.takeIf{it.state in setOf(DataState.READY,DataState.STALE)}?.value;val text=when{v==null->"Unavailable";m==Metric.SLEEP->"$v h";m==Metric.DISTANCE->String.format("%.2f %s",if(p.imperial)v*.621371 else v,if(p.imperial)"mi" else "km");else->"${v.toInt()} ${m.unit}"};"${m.label}: $text"+if(m==Metric.STEPS&&v!=null)" / ${p.stepGoal} goal" else ""}}
 fun publish(c:Context,p:Preferences,s:HealthSnapshot){
 if(!p.progressNotifications||p.mode==DataMode.DEMO||s.mode!=p.mode||s.synced==null||s.loading){cancel(c);return}
 if(s.error?.contains("permission",ignoreCase=true)==true||s.days.none{day->day.date==LocalDate.now()&&p.notificationMetrics.any{m->day.readings[m]?.let{it.value!=null&&it.state in setOf(DataState.READY,DataState.STALE)}==true}}){cancel(c);return}
 if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(c,android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){cancel(c);return}
 val manager=c.getSystemService(NotificationManager::class.java);manager.createNotificationChannel(NotificationChannel(CHANNEL,"PULSE periodic progress",NotificationManager.IMPORTANCE_LOW))
 val intent=PendingIntent.getActivity(c,0,Intent(c,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
 val lines=lines(p,s);val time=s.synced.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM HH:mm"))
 val cached=s.error!=null||Duration.between(s.synced,Instant.now()).toHours()>=2
 val notification=NotificationCompat.Builder(c,CHANNEL).setSmallIcon(R.drawable.ic_pulse).setContentTitle(if(cached)"PULSE · cached progress" else "PULSE · today").setContentText(lines.joinToString(" · ")).setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")+"\nUpdated $time · periodic refresh")).setContentIntent(intent).setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setOngoing(false).setWhen(s.synced.toEpochMilli()).build()
 NotificationManagerCompat.from(c).notify(401,notification)
 }
}
