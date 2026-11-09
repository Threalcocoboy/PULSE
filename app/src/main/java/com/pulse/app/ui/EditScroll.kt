package com.pulse.app.ui
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.*
@Composable fun Modifier.editScroll(editing:Boolean,scroll:ScrollState):Modifier {
 val scope=rememberCoroutineScope();var fling by remember{mutableStateOf<Job?>(null)}
 return pointerInput(editing,scroll){if(editing)awaitEachGesture{
 awaitFirstDown(false,PointerEventPass.Initial);fling?.cancel();var multi=false;val velocity=VelocityTracker();var y=0f
 do {val event=awaitPointerEvent(PointerEventPass.Initial);val fingers=event.changes.filter{it.pressed&&it.previousPressed}
 if(event.changes.count{it.pressed}>=2){multi=true;if(fingers.size>=2){val dy=fingers.map{it.positionChange().y}.average().toFloat();scroll.dispatchRawDelta(-dy);y-=dy;velocity.addPosition(event.changes.first().uptimeMillis,androidx.compose.ui.geometry.Offset(0f,y))}}
 if(multi)event.changes.forEach{it.consume()}
 }while(event.changes.any{it.pressed})
 if(multi) {val speed=velocity.calculateVelocity().y.coerceIn(-5000f,5000f);fling=scope.launch{var previous=0f;AnimationState(initialValue=0f,initialVelocity=speed).animateDecay(exponentialDecay()){val consumed=scroll.dispatchRawDelta(value-previous);if(kotlin.math.abs(consumed-(value-previous))>1)cancelAnimation();previous=value}}}
 }}
}
