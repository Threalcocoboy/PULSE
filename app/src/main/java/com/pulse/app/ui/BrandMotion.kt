package com.pulse.app.ui
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.*
import kotlinx.coroutines.delay
val LocalColdLaunch=staticCompositionLocalOf{false}
object LogoGeometry {
 val letters=listOf(listOf("11110","10001","10001","11110","10000","10000","10000"),listOf("10001","10001","10001","10001","10001","10001","01110"),listOf("10000","10000","10000","10000","10000","10000","11111"),listOf("01111","10000","10000","01110","00001","00001","11110"),listOf("11111","10000","10000","11110","10000","10000","11111"))
 val wave=listOf(Offset(.5f,5.5f),Offset(34.5f,5.5f),Offset(36.5f,9.5f),Offset(38.5f,.5f),Offset(40.5f,5.5f),Offset(44.5f,5.5f))
}
@Composable fun BrandLogo(modifier:Modifier=Modifier,animate:Boolean=false,finished:()->Unit={},syncing:Boolean=false){
 val motion=LocalAnimations.current;val progress=remember{Animatable(if(animate&&motion)0f else 1f)};val scale=remember{Animatable(1f)};val done=rememberUpdatedState(finished)
 LaunchedEffect(animate,motion){if(animate&&motion){repeat(2){scale.animateTo(1.025f,tween(130));scale.animateTo(1f,tween(170))};progress.animateTo(1f,tween(550,easing=LinearEasing))};done.value()}
 val phase=remember{Animatable(0f)}
 LaunchedEffect(syncing,motion){if(syncing&&motion)while(true){phase.snapTo(0f);phase.animateTo(1f,tween(1800,easing=LinearEasing))}else phase.snapTo(0f)}
 val ink=MaterialTheme.colorScheme.onBackground
 Canvas(modifier.graphicsLayer{scaleX=scale.value;scaleY=scale.value}.semantics{contentDescription="PULSE logo"}){
 val cell=minOf(size.width/45,size.height/10);val origin=Offset((size.width-45*cell)/2,(size.height-10*cell)/2)
 LogoGeometry.letters.forEachIndexed{g,rows->rows.forEachIndexed{row,line->line.forEachIndexed{col,ch->if(ch=='1')drawCircle(ink,cell*.34f,origin+Offset((g*6+col+.5f)*cell,(row+2.5f)*cell))}}}
 // E endpoint and red baseline use the same normalized grid as the wordmark.
 if(syncing){
 val anchor=origin+Offset(28.5f,5.5f)*cell
 val right=minOf(size.width-cell*.5f,origin.x+44.5f*cell)
 val width=(right-anchor.x).coerceAtLeast(0f)
 val baseline=anchor.y
 drawLine(Red,origin+Offset(.5f,5.5f)*cell,anchor,cell*.68f,StrokeCap.Round)
 val path=Path().apply{moveTo(anchor.x,baseline)}
 val amplitude=if(motion)kotlin.math.sin(phase.value*kotlin.math.PI).toFloat()*cell*2.7f else 0f
 listOf(.18f to 0f,.3f to -.2f,.4f to .6f,.51f to -1f,.64f to .5f,.77f to 0f,1f to 0f).forEach{(x,y)->path.lineTo(anchor.x+x*width,baseline+y*amplitude)}
 drawPath(path,Red,style=androidx.compose.ui.graphics.drawscope.Stroke(cell*.68f,cap=StrokeCap.Round,join=StrokeJoin.Round))
 }else{
 val segments=LogoGeometry.wave.zipWithNext();val count=segments.sumOf{(it.second-it.first).getDistance().toDouble()};var offset=0.0
 segments.forEach{(a,b)->val length=(b-a).getDistance();val n=kotlin.math.ceil(length).toInt();repeat(n){i->val fraction=i.toFloat()/n;val at=a+(b-a)*fraction;if((offset+length*fraction)/count<=progress.value)drawCircle(Red,cell*.34f,origin+at*cell)};offset+=length}
 if(progress.value>=1f)drawCircle(Red,cell*.34f,origin+LogoGeometry.wave.last()*cell)
 }
 }
}
@Composable fun BrandHeader(syncing:Boolean=false,modifier:Modifier=Modifier){
 var played by rememberSaveable{mutableStateOf(false)};val cold=LocalColdLaunch.current;val animate=remember{cold&&!played}
 LaunchedEffect(Unit){played=true};BrandLogo(modifier.widthIn(max=200.dp).height(32.dp),animate=animate,syncing=syncing)
}
@Composable fun FirstLaunchIntro(animate:Boolean,finish:()->Unit){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){BrandLogo(Modifier.fillMaxWidth().height(180.dp).padding(24.dp),animate,finish)}}
@Composable fun Modifier.reveal(index:Int=0):Modifier {val motion=LocalAnimations.current&&index<8;val p=remember{Animatable(if(motion)0f else 1f)};LaunchedEffect(Unit){if(motion){delay((index.coerceAtMost(5)*35).toLong());p.animateTo(1f,tween(220))}};return graphicsLayer{alpha=p.value;translationY=(1-p.value)*8.dp.toPx()}}
