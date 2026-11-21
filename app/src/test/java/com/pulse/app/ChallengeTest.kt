package com.pulse.app

import com.pulse.app.core.*
import com.pulse.app.data.ChallengeCodec
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ChallengeTest {
    private val today=LocalDate.of(2026,11,8)
    private fun manual()=PersonalChallenge(name="Read",tracking=ChallengeTracking.NUMERIC,target=30.0,start=today,duration=7,unit="minutes")
    private fun snapshot(vararg values:Pair<LocalDate,Double?>)=HealthSnapshot(mode=DataMode.HEALTH_CONNECT,days=values.map{(date,value)->DayData(date,mapOf(Metric.STEPS to Reading(value,if(value==null)DataState.EMPTY else DataState.READY)),complete=true)})
    @Test fun manualEventsRoundTripAndRewardIsPermanent(){
        val c=manual().copy(events=listOf(ChallengeEvent("a",today,10.0),ChallengeEvent("b",today,20.0)))
        val result=ChallengeEvaluator.evaluate(c,HealthSnapshot(),today)
        assertEquals(ChallengeStatus.COMPLETED,result.status)
        assertEquals(result,ChallengeCodec.decode(ChallengeCodec.encode(listOf(result))).single())
        assertEquals(result,ChallengeEvaluator.evaluate(result.copy(events=emptyList()),HealthSnapshot(),today).copy(events=result.events))
        assertEquals(result.reward().instanceId,ChallengeEvaluator.evaluate(result,HealthSnapshot(),today.plusDays(1)).reward().instanceId)
    }
    @Test fun duplicateEventsAndDaysDoNotDoubleCount(){
        val event=ChallengeEvent("a",today,10.0)
        assertEquals(10.0,ChallengeEvaluator.evaluate(manual().copy(events=listOf(event,event)),HealthSnapshot(),today).progress,0.0)
        val c=manual().copy(tracking=ChallengeTracking.AUTOMATIC,target=100.0)
        assertEquals(60.0,ChallengeEvaluator.evaluate(c,snapshot(today to 60.0,today to 60.0),today).progress,0.0)
    }
    @Test fun missingAndDisconnectedNeverCompleteOrExpire(){
        val c=manual().copy(tracking=ChallengeTracking.AUTOMATIC,duration=1)
        assertEquals(ChallengeStatus.AWAITING_DATA,ChallengeEvaluator.evaluate(c,snapshot(today to null),today.plusDays(2)).status)
        assertEquals(ChallengeStatus.AWAITING_DATA,ChallengeEvaluator.evaluate(c,snapshot(today to 100.0).copy(mode=DataMode.NONE),today).status)
        assertEquals(ChallengeStatus.EXPIRED,ChallengeEvaluator.evaluate(c,snapshot(today to 10.0),today.plusDays(1)).status)
        assertEquals(ChallengeStatus.COMPLETED,ChallengeEvaluator.evaluate(c,snapshot(today to 40.0),today.plusDays(2)).status)
    }
    @Test fun flexibleStreakOneTimeAndScheduling(){
        val c=manual().copy(tracking=ChallengeTracking.AUTOMATIC,start=today.minusDays(2),target=10.0,requiredDays=2,rule=ChallengeRule.FLEXIBLE)
        val s=snapshot(today.minusDays(2) to 15.0,today.minusDays(1) to null,today to 20.0)
        assertEquals(ChallengeStatus.COMPLETED,ChallengeEvaluator.evaluate(c,s,today).status)
        assertEquals(1.0,ChallengeEvaluator.evaluate(c.copy(rule=ChallengeRule.STREAK),s,today).progress,0.0)
        assertEquals(20.0,ChallengeEvaluator.evaluate(c.copy(rule=ChallengeRule.ONE_TIME),s,today).progress,0.0)
        assertEquals(ChallengeStatus.SCHEDULED,ChallengeEvaluator.evaluate(c.copy(start=today.plusDays(1)),s,today).status)
        assertEquals(ChallengeStatus.PAUSED,ChallengeEvaluator.evaluate(c.copy(status=ChallengeStatus.PAUSED),s,today).status)
    }
    @Test fun validatesRules(){
        assertNotNull(manual().copy(name="").validation())
        assertNotNull(manual().copy(target=Double.NaN).validation())
        assertNotNull(manual().copy(duration=0).validation())
        assertNotNull(manual().copy(rule=ChallengeRule.STREAK,requiredDays=8).validation())
        assertNotNull(manual().copy(tracking=ChallengeTracking.AUTOMATIC,metric=Metric.SLEEP).validation())
        assertNull(manual().validation())
    }
}
