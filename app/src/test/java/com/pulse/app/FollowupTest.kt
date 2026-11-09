package com.pulse.app
import com.pulse.app.core.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*
class FollowupTest {
 @Test fun familyCountsMonthsInsteadOfTierCrossings(){val t=LocalDate.of(2026,11,7);val s=HealthSnapshot(listOf(DayData(t,mapOf(Metric.STEPS to Reading(310000.0,DataState.READY))),DayData(t.minusMonths(1),mapOf(Metric.STEPS to Reading(100000.0,DataState.READY)))));val all=AchievementCollection.monthly(s,YearMonth.from(t),t)+AchievementCollection.monthly(s,YearMonth.from(t).minusMonths(1),t);assertEquals(2,MonthlyBadges.familyCount(all+all));assertEquals("monthly_steps_DIAMOND",MonthlyBadges.main(all.take(4)).definition.id)}
 @Test fun monthAttemptsResetButPriorAwardsSurvive(){val t=LocalDate.of(2026,11,7);val old=AchievementCollection.monthly(HealthSnapshot(),YearMonth.of(2026,10),t).map{it.copy(unlocked=true,progress=300000.0)};val current=MonthlyBadges.items(HealthSnapshot(),old,YearMonth.from(t),t);assertTrue(current.all{it.progress==0.0&&!it.unlocked});assertTrue(MonthlyBadges.items(HealthSnapshot(),old,YearMonth.of(2026,10),t).all{it.unlocked});assertEquals(AchievementRepeat.STEP_FAMILY,old.first().repeat)}
 @Test fun newWidgetIsPackedAtTopWithoutOverlap(){val old=AdaptiveFlow.defaults();val next=AdaptiveFlow.pack(listOf(Widget("new",Metric.DISTANCE))+old);assertEquals("new",next.first().id);assertEquals(0,next.first().row);assertTrue(AdaptiveFlow.isValid(next));assertEquals(old.size+1,next.size)}
}
