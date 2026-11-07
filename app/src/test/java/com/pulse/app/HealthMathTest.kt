package com.pulse.app

import com.pulse.app.core.*
import com.pulse.app.data.PreferencesCodec
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class HealthMathTest {
    private fun day(date: LocalDate,steps: Double?,complete: Boolean=true,sleep: Double?=null)=DayData(date,mapOf(Metric.STEPS to Reading(steps),Metric.SLEEP to Reading(sleep)),complete)
    @Test fun exactMedalThresholds(){val values=listOf(89999L to null,90000L to Tier.BRONZE,149999L to Tier.BRONZE,150000L to Tier.SILVER,224999L to Tier.SILVER,225000L to Tier.GOLD,299999L to Tier.GOLD,300000L to Tier.DIAMOND);values.forEach{(steps,tier)->assertEquals("$steps",tier,Achievements.tier(steps))}}
    @Test fun februaryAndLeapYearsKeepFixedThresholds(){listOf(2024,2025,2026).forEach{year->val month=YearMonth.of(year,2);val records=(1..month.lengthOfMonth()).map{day(month.atDay(it),if(it==1)90000.0 else 0.0)};val medal=Achievements.calculate(month,records,LocalDate.of(year,3,1));assertEquals(Tier.BRONZE,medal.tier);assertEquals("FINAL",medal.state)}}
    @Test fun currentMonthIsProvisional(){val date=LocalDate.of(2026,11,5);assertEquals("PROVISIONAL",Achievements.calculate(YearMonth.from(date),listOf(day(date,300000.0)),date).state)}
    @Test fun incompleteMonthNeverFinal(){assertEquals("INCOMPLETE",Achievements.calculate(YearMonth.of(2026,10),listOf(day(LocalDate.of(2026,10,1),90000.0)),LocalDate.of(2026,11,1)).state)}
    @Test fun completeCoverageWithoutProviderConfirmationIsNotFinal(){val month=YearMonth.of(2026,9);assertEquals("INCOMPLETE",Achievements.calculate(month,(1..30).map{day(month.atDay(it),10000.0,false)},LocalDate.of(2026,10,2)).state)}
    @Test fun validLateCorrectionRecalculatesTier(){val month=YearMonth.of(2026,4);val records=(1..30).map{day(month.atDay(it),3000.0)};assertEquals(Tier.BRONZE,Achievements.calculate(month,records,LocalDate.of(2026,5,1)).tier);val corrected=records+day(month.atDay(1),63000.0);assertEquals(Tier.SILVER,Achievements.calculate(month,corrected,LocalDate.of(2026,5,1)).tier)}
    @Test fun duplicatesAreReplacementsNotSummed(){val date=LocalDate.of(2026,1,1);assertEquals(90000L,Achievements.calculate(YearMonth.from(date),listOf(day(date,90000.0),day(date,90000.0)),date).total)}
    @Test fun monthBoundaryExcludesOtherMonths(){val m=YearMonth.of(2026,10);val result=Achievements.calculate(m,listOf(day(m.atDay(1),90000.0),day(m.plusMonths(1).atDay(1),300000.0)),LocalDate.of(2026,11,5));assertEquals(90000L,result.total)}
    @Test fun missingDataIsUnavailableNotZero(){val date=LocalDate.of(2026,1,1);assertNull(HistoryMath.aggregate(listOf(day(date,null)),Metric.STEPS));assertEquals(0.0,HistoryMath.aggregate(listOf(day(date,0.0)),Metric.STEPS)!!,0.0)}
    @Test fun sleepIsAnAverage(){val d=LocalDate.of(2026,1,1);assertEquals(7.0,HistoryMath.aggregate(listOf(day(d,1.0,sleep=6.0),day(d.plusDays(1),1.0,sleep=8.0)),Metric.SLEEP)!!,0.0)}
    @Test fun weeklyStepsSumAndCompare(){val end=LocalDate.of(2026,11,5);val records=(0L..13L).map{day(end.minusDays(it),if(it<7)200.0 else 100.0)};val result=HistoryMath.compare(records,end,7,Metric.STEPS);assertEquals(1400.0,result.value!!,0.0);assertEquals(700.0,result.previous!!,0.0);assertEquals(100.0,result.percent!!,0.0)}
    @Test fun partialPeriodsDoNotMakeDefinitiveComparison(){val end=LocalDate.of(2026,11,5);assertNull(HistoryMath.compare(listOf(day(end,100.0),day(end.minusDays(7),50.0)),end,7,Metric.STEPS).change)}
    @Test fun zeroPreviousHasNoPercent(){val end=LocalDate.of(2026,11,5);val result=HistoryMath.compare(listOf(day(end,10.0),day(end.minusDays(1),0.0)),end,1,Metric.STEPS);assertNull(result.percent);assertEquals(10.0,result.change!!,0.0)}
    @Test fun monthlyAggregationSeparatesCalendarMonths(){val records=listOf(day(LocalDate.of(2026,10,31),2.0),day(LocalDate.of(2026,11,1),3.0));val result=HistoryMath.monthly(records,Metric.STEPS);assertEquals(2,result.size);assertEquals(3.0,result[YearMonth.of(2026,11)]!!,0.0)}
    @Test fun timezoneDaysHandleDst(){val zone=ZoneId.of("Europe/Paris");val spring=HistoryMath.dayBounds(LocalDate.of(2026,3,29),zone);val autumn=HistoryMath.dayBounds(LocalDate.of(2026,10,25),zone);assertEquals(23,Duration.between(spring.first,spring.second).toHours());assertEquals(25,Duration.between(autumn.first,autumn.second).toHours())}
    @Test fun sleepAcrossMidnightUsesWakeDate(){val zone=ZoneId.of("Europe/Paris");val start=ZonedDateTime.of(2026,10,24,23,0,0,0,zone).toInstant();val end=ZonedDateTime.of(2026,10,25,7,0,0,0,zone).toInstant();assertEquals(LocalDate.of(2026,10,25),HistoryMath.sleepDate(end,zone));assertEquals(9.0,HistoryMath.sleepHours(listOf(start to end)),0.0)}
    @Test fun overlappingSleepIsMerged(){val start=Instant.parse("2026-11-01T22:00:00Z");assertEquals(8.0,HistoryMath.sleepHours(listOf(start to start.plusSeconds(8*3600),start.plusSeconds(3600) to start.plusSeconds(4*3600))),0.0)}
    @Test fun futureMonthIsLockedAndDateIsEngraved(){val result=Achievements.calculate(YearMonth.of(2026,12),emptyList(),LocalDate.of(2026,11,5));assertEquals("FUTURE",result.state);assertEquals("12/26",result.engraving);assertEquals(Season.WINTER,result.season)}
    @Test fun seasonalMapping(){val today=LocalDate.of(2026,11,5);listOf(2 to Season.WINTER,4 to Season.SPRING,8 to Season.SUMMER,11 to Season.AUTUMN).forEach{(m,s)->assertEquals(s,Achievements.calculate(YearMonth.of(2026,m),emptyList(),today).season)}}
    @Test fun demoIsDeterministicAndNoFutureRecords(){val date=LocalDate.of(2026,11,5);val a=DemoData.snapshot(date);val b=DemoData.snapshot(date);assertEquals(a,b);assertTrue(a.days.all{it.date<=date});assertFalse(a.days.last().complete)}
    @Test fun preferencesRoundTripAllBehavior(){val p=Preferences(true,DataMode.HEALTH_CONNECT,8000,7.5,"LIGHT",false,false,false,true);assertEquals(p,PreferencesCodec.decode(PreferencesCodec.encode(p)))}
    @Test fun preferencesRejectCorruptValues(){assertEquals(Preferences(),PreferencesCodec.decode("broken"));val p=Preferences(stepGoal=-1,sleepGoal=Double.NaN,theme="BAD").sanitized();assertEquals(100,p.stepGoal);assertEquals(8.0,p.sleepGoal,0.0);assertEquals("DARK",p.theme)}
    @Test fun goalChangeDoesNotAffectMonthlyThreshold(){val before=Preferences();val after=before.copy(stepGoal=20000);assertNotEquals(before.stepGoal,after.stepGoal);assertEquals(Tier.SILVER,Achievements.tier(150000))}
}
