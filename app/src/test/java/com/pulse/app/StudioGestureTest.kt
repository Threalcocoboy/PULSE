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
    @Test fun circleCanGrowVerticallyAtScreenEdge(){val circle=widget.copy(shape=WidgetShape.CIRCLE);assertEquals(Footprint(3,3),StudioGestureRules.resize(circle,2.0f,3.1f,Footprint(2,2)));assertEquals(Footprint(4,4),StudioGestureRules.resize(circle,2.0f,3.8f,Footprint(2,2)))}
    @Test fun rightEdgeExpansionStaysInFourColumns(){val next=StudioGestureRules.resize(widget,4f,3f,Footprint(2,2));val layout=AdaptiveFlow.pack(listOf(widget.copy(width=next.columns,height=next.rows)),widget.id,3,0);assertTrue(AdaptiveFlow.isValid(layout));assertEquals(0,layout.single().column)}
    @Test fun cancelPreviewLeavesOriginalAndUndoRestoresResize(){val original=AdaptiveFlow.pack(listOf(widget));val history=LayoutHistory(original);val preview=AdaptiveFlow.pack(listOf(widget.copy(width=4,height=3)));assertEquals(original,history.current);history.commit(preview);assertEquals(original,history.undo());assertEquals(preview,history.redo())}

    @Test fun liveResizeCalculatesContinuousDimensions() {
        val stepX = 100f
        val stepY = 100f
        val gap = 8f
        // Dragging outward by 50px
        val live = StudioGestureRules.calculateLive(widget, 50f, 50f, stepX, stepY, gap, Footprint(2, 2))
        val baseW = 2 * stepX - gap
        assertEquals(baseW + 50f, live.widthPx, 0.1f)
        assertEquals(baseW + 50f, live.heightPx, 0.1f)
    }

    @Test fun liveResizeCircleMaintainsSquareLiveDimensions() {
        val circle = widget.copy(shape = WidgetShape.CIRCLE)
        val stepX = 100f
        val stepY = 100f
        val gap = 8f
        // Dragging horizontally more than vertically
        val live = StudioGestureRules.calculateLive(circle, 120f, 40f, stepX, stepY, gap, Footprint(2, 2))
        assertEquals(live.widthPx, live.heightPx, 0.001f)
    }

    @Test fun liveResizePillLocksRowHeight() {
        val pill = widget.copy(shape = WidgetShape.PILL, width = 2, height = 1)
        val stepX = 100f
        val stepY = 100f
        val gap = 8f
        val live = StudioGestureRules.calculateLive(pill, 80f, 150f, stepX, stepY, gap, Footprint(2, 1))
        val baseH = 1 * stepY - gap
        assertEquals(baseH, live.heightPx, 0.001f)
        assertTrue(live.widthPx > 2 * stepX - gap)
    }

    @Test fun liveResizeBannerLocksFullWidth() {
        val banner = widget.copy(shape = WidgetShape.BANNER, width = 4, height = 1)
        val stepX = 100f
        val stepY = 100f
        val gap = 8f
        val live = StudioGestureRules.calculateLive(banner, -50f, 60f, stepX, stepY, gap, Footprint(4, 1))
        val baseW = 4 * stepX - gap
        assertEquals(baseW, live.widthPx, 0.001f)
        assertTrue(live.heightPx > 1 * stepY - gap)
    }
}
