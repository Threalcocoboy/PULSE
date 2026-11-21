package com.pulse.app

import com.pulse.app.core.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class AdaptiveFlowTest {
    @Test fun emptyGrid() {assertEquals(emptyList<Widget>(),AdaptiveFlow.pack(emptyList()))}
    @Test fun defaultsDoNotOverlap() {assertTrue(AdaptiveFlow.isValid(AdaptiveFlow.defaults()))}
    @Test fun selectedPlacementChangesOrderWithoutReservingEmptyRows() {val list=AdaptiveFlow.defaults();val packed=AdaptiveFlow.pack(list,"heart",2,50);assertTrue(packed.first{it.id=="heart"}.row<50);assertTrue(AdaptiveFlow.isValid(packed));assertEquals(list.size,packed.size)}
    @Test fun expansionAtRightEdgeClamps() {val list=AdaptiveFlow.defaults().map{if(it.id=="heart")it.copy(width=4,height=3)else it};val result=AdaptiveFlow.pack(list,"heart",3,1);assertEquals(0,result.first{it.id=="heart"}.column);assertTrue(AdaptiveFlow.isValid(result))}
    @Test fun shrinkingFillsGap() {val large=Widget("a",Metric.STEPS,width=4,height=2);val b=Widget("b",Metric.SLEEP);val c=Widget("c",Metric.HEART);val before=AdaptiveFlow.pack(listOf(large,b,c));val after=AdaptiveFlow.pack(before.map{if(it.id=="a")it.copy(width=2,height=1)else it},"a",0,0);assertEquals(0,after.first{it.id=="b"}.row);assertTrue(AdaptiveFlow.isValid(after))}
    @Test fun deterministic() {val layout=AdaptiveFlow.defaults();assertEquals(AdaptiveFlow.pack(layout,"sleep",1,3),AdaptiveFlow.pack(layout,"sleep",1,3))}
    @Test(expected=IllegalArgumentException::class) fun invalidWidthRejected(){AdaptiveFlow.pack(listOf(Widget("a",Metric.STEPS,width=5)))}
    @Test(expected=IllegalArgumentException::class) fun ovalRejected(){AdaptiveFlow.pack(listOf(Widget("a",Metric.STEPS,WidgetShape.CIRCLE,2,1)))}
    @Test(expected=IllegalArgumentException::class) fun duplicateIdRejected(){AdaptiveFlow.pack(listOf(Widget("a",Metric.STEPS),Widget("a",Metric.SLEEP)))}
    @Test fun moveAcrossRowsDoesNotLoseWidgets(){val layout=AdaptiveFlow.defaults();val result=AdaptiveFlow.pack(layout,"steps",0,20);assertEquals(layout.map{it.id}.toSet(),result.map{it.id}.toSet());assertTrue(AdaptiveFlow.isValid(result))}
    @Test fun fullGridHasNoOverlap(){val result=AdaptiveFlow.pack((0..99).map{Widget("w$it",Metric.STEPS,WidgetShape.SQUARE,1,1)});assertEquals(25,result.maxOf{it.row+it.height});assertTrue(AdaptiveFlow.isValid(result))}
    @Test fun randomizedRapidMovesAndResizes(){val random=Random(42);var layout=AdaptiveFlow.pack((0..39).map{Widget("w$it",Metric.entries[it%Metric.entries.size])});repeat(1000){val w=layout[random.nextInt(layout.size)];val next=w.allowedSizes().random(random);layout=AdaptiveFlow.pack(layout.map{if(it.id==w.id)it.copy(width=next.columns,height=next.rows)else it},w.id,random.nextInt(-3,8),random.nextInt(-4,100));assertEquals(40,layout.size);assertTrue("iteration $it",AdaptiveFlow.isValid(layout))}}
    @Test fun serializedPlacementRoundTrips(){val layout=AdaptiveFlow.pack(AdaptiveFlow.defaults(),"sleep",2,8);assertEquals(layout,LayoutCodec.decode(LayoutCodec.encode(layout)))}
    @Test fun emptyDashboardPersists(){assertEquals(emptyList<Widget>(),LayoutCodec.decode(LayoutCodec.encode(emptyList())))}
    @Test fun invalidPersistenceRecovers(){
        assertEquals(AdaptiveFlow.defaults(), LayoutCodec.decode("garbage"))
        val circle = LayoutCodec.decode("v1\na|STEPS|CIRCLE|4|1|0|0|true|true|true").single()
        assertEquals("a", circle.id)
        assertEquals(circle.width, circle.height)
        val duplicate = "a|STEPS|RECTANGLE|2|2|0|0|true|true|true"
        assertEquals(Widget("a", Metric.STEPS), LayoutCodec.decode("v1\n$duplicate\n$duplicate").single())
    }
    @Test fun undoRemovalAndRedoAddition(){val initial=AdaptiveFlow.defaults();val history=LayoutHistory(initial);history.commit(initial.dropLast(1));assertEquals(initial,history.undo());assertEquals(initial.dropLast(1),history.redo());val added=AdaptiveFlow.pack(history.current+Widget("new",Metric.SLEEP));history.commit(added);history.undo();assertEquals(added,history.redo())}
    @Test fun newCommitClearsRedo(){val history=LayoutHistory(AdaptiveFlow.defaults());history.commit(emptyList());history.undo();history.commit(AdaptiveFlow.pack(listOf(Widget("x",Metric.HEART))));assertFalse(history.canRedo)}
    @Test fun historySurvivesSavedStateSerialization(){val history=LayoutHistory(AdaptiveFlow.defaults());history.commit(emptyList());val restored=LayoutHistory(history.current);restored.restore(history.savedUndo(),history.savedRedo());assertEquals(AdaptiveFlow.defaults(),restored.undo())}
    @Test fun interruptedPreviewDoesNotMutateCommittedLayout(){val original=AdaptiveFlow.defaults();AdaptiveFlow.pack(original,"sleep",0,20);assertEquals(AdaptiveFlow.defaults(),original)}
}
