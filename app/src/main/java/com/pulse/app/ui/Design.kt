package com.pulse.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import com.pulse.app.core.*
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale
import kotlin.math.*

val Red=Color(0xFFFF3535)
val LocalAnimations=staticCompositionLocalOf { true }
val LocalDemo=staticCompositionLocalOf { true }
object PulseTokens {
    val screenPadding=20.dp
    val gridGap=8.dp
    val cardPadding=14.dp
    val corner=12.dp
    val touchTarget=48.dp
    val iconSize=20.dp
    const val reflowMillis=220
    val dotRadius=2.dp
    val display=FontFamily.Serif
    val body=FontFamily.SansSerif
    val mono=FontFamily.Monospace
}
private val dark=darkColorScheme(primary=Red,onPrimary=Color.White,secondary=Red,secondaryContainer=Color(0xFF301717),onSecondaryContainer=Red,primaryContainer=Color(0xFF301717),onPrimaryContainer=Color.White,background=Color(0xFF090909),surface=Color(0xFF171717),surfaceContainer=Color(0xFF222222),onBackground=Color.White,onSurface=Color.White,onSurfaceVariant=Color(0xFFA0A0A0),outline=Color(0xFF343434))
private val light=lightColorScheme(primary=Color(0xFFC91D26),onPrimary=Color.White,secondary=Color(0xFFC91D26),secondaryContainer=Color(0xFFF6DFDA),onSecondaryContainer=Color(0xFF9E1720),primaryContainer=Color(0xFFF6DFDA),onPrimaryContainer=Color(0xFF111111),background=Color(0xFFF4F3EF),surface=Color.White,surfaceContainer=Color(0xFFE9E8E3),onBackground=Color(0xFF111111),onSurface=Color(0xFF111111),onSurfaceVariant=Color(0xFF595959),outline=Color(0xFFD0CECA))
@Composable fun PulseTheme(p: Preferences, content: @Composable () -> Unit) {
    val isDark=when(p.theme) { "LIGHT" -> false; "SYSTEM" -> isSystemInDarkTheme(); else -> true }
    CompositionLocalProvider(LocalAnimations provides (p.animations && android.provider.Settings.Global.getFloat(androidx.compose.ui.platform.LocalContext.current.contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f),LocalDemo provides (p.mode==DataMode.DEMO)) {
        MaterialTheme(colorScheme=if(isDark) dark else light,typography=Typography(
            displayLarge=androidx.compose.ui.text.TextStyle(fontFamily=PulseTokens.display,fontSize=40.sp,lineHeight=44.sp,fontWeight=FontWeight.Normal),
            headlineLarge=androidx.compose.ui.text.TextStyle(fontFamily=PulseTokens.display,fontSize=30.sp,lineHeight=36.sp,fontWeight=FontWeight.Normal),
            headlineMedium=androidx.compose.ui.text.TextStyle(fontFamily=PulseTokens.display,fontSize=26.sp,lineHeight=32.sp),
            titleLarge=androidx.compose.ui.text.TextStyle(fontFamily=PulseTokens.body,fontSize=20.sp,lineHeight=26.sp,fontWeight=FontWeight.Medium),
            titleMedium=androidx.compose.ui.text.TextStyle(fontFamily=PulseTokens.body,fontSize=16.sp,lineHeight=22.sp,fontWeight=FontWeight.Medium),
            bodyLarge=androidx.compose.ui.text.TextStyle(fontFamily=PulseTokens.body,fontSize=16.sp,lineHeight=24.sp),
            bodyMedium=androidx.compose.ui.text.TextStyle(fontFamily=PulseTokens.body,fontSize=14.sp,lineHeight=20.sp),
            bodySmall=androidx.compose.ui.text.TextStyle(fontFamily=PulseTokens.body,fontSize=12.sp,lineHeight=17.sp),
            labelSmall=androidx.compose.ui.text.TextStyle(fontFamily=PulseTokens.body,fontSize=11.sp,lineHeight=15.sp,letterSpacing=0.sp)),content=content)
    }
}
fun number(value: Double?) = value?.takeIf { it.isFinite() }?.let { NumberFormat.getIntegerInstance().format(it) } ?: "—"
fun valueText(value: Double?, metric: Metric, imperial: Boolean=false): String {
    if(value==null) return "—"
    return when(metric) {
        Metric.SLEEP, Metric.SLEEP_STAGES -> (value*60).roundToInt().let {"${it/60}h ${it%60}m"}
        Metric.DISTANCE -> String.format(Locale.getDefault(),"%.2f",if(imperial) value*0.621371 else value)
        else -> number(value)
    }
}
@Composable fun Label(text: String, modifier: Modifier=Modifier, color: Color=MaterialTheme.colorScheme.onSurfaceVariant) {
    val display=if(text==text.uppercase(Locale.ROOT) && text.any(Char::isLetter) && text !in setOf("HRV","PULSE","BPM")) text.lowercase(Locale.ROOT).replaceFirstChar(Char::titlecase) else text
    Text(display,modifier,style=MaterialTheme.typography.bodySmall,color=color)
}
@Composable fun MetaText(text: String,modifier: Modifier=Modifier) {Text(text,modifier,fontFamily=PulseTokens.mono,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
@Composable fun PulseCard(modifier: Modifier=Modifier, content: @Composable ColumnScope.()->Unit) {
    Column(modifier.clip(RoundedCornerShape(PulseTokens.corner)).background(MaterialTheme.colorScheme.surface).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
}
@Composable fun SectionTitle(title: String, subtitle: String?=null) {
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) { Text(title,style=MaterialTheme.typography.headlineLarge); subtitle?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) } }
}
@Composable fun PulseButton(text: String, modifier: Modifier=Modifier, enabled: Boolean=true, onClick:()->Unit) {
    Button(onClick,modifier.heightIn(min=48.dp),enabled,shape=RoundedCornerShape(8.dp),contentPadding=PaddingValues(horizontal=20.dp,vertical=12.dp)) { Text(text) }
}
@Composable fun SmallAction(text: String, enabled: Boolean=true, onClick:()->Unit) {
    TextButton(onClick,enabled=enabled,contentPadding=PaddingValues(horizontal=12.dp,vertical=12.dp)) { Text(text,fontSize=12.sp) }
}
@Composable fun Glyph(name: String, modifier: Modifier=Modifier, color: Color=LocalContentColor.current) {
    Canvas(modifier.size(22.dp)) {
        val s=size.minDimension; val stroke=Stroke(s*0.065f,cap=StrokeCap.Round,join=StrokeJoin.Round)
        fun line(x1: Float,y1: Float,x2: Float,y2: Float) { drawLine(color,Offset(x1*s,y1*s),Offset(x2*s,y2*s),s*0.065f,StrokeCap.Round) }
        when(name) {
            "moon" -> {
                val crescent = Path().apply {
                    moveTo(.6f*s,.13f*s)
                    cubicTo(.2f*s,.07f*s,.06f*s,.47f*s,.23f*s,.72f*s)
                    cubicTo(.38f*s,.94f*s,.74f*s,.92f*s,.87f*s,.66f*s)
                    cubicTo(.51f*s,.73f*s,.38f*s,.36f*s,.6f*s,.13f*s)
                    close()
                }
                drawPath(crescent,color,style=stroke)
            }
            "calendar" -> {
                drawRoundRect(color,Offset(.15f*s,.22f*s),Size(.7f*s,.63f*s),CornerRadius(.07f*s),style=stroke)
                line(.15f,.41f,.85f,.41f)
                line(.33f,.12f,.33f,.3f)
                line(.67f,.12f,.67f,.3f)
                drawCircle(color,.055f*s,Offset(.5f*s,.63f*s))
            }
            "stopwatch" -> {
                drawCircle(color,.3f*s,Offset(.5f*s,.57f*s),style=stroke)
                line(.5f,.14f,.5f,.26f)
                line(.39f,.14f,.61f,.14f)
                line(.5f,.57f,.5f,.39f)
                line(.5f,.57f,.64f,.63f)
                line(.72f,.29f,.8f,.22f)
            }
            "dumbbell" -> {
                line(.35f,.5f,.65f,.5f)
                drawRoundRect(color,Offset(.19f*s,.28f*s),Size(.16f*s,.44f*s),CornerRadius(.04f*s),style=stroke)
                drawRoundRect(color,Offset(.65f*s,.28f*s),Size(.16f*s,.44f*s),CornerRadius(.04f*s),style=stroke)
                line(.09f,.5f,.19f,.5f)
                line(.81f,.5f,.91f,.5f)
            }
            "check" -> { line(.2f,.5f,.42f,.72f);line(.42f,.72f,.83f,.28f) }
            "pause" -> { line(.34f,.2f,.34f,.8f);line(.66f,.2f,.66f,.8f) }
            "play" -> { line(.32f,.19f,.32f,.81f);line(.32f,.81f,.78f,.5f);line(.78f,.5f,.32f,.19f) }
            "archive" -> {
                drawRoundRect(color,Offset(.13f*s,.19f*s),Size(.74f*s,.22f*s),CornerRadius(.04f*s),style=stroke)
                line(.2f,.41f,.2f,.83f);line(.2f,.83f,.8f,.83f);line(.8f,.83f,.8f,.41f)
                line(.4f,.56f,.6f,.56f)
            }
            "foot" -> { drawOval(color,Offset(s*.2f,s*.15f),Size(s*.2f,s*.42f),style=stroke);drawOval(color,Offset(s*.6f,s*.35f),Size(s*.2f,s*.42f),style=stroke);drawCircle(color,s*.06f,Offset(s*.3f,s*.75f));drawCircle(color,s*.06f,Offset(s*.7f,s*.9f)) }
            "heart" -> { val path=Path().apply{moveTo(.5f*s,.85f*s);cubicTo(-.2f*s,.4f*s,.25f*s,-.1f*s,.5f*s,.3f*s);cubicTo(.75f*s,-.1f*s,1.2f*s,.4f*s,.5f*s,.85f*s)};drawPath(path,color,style=stroke) }
            "book" -> { line(.5f,.2f,.5f,.85f);line(.12f,.15f,.5f,.25f);line(.88f,.15f,.5f,.25f);line(.12f,.15f,.12f,.75f);line(.88f,.15f,.88f,.75f);line(.12f,.75f,.5f,.85f);line(.88f,.75f,.5f,.85f) }
            "target" -> { drawCircle(color,s*.35f,style=stroke);drawCircle(color,s*.18f,style=stroke);drawCircle(color,s*.04f) }
            "home" -> { val path=Path().apply { moveTo(.12f*s,.44f*s); lineTo(.5f*s,.12f*s); lineTo(.88f*s,.44f*s); lineTo(.88f*s,.88f*s); lineTo(.58f*s,.88f*s); lineTo(.58f*s,.61f*s); lineTo(.4f*s,.61f*s); lineTo(.4f*s,.88f*s); lineTo(.12f*s,.88f*s); close() }; drawPath(path,color,style=stroke) }
            "history" -> { drawCircle(color,s*.35f,style=stroke); line(.5f,.26f,.5f,.51f); line(.5f,.51f,.67f,.61f) }
            "medal" -> { drawCircle(color,s*.25f,Offset(.5f*s,.65f*s),style=stroke); line(.32f,.08f,.18f,.25f); line(.18f,.25f,.39f,.43f); line(.68f,.08f,.82f,.25f); line(.82f,.25f,.61f,.43f) }
            "settings" -> { drawCircle(color,s*.29f,style=stroke); drawCircle(color,s*.1f,style=stroke); repeat(8) { val a=it*PI/4; drawLine(color,center+Offset(cos(a).toFloat(),sin(a).toFloat())*s*.3f,center+Offset(cos(a).toFloat(),sin(a).toFloat())*s*.43f,s*.07f) } }
            "back" -> { line(.65f,.2f,.3f,.5f); line(.3f,.5f,.65f,.8f) }
            "next" -> { line(.35f,.2f,.7f,.5f); line(.7f,.5f,.35f,.8f) }
            "add" -> { line(.5f,.18f,.5f,.82f); line(.18f,.5f,.82f,.5f) }
            "close" -> { line(.22f,.22f,.78f,.78f); line(.78f,.22f,.22f,.78f) }
            "resize" -> { line(.16f,.84f,.43f,.57f);line(.16f,.84f,.16f,.59f);line(.16f,.84f,.41f,.84f);line(.84f,.16f,.57f,.43f);line(.84f,.16f,.59f,.16f);line(.84f,.16f,.84f,.41f) }
            "edit" -> { line(.2f,.8f,.8f,.2f); line(.2f,.8f,.4f,.76f); line(.2f,.8f,.24f,.6f); line(.65f,.18f,.82f,.35f) }
            else -> repeat(3) { i -> repeat(3) { j -> drawCircle(color,s*.055f,Offset((.25f+i*.25f)*s,(.25f+j*.25f)*s)) } }
        }
    }
}
@Composable fun IconAction(name: String, description: String, enabled: Boolean=true, onClick:()->Unit) {
    IconButton(onClick,enabled=enabled,modifier=Modifier.semantics { contentDescription=description }) { Glyph(name) }
}
@Composable fun DotWordmark(modifier:Modifier=Modifier,pulseVisible:Boolean=true){BrandLogo(modifier.size(160.dp,32.dp))}
@Composable fun DotRing(progress: Double, modifier: Modifier=Modifier) {
    val muted=MaterialTheme.colorScheme.outline
    Canvas(modifier.semantics { contentDescription="Goal progress ${(progress*100).toInt()} percent" }) {
        val r=size.minDimension*.45f; val dot=(r*.025f).coerceAtLeast(1f)
        repeat(96) { i -> val a=2*PI*i/96-PI/2; drawCircle(if(i<96*progress.coerceIn(0.0,1.0)) Red else muted,dot,center+Offset(cos(a).toFloat()*r,sin(a).toFloat()*r)) }
    }
}
@Composable fun DotChart(values: List<Double?>, modifier: Modifier=Modifier, selected: Int?=null, onSelect: ((Int)->Unit)?=null, bars: Boolean=false, description: String="Recorded values",domainMin:Double=0.0,domainMax:Double?=null,xPositions:List<Float>?=null) {
    val muted=MaterialTheme.colorScheme.outline; val text=MaterialTheme.colorScheme.onSurfaceVariant
    val max=domainMax ?: (values.filterNotNull().filter{it.isFinite()}.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0)
    fun chooseIndex(x:Float,width:Float)=if(xPositions!=null)xPositions.indices.minByOrNull{kotlin.math.abs(xPositions[it]-x/width)} ?: 0 else ((x/width)*values.size).toInt().coerceIn(0,values.lastIndex)
    val input=if(onSelect==null || values.isEmpty()) Modifier else Modifier.pointerInput(values) {
        fun choose(x: Float) { onSelect(chooseIndex(x,size.width.toFloat())) }
        detectTapGestures { choose(it.x) }
    }.pointerInput(values) { detectDragGestures { change,_ -> change.consume(); onSelect(chooseIndex(change.position.x,size.width.toFloat())) } }
    Canvas(modifier.then(input).semantics { contentDescription=description; if(onSelect!=null && values.isNotEmpty()) { customActions=listOf(CustomAccessibilityAction("Next value") { onSelect(((selected ?: -1)+1).coerceAtMost(values.lastIndex)); true },CustomAccessibilityAction("Previous value") { onSelect(((selected ?: 1)-1).coerceAtLeast(0)); true }) } }) {
        val w=size.width; val h=size.height-12f; val pad=7f
        for(row in 0..3) for(x in 0..(w/14).toInt()) drawCircle(muted,.8f,Offset(x*14f,h*row/3))
        val step=(w-pad*2)/values.size.coerceAtLeast(1)
        values.forEachIndexed { i,v -> if(v!=null) {
            val x=if(xPositions!=null)pad+xPositions[i]*(w-pad*2) else pad+step*(i+.5f); val y=h-((v-domainMin)/(max-domainMin)).toFloat()*(h-8)
            val col=if(selected==i || (selected==null && i==values.lastIndex)) Red else text
            if(bars) {
                var dy=h; while(dy>=y) { drawCircle(col,min(3.8f,step*.2f),Offset(x-min(4.8f,step*.3f),dy));drawCircle(col,min(3.8f,step*.2f),Offset(x+min(4.8f,step*.3f),dy)); dy-=9f }
            } else {
                drawCircle(col,2.8f,Offset(x,y))
                if(i>0 && values[i-1]!=null) {
                    val a=Offset(if(xPositions!=null)pad+xPositions[i-1]*(w-pad*2) else x-step,h-((values[i-1]!!-domainMin)/(max-domainMin)).toFloat()*(h-8)); val b=Offset(x,y)
                    val distance=(b-a).getDistance(); val n=(distance/8).toInt().coerceAtLeast(1)
                    val tangent=b-a;val normal=Offset(-tangent.y,tangent.x)/distance.coerceAtLeast(1f)*4.2f
                    repeat(n) { j -> val point=a+(b-a)*(j.toFloat()/n);drawCircle(col,3.5f,point-normal);drawCircle(col,3.5f,point+normal) }
                }
            }
            if(selected==i) drawCircle(col,6f,Offset(x,y),style=Stroke(1.5f))
        } }
    }
}
@Composable fun StatusStrip(snapshot: HealthSnapshot, retry:(()->Unit)?=null) {
    if(LocalDemo.current) BoxWithConstraints(Modifier.fillMaxWidth()) {
        if(maxWidth<340.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale>1.3f) Column(verticalArrangement=Arrangement.spacedBy(4.dp)){Label("●  DEMO MODE",color=Red);Label("SAMPLE DATA / NOT LIVE")}
        else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) { Label("●  DEMO MODE",color=Red); Label("SAMPLE DATA / NOT LIVE") }
    }
    else Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface,RoundedCornerShape(8.dp)).padding(12.dp)) {
        Label(if(snapshot.loading) "SYNCING HEALTH CONNECT…" else snapshot.status.uppercase())
        snapshot.synced?.let { Label("Last sync: ${it.atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("d MMM, HH:mm"))}") }
        snapshot.error?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=Red) }
        if(!snapshot.loading && retry!=null) SmallAction("Refresh",onClick=retry)
    }
}
