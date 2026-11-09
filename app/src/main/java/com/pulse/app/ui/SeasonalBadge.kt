package com.pulse.app.ui
import androidx.compose.foundation.Canvas
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.semantics.*
import com.pulse.app.core.*
import kotlin.math.*
@Composable fun SeasonalBadge(item:UserAchievement,modifier:Modifier=Modifier){
 val ink=MaterialTheme.colorScheme.onSurface;val muted=MaterialTheme.colorScheme.outline
 val progress=(item.progress/item.definition.target).toFloat().coerceIn(0f,1f)
 val season=Medal(item.period!!,null,null,"",0,null,null).season
 Canvas(modifier.semantics{contentDescription="${item.period}, ${if(item.unlocked)item.definition.name else "No tier yet"}, ${(progress*100).toInt()} percent"}){
 val side=size.minDimension
 withTransform({translate((size.width-side)/2,(size.height-side)/2);scale(side/100,side/100,Offset.Zero)}){
 val stroke=Stroke(1.8f,cap=StrokeCap.Round,join=StrokeJoin.Round)
 drawCircle(muted,31f,Offset(50f,50f),style=stroke);drawArc(Red,-90f,progress*360,false,Offset(19f,19f),Size(62f,62f),style=Stroke(3f))
 // Each ornament has a muted full silhouette and fills from its base/first segment.
 fun ornament(index:Int,count:Int,draw:DrawScope.(Color)->Unit){val fill=(progress*count-index).coerceIn(0f,1f);draw(muted);if(fill>0)clipRect(top=100f*(1-fill)){draw(Red)}}
 when(season){
 Season.SUMMER->repeat(16){i->val a=i*PI/8;val from=Offset(50+cos(a).toFloat()*38,50+sin(a).toFloat()*38);val to=Offset(50+cos(a).toFloat()*47,50+sin(a).toFloat()*47);drawLine(muted,from,to,2f);drawLine(Red,from,from+(to-from)*(progress*16-i).coerceIn(0f,1f),2f)}
 Season.SPRING->{val path=Path().apply{moveTo(50f,20f);lineTo(50f,9f);moveTo(50f,13f);cubicTo(34f,15f,34f,2f,50f,9f);moveTo(50f,10f);cubicTo(66f,11f,66f,0f,50f,7f)};drawPath(path,muted,style=stroke);clipRect(top=20*(1-progress)){drawPath(path,Red,style=stroke)}}
 Season.AUTUMN->repeat(3){i->withTransform({rotate(i*120f,Offset(50f,50f))}){val p=Path().apply{moveTo(43f,18f);cubicTo(36f,8f,49f,2f,60f,2f);cubicTo(60f,17f,50f,24f,43f,18f);close()};drawPath(p,muted,style=stroke);clipRect(top=24*(1-(progress*3-i).coerceIn(0f,1f))){drawPath(p,Red,style=stroke)};drawLine(ink,Offset(41f,23f),Offset(57f,5f),1f)}}
 Season.WINTER->repeat(3){i->val center=when(i){0->Offset(17f,18f);1->Offset(83f,24f);else->Offset(72f,86f)};repeat(6){j->val a=j*PI/3;val v=Offset(cos(a).toFloat(),sin(a).toFloat());val f=(progress*18-i*6-j).coerceIn(0f,1f);drawLine(muted,center,center+v*10f,1.6f);drawLine(Red,center,center+v*(10*f),1.6f);val tip=center+v*7f;val n=Offset(-v.y,v.x);drawLine(if(f==1f)Red else muted,tip,tip-v*3f+n*2f,1.3f);drawLine(if(f==1f)Red else muted,tip,tip-v*3f-n*2f,1.3f)}}
 }
 val tier=if(item.unlocked)Tier.valueOf(item.definition.id.removePrefix("monthly_steps_")).ordinal+1 else 0
 if(tier>=2)drawCircle(ink,26f,Offset(50f,50f),style=Stroke(.9f))
 if(tier>=3)repeat(8){i->val angle=i*PI/4;val v=Offset(cos(angle).toFloat(),sin(angle).toFloat());drawLine(ink,Offset(50f,50f)+v*34f,Offset(50f,50f)+v*37f,1.4f)}
 if(tier==4){val frame=Path().apply{moveTo(50f,10f);lineTo(90f,50f);lineTo(50f,90f);lineTo(10f,50f);close()};drawPath(frame,ink,style=Stroke(1.2f))}
 val mark=Path().apply{moveTo(30f,50f);lineTo(40f,50f);lineTo(46f,36f);lineTo(55f,64f);lineTo(62f,47f);lineTo(71f,47f)};drawPath(mark,ink,style=Stroke(2.5f))
 repeat(if(item.unlocked)Tier.valueOf(item.definition.id.removePrefix("monthly_steps_")).ordinal+1 else 0){i->drawCircle(Red,2f,Offset(41f+i*6,72f))}
 }
 }
}
