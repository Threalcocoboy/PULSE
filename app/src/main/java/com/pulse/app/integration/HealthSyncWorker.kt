package com.pulse.app.integration
import android.content.Context
import androidx.work.*
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import com.pulse.app.core.*
import com.pulse.app.data.*
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.concurrent.TimeUnit
class HealthSyncWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){
 override suspend fun doWork():Result {val p=PreferenceStore(applicationContext).preferences.first();if(!p.onboarded||p.mode!=DataMode.HEALTH_CONNECT||HealthConnectClient.getSdkStatus(applicationContext)!=HealthConnectClient.SDK_AVAILABLE)return Result.success();val db=PulseDatabase.open(applicationContext);return try {if(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND !in HealthConnectClient.getOrCreate(applicationContext).permissionController.getGrantedPermissions()){ProgressNotifications.cancel(applicationContext);return Result.success()};val s=HealthConnectRepository(applicationContext,db.health()).load(LocalDate.now());ProgressNotifications.publish(applicationContext,p,s);if(s.error!=null&&runAttemptCount<3)Result.retry() else Result.success()}finally{db.close()}}
 companion object {fun schedule(c:Context,enabled:Boolean){val wm=WorkManager.getInstance(c);if(!enabled){wm.cancelUniqueWork("pulse-health-refresh");return};wm.enqueueUniquePeriodicWork("pulse-health-refresh",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<HealthSyncWorker>(1,TimeUnit.HOURS).setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build()).build())}}
}
