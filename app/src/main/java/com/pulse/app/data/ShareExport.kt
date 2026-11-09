package com.pulse.app.data

import android.content.*
import android.graphics.*
import androidx.core.content.FileProvider
import com.pulse.app.core.*
import com.pulse.app.ui.drawMedal
import kotlinx.coroutines.*
import org.json.*
import java.io.File
import java.time.*

object ShareExport {
    suspend fun data(context: Context,p: Preferences,layout: List<Widget>,snapshot: HealthSnapshot,cached: List<DailyEntity>,garmin: List<GarminDayEntity> = emptyList()) {
        val file=withContext(Dispatchers.IO) {
            val root=JSONObject().put("format","pulse-export-v1").put("exportedAt",Instant.now().toString()).put("zone",ZoneId.systemDefault().id)
                .put("mode",p.mode.name).put("preferences",PreferencesCodec.encode(p.copy(displayName=""))).put("layout",LayoutCodec.encode(layout))
            val days=JSONArray();snapshot.days.forEach {day->val readings=JSONObject();day.readings.forEach{(m,r)->readings.put(m.name,JSONObject().put("value",r.value ?: JSONObject.NULL).put("state",r.state.name).put("updated",r.updated?.toString() ?: JSONObject.NULL))};days.put(JSONObject().put("date",day.date.toString()).put("complete",day.complete).put("readings",readings))};root.put("currentDataset",days)
            val real=JSONArray();cached.forEach{r->real.put(JSONObject().put("date",r.date).put("zone",r.zone).put("steps",r.steps ?: JSONObject.NULL).put("sleepHours",r.sleep ?: JSONObject.NULL).put("averageHeartBpm",r.heart ?: JSONObject.NULL).put("distanceKm",r.distance ?: JSONObject.NULL).put("syncedAt",Instant.ofEpochMilli(r.synced).toString()).put("source","HEALTH_CONNECT"))};root.put("cachedRealRecords",real)
            val garminRows=JSONArray();garmin.forEach{r->garminRows.put(JSONObject(r.payload).put("source","GARMIN").put("calendar","GARMIN_ACCOUNT_LOCAL").put("synced",r.synced))};root.put("cachedGarminRecords",garminRows)
            File(context.cacheDir,"exports").apply{mkdirs()}.resolve("pulse-data.json").apply{writeText(root.toString(2))}
        }
        share(context,file,"application/json","PULSE local data")
    }
    fun medal(context: Context,medal: Medal,demo: Boolean) {
        val bitmap=Bitmap.createBitmap(800,1060,Bitmap.Config.ARGB_8888);val c=Canvas(bitmap);c.drawColor(0xFF090909.toInt())
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.WHITE;textAlign=Paint.Align.CENTER;typeface=Typeface.create("monospace",Typeface.NORMAL);textSize=32f}
        c.drawText("P U L S E",400f,90f,paint);paint.color=0xFFFF3535.toInt();paint.textSize=16f;c.drawText(if(demo)"DEMO COLLECTION / SAMPLE DATA"else"SEASONAL COLLECTION",400f,128f,paint)
        c.save();c.translate(100f,180f);c.scale(1.5f,1.5f);drawMedal(c,medal);c.restore()
        paint.color=Color.WHITE;paint.textSize=30f;c.drawText(medal.month.month.name+" "+medal.month.year,400f,850f,paint);paint.textSize=22f;c.drawText("${medal.tier?.name} · ${medal.total} STEPS",400f,900f,paint);paint.textSize=16f;paint.color=0xFFA0A0A0.toInt();c.drawText(medal.state,400f,942f,paint)
        val file=File(context.cacheDir,"exports").apply{mkdirs()}.resolve("pulse-medal.png");file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle();share(context,file,"image/png","PULSE monthly medal")
    }
    private fun share(context: Context,file: File,type: String,title: String) {
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM,uri).putExtra(Intent.EXTRA_SUBJECT,title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply{clipData=ClipData.newRawUri(title,uri)},title))
    }
}
