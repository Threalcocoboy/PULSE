package com.pulse.app.ui
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.semantics.*
import com.pulse.app.core.*
import kotlin.math.*
@Composable fun PulseBadgeArt(item:UserAchievement,modifier:Modifier=Modifier,compact:Boolean=false){
 if(item.definition.challengeIcon!=null){
 val rewardColor=if(item.unlocked)Red else MaterialTheme.colorScheme.outline
 androidx.compose.foundation.layout.Box(modifier.semantics{contentDescription="${item.definition.name}, ${if(item.unlocked)"earned challenge reward"else "locked challenge reward"}"},contentAlignment=androidx.compose.ui.Alignment.Center){
 Canvas(androidx.compose.ui.Modifier.matchParentSize()){drawCircle(rewardColor,style=Stroke(size.minDimension*.025f))}
 Glyph(item.definition.challengeIcon,androidx.compose.ui.Modifier.then(Modifier.size(36.dp)),if(item.unlocked)Red else MaterialTheme.colorScheme.onSurfaceVariant)
 };return
 }
 if(item.period!=null){SeasonalBadge(item,modifier);return}
 val identity=item.definition.badgeIdentity();val ink=if(item.unlocked)MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant;val accent=if(item.unlocked)Red else ink
 Canvas(modifier.semantics{contentDescription="${item.definition.name}, ${if(item.unlocked)"unlocked" else "in progress"}"}){
 val side=size.minDimension;withTransform({translate((size.width-side)/2,(size.height-side)/2);scale(side/100,side/100,Offset.Zero)}){
 val stroke=Stroke(2.6f,cap=StrokeCap.Round,join=StrokeJoin.Round)
 fun line(x:Float,y:Float,a:Float,b:Float,c:Color=ink)=drawLine(c,Offset(x,y),Offset(a,b),2.6f,StrokeCap.Round)
 fun path(vararg pairs:Pair<Float,Float>,color:Color=ink){val p=Path();pairs.forEachIndexed{i,v->if(i==0)p.moveTo(v.first,v.second)else p.lineTo(v.first,v.second)};drawPath(p,color,style=stroke)}
 fun moon(){val p=Path().apply{moveTo(59f,15f);cubicTo(0f,15f,0f,72f,59f,72f);cubicTo(29f,62f,29f,25f,59f,15f)};drawPath(p,ink,style=stroke)}
 when(identity.symbol){
 "foot"->{drawOval(ink,Offset(26f,18f),Size(15f,31f),style=stroke);drawOval(accent,Offset(56f,28f),Size(15f,31f),style=stroke);drawCircle(accent,5f,Offset(34f,60f));drawCircle(ink,5f,Offset(64f,70f))}
 "moon","night"->{moon();if(identity.symbol=="night"){line(50f,68f,85f,68f,accent);line(62f,77f,85f,77f,accent)}}
 "calendar"->{path(18f to 25f,82f to 25f,82f to 75f,18f to 75f,18f to 25f);line(18f,39f,82f,39f);line(32f,17f,32f,30f);line(68f,17f,68f,30f);repeat(6){i->drawCircle(if(i%2==0)accent else ink,3f,Offset(30f+i%3*20,51f+i/3*14))}}
 "timer","clock"->{drawCircle(ink,27f,Offset(50f,48f),style=stroke);line(50f,48f,50f,30f);line(50f,48f,65f,54f,accent);line(42f,12f,58f,12f);line(50f,12f,50f,20f);drawArc(accent,-90f,210f,false,Offset(18f,16f),Size(64f,64f),style=stroke)}
 "runner"->{drawCircle(ink,6f,Offset(57f,18f));path(23f to 38f,43f to 29f,63f to 43f,81f to 43f);path(43f to 29f,40f to 50f,59f to 68f,57f to 80f);path(40f to 50f,25f to 70f,12f to 70f,color=accent)}
 "sunrise","sun"->{if(identity.symbol=="sunrise"){drawArc(ink,180f,180f,false,Offset(28f,36f),Size(44f,44f),style=stroke);line(13f,58f,87f,58f)}else drawCircle(ink,20f,Offset(50f,43f),style=stroke);repeat(7){i->val a=PI+i*PI/6;line(50+cos(a).toFloat()*29,56+sin(a).toFloat()*29,50+cos(a).toFloat()*38,56+sin(a).toFloat()*38,accent)}}
 "route"->{val p=Path().apply{moveTo(18f,70f);cubicTo(85f,70f,8f,40f,60f,40f);cubicTo(88f,40f,78f,16f,72f,16f)};drawPath(p,ink,style=stroke);drawCircle(accent,5f,Offset(18f,70f));drawCircle(accent,5f,Offset(72f,16f))}
 "stars"->{val points=listOf(20f to 54f,35f to 23f,55f to 39f,77f to 18f,84f to 60f,58f to 73f);path(*points.toTypedArray());points.forEach{drawCircle(accent,4f,Offset(it.first,it.second))}}
 "target"->{drawCircle(ink,29f,Offset(50f,44f),style=stroke);drawCircle(accent,17f,Offset(50f,44f),style=stroke);path(40f to 43f,49f to 52f,68f to 29f)}
 "metrics"->{repeat(3){i->line(18f,25f+i*23,82f,25f+i*23);drawCircle(accent,6f,Offset(33f+i*15,25f+i*23))}}
 "variety"->{drawCircle(ink,13f,Offset(27f,28f),style=stroke);path(56f to 38f,70f to 13f,84f to 38f,56f to 38f);path(22f to 71f,38f to 51f,54f to 71f,72f to 51f,color=accent)}
 "return"->{drawArc(ink,35f,290f,false,Offset(17f,13f),Size(66f,66f),style=stroke);path(65f to 17f,80f to 21f,77f to 6f,color=accent)}
 else->path(10f to 45f,29f to 45f,40f to 21f,54f to 73f,67f to 40f,78f to 45f,90f to 45f,color=accent)
 }
 if(identity.mark.isNotEmpty())drawIntoCanvas{it.nativeCanvas.drawText(identity.mark,50f,96f,android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{color=ink.toArgb();textSize=15f;typeface=android.graphics.Typeface.create("monospace",android.graphics.Typeface.BOLD);textAlign=android.graphics.Paint.Align.CENTER})}
 }}
}
