package com.pulse.app

import com.pulse.app.core.*
import org.junit.Assert.*
import org.junit.Test

class StudioGestureTest {
    private val widget=Widget("test",Metric.HEART,width=2,height=2)
    @Test fun onlySelectedEditingCornerResizes(){assertTrue(StudioGestureRules.resizeHit(true,true,180f,180f,200f,200f,48f));assertFalse(StudioGestureRules.resizeHit(false,true,180f,180f,200f,200f,48f));assertFalse(StudioGestureRules.resizeHit(true,false,180f,180f,200f,200f,48f));assertFalse(StudioGestureRules.resizeHit(true,true,100f,100f,200f,200f,48f))}
    @Test fun outwardIncreasesAndInwardDecreases(){assertEquals(Footprint(4,2),StudioGestureRules.resize(widget,4f,2f,Footprint(2,2)));assertEquals(Footprint(2,1),StudioGestureRules.resize(widget,2f,1f,Footprint(2,2)))}
    @Test fun boundaryHysteresisPreventsOscillation(){val current=Footprint(2,2);assertEquals(current,StudioGestureRules.resize(widget,3.05f,2f,current));assertEquals(current,StudioGestureRules.resize(widget,2.95f,2f,current))}
    @Test fun circleRemainsSquare(){val circle=widget.copy(shape=WidgetShape.CIRCLE);for(w in listOf(0f,2.5f,4f,8f)){val s=StudioGestureRules.resize(circle,w,3.8f,Footprint(2,2));assertEquals(s.columns,s.rows)}}
    @Test fun circleCanGrowVerticallyAtScreenEdge(){val circle=widget.copy(shape=WidgetShape.CIRCLE);assertEquals(Footprint(4,4),StudioGestureRules.resize(circle,2.0f,3.3f,Footprint(2,2)))}
    @Test fun rightEdgeExpansionStaysInFourColumns(){val next=StudioGestureRules.resize(widget,4f,3f,Footprint(2,2));val layout=AdaptiveFlow.pack(listOf(widget.copy(width=next.columns,height=next.rows)),widget.id,3,0);assertTrue(AdaptiveFlow.isValid(layout));assertEquals(0,layout.single().column)}
    @Test fun cancelPreviewLeavesOriginalAndUndoRestoresResize(){val original=AdaptiveFlow.pack(listOf(widget));val history=LayoutHistory(original);val preview=AdaptiveFlow.pack(listOf(widget.copy(width=4,height=3)));assertEquals(original,history.current);history.commit(preview);assertEquals(original,history.undo());assertEquals(preview,history.redo())}
}
