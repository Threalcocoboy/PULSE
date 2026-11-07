package com.pulse.app.ui

import android.graphics.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.pulse.app.core.*
import com.pulse.app.data.ShareExport
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.*

@Composable fun AchievementsScreen(snapshot: HealthSnapshot,detail:(YearMonth)->Unit) {
    var year by rememberSaveable {mutableIntStateOf(LocalDate.now().year)}
    val medals=remember(snapshot.days,year){(1..12).map {Achievements.calculate(YearMonth.of(year,it),snapshot.days,LocalDate.now())}}
    val earned=medals.filter {it.tier!=null && it.state=="FINAL"}
    val best=medals.filter {it.total!=null}.maxByOrNull {it.total!!}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PulseTokens.screenPadding),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        SectionTitle("Collection","Monthly steps")
        if(LocalDemo.current)Label("Demo collection")
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){IconAction("back","Previous year"){year--};Text(year.toString(),fontSize=22.sp,fontWeight=FontWeight.Light);IconAction("next","Next year",year<LocalDate.now().year){year++}}
        Row(Modifier.fillMaxWidth().padding(vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)){Label("Collected");Text("${earned.size} / 12",fontSize=22.sp)}
            Column(Modifier.weight(1f)){Label("Diamond");Text(earned.count {it.tier==Tier.DIAMOND}.toString(),fontSize=22.sp)}
            Column(Modifier.weight(1.3f)){Label("Best month");Text(best?.month?.format(DateTimeFormatter.ofPattern("MMM yyyy")) ?: "—",style=MaterialTheme.typography.titleMedium);Label(number(best?.total?.toDouble()))}
        }
        repeat(4){row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){repeat(3){col->val medal=medals[row*3+col]
            Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable{detail(medal.month)}.padding(vertical=4.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)) {
                MedalArt(medal,Modifier.fillMaxWidth().aspectRatio(1f))
                Text(medal.month.month.name.take(3).lowercase().replaceFirstChar(Char::titlecase),style=MaterialTheme.typography.bodySmall)
                Text(when {medal.state=="FUTURE"->"Locked";medal.state=="INCOMPLETE"->"Incomplete";medal.state=="PROVISIONAL"->"In progress";medal.tier==null->"Unearned";else->medal.tier.name.lowercase().replaceFirstChar(Char::titlecase)},fontSize=11.sp,color=if(medal.state=="PROVISIONAL")Red else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }}}
        HorizontalDivider(color=MaterialTheme.colorScheme.outline)
        Text("Collected counts finalized medals only. Current and incomplete months are shown separately and may change with late records.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Bronze 90k · Silver 150k · Gold 225k · Diamond 300k",style=MaterialTheme.typography.bodySmall)
        val reached=snapshot.days.count {(it.value(Metric.STEPS) ?: 0.0)>=5530}
        if(reached>0)Label("$reached recorded days with at least 5,530 steps")
        Spacer(Modifier.height(12.dp))
    }
}
@Composable fun MedalArt(medal: Medal,modifier: Modifier=Modifier) {
    Canvas(modifier.semantics {contentDescription="${medal.month}, ${medal.tier?.name ?: "no medal"}, ${medal.state}, ${medal.total ?: "no recorded"} steps"}) {drawIntoCanvas {canvas->
        val n=canvas.nativeCanvas;val save=n.save();val side=size.minDimension;n.translate((size.width-side)/2,(size.height-side)/2);n.scale(side/400,side/400);drawMedal(n,medal,side<160.dp.toPx());n.restoreToCount(save)
    }}
}
fun drawMedal(c: android.graphics.Canvas,m: Medal,compact: Boolean=false) {
    val tone=when(m.tier){Tier.BRONZE->0xFFC89C79.toInt();Tier.SILVER->0xFFC5C9CD.toInt();Tier.GOLD->0xFFD7C18A.toInt();Tier.DIAMOND->0xFFD4E5E9.toInt();else->0xFF8F9397.toInt()}
    val p=Paint(Paint.ANTI_ALIAS_FLAG)
    p.color=if(m.tier==null)0xFF202224.toInt()else tone
    c.drawCircle(200f,200f,178f,p)
    p.style=Paint.Style.STROKE;p.strokeWidth=2.5f;p.color=tone
    if(m.state=="INCOMPLETE")p.pathEffect=DashPathEffect(floatArrayOf(8f,8f),0f)
    c.drawCircle(200f,200f,178f,p);p.pathEffect=null
    val ink=if(m.tier==null)0xFFD2D4D6.toInt()else 0xFF252624.toInt()
    p.color=ink;p.strokeWidth=3.5f;p.strokeCap=Paint.Cap.ROUND
    when(m.season){
        Season.WINTER->repeat(6){i->val a=i*PI/3;c.drawLine(200f,110f,200+cos(a).toFloat()*19,110+sin(a).toFloat()*19,p)}
        Season.SUMMER->{c.drawCircle(200f,110f,10f,p);repeat(8){i->val a=i*PI/4;c.drawLine(200+cos(a).toFloat()*16,110+sin(a).toFloat()*16,200+cos(a).toFloat()*22,110+sin(a).toFloat()*22,p)}}
        Season.SPRING->{val path=Path().apply{moveTo(200f,132f);lineTo(200f,106f);cubicTo(176f,108f,178f,90f,198f,100f);moveTo(201f,116f);cubicTo(226f,111f,220f,91f,202f,108f)};c.drawPath(path,p)}
        Season.AUTUMN->{val path=Path().apply{moveTo(187f,132f);lineTo(211f,93f);quadTo(235f,131f,196f,128f);lineTo(187f,132f)};c.drawPath(path,p)}
    }
    p.style=Paint.Style.FILL;p.typeface=Typeface.create("sans-serif",Typeface.NORMAL);p.textAlign=Paint.Align.CENTER;p.textSize=78f
    c.drawText(m.engraving,200f,226f,p)
    if(!compact){p.textSize=23f;c.drawText(m.tier?.name?.lowercase()?.replaceFirstChar(Char::titlecase) ?: if(m.state=="FUTURE")"Locked"else"No medal",200f,272f,p)}
    repeat(7){i->c.drawCircle(152f+i*16f,310f,2.4f,p)}
    if(m.state=="PROVISIONAL"){p.color=0xFFFF3535.toInt();c.drawCircle(325f,74f,9f,p)}
}
@Composable fun MedalScreen(month: YearMonth,snapshot: HealthSnapshot,back:()->Unit) {
    val medal=remember(month,snapshot.days){Achievements.calculate(month,snapshot.days,LocalDate.now())};val context=LocalContext.current;val demo=LocalDemo.current
    val days=(1..month.lengthOfMonth()).map {d->snapshot.days.firstOrNull {it.date==month.atDay(d)}?.value(Metric.STEPS)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){IconAction("back","Back",onClick=back);Label(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")).uppercase())}
        if(demo) Label("DEMO COLLECTION / SAMPLE DATA",color=Red)
        MedalArt(medal,Modifier.fillMaxWidth(.84f).aspectRatio(1f))
        Text(medal.tier?.name ?: "NO MEDAL",fontSize=28.sp,fontWeight=FontWeight.Normal)
        Label("MONTHLY STEP BADGE · ${medal.state}",color=if(medal.state=="PROVISIONAL")Red else MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${number(medal.total?.toDouble())} TOTAL STEPS",style=MaterialTheme.typography.titleLarge)
        if(medal.next!=null && medal.state=="PROVISIONAL") {val next=medal.next!!;DotChart(days,Modifier.fillMaxWidth().height(70.dp),bars=true,description="Recorded daily steps in $month");Label("${number((next.threshold-(medal.total ?: 0)).toDouble())} TO ${next.name}")}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceAround){Column(horizontalAlignment=Alignment.CenterHorizontally){Label("DAILY AVERAGE");Text(number(medal.average),fontSize=22.sp)};Column(horizontalAlignment=Alignment.CenterHorizontally){Label("MOST ACTIVE DAY");Text(number(medal.best?.toDouble()),fontSize=22.sp)}}
        DotChart(days,Modifier.fillMaxWidth().height(140.dp),bars=true,description="${medal.days} recorded days. ${number(medal.total?.toDouble())} total steps in $month")
        Label("${medal.days} / ${month.lengthOfMonth()} DAYS RECORDED")
        if(medal.state=="INCOMPLETE") Text("The source has not confirmed complete monthly coverage. This result may change when late records arrive.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        PulseButton("Share medal",Modifier.fillMaxWidth(),enabled=medal.tier!=null){ShareExport.medal(context,medal,demo)}
        Spacer(Modifier.height(20.dp))
    }
}
