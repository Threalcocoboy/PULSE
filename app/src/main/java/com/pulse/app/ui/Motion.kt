package com.pulse.app.ui
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.*
@Composable fun EcgSync(modifier:Modifier=Modifier,running:Boolean=true){
 val motion=LocalAnimations.current;val phase=remember{Animatable(0f)}
 LaunchedEffect(running,motion){if(running&&motion)while(true){phase.snapTo(0f);phase.animateTo(1f,tween(2400,easing=LinearEasing))}else phase.snapTo(1f)}
 val muted=MaterialTheme.colorScheme.outline
 Canvas(modifier.fillMaxWidth().height(30.dp).semantics{contentDescription=if(running)"Synchronizing health data" else "Synchronization complete"}){
 drawLine(muted,Offset(0f,size.height/2),Offset(size.width,size.height/2),1.dp.toPx())
 val p=Path();val count=240
 for(i in 0..count){val x=i.toFloat()/count;val beat=(x*3-phase.value*3+30)%1;val y=when{beat<.60f->.5f;beat<.66f->.5f-(beat-.60f)*2;beat<.70f->.38f+(beat-.66f)*8;beat<.76f->.70f-(beat-.70f)*10;beat<.83f->.10f+(beat-.76f)*10;beat<.90f->.80f-(beat-.83f)*4.2857f;else->.5f};if(i==0)p.moveTo(0f,y*size.height)else p.lineTo(x*size.width,y*size.height)}
 clipRect(right=if(motion&&running)size.width*phase.value else size.width){drawPath(p,Red,style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))}
 }
}
@Composable fun SuccessMark(){Text("✓",color=Red,style=MaterialTheme.typography.displayLarge)}
@Composable fun PulseIntro(modifier:Modifier=Modifier){BrandLogo(modifier,animate=true)}

enum class SyncVisualState{IDLE,SYNCING,SUCCESS,ERROR}
@Composable fun SyncFeedback(snapshot:com.pulse.app.core.HealthSnapshot){
 var completed by remember{mutableStateOf(false)};var previouslyLoading by remember{mutableStateOf(snapshot.loading)}
 LaunchedEffect(snapshot.loading,snapshot.error){if(previouslyLoading&&!snapshot.loading&&snapshot.error==null){completed=true;kotlinx.coroutines.delay(1800);completed=false};previouslyLoading=snapshot.loading}
 when{snapshot.loading->EcgSync();snapshot.error!=null->Text(snapshot.error,color=Red,style=MaterialTheme.typography.bodySmall);completed->Text("✓ Updated",color=Red,style=MaterialTheme.typography.labelSmall)}
}
