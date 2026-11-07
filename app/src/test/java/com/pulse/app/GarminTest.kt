package com.pulse.app

import com.pulse.app.core.*
import com.pulse.app.data.PreferencesCodec
import com.pulse.app.integration.garmin.*
import kotlinx.coroutines.test.runTest
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.util.Base64

class GarminTest {
    private val now=Instant.parse("2026-11-05T12:00:00Z")
    private fun jwt(exp: Instant): String="header."+Base64.getUrlEncoder().withoutPadding().encodeToString("{\"exp\":${exp.epochSecond}}".toByteArray())+".signature"
    @Test fun tokenIsRawJwtAndCached()=runTest {
        var calls=0;val api=GarminApi(GarminTransport{method,_,_,accept->calls++;if(method=="POST"){assertEquals("*/*",accept);GarminResponse(200,jwt(now.plusSeconds(7200)))}else GarminResponse(200,"[]")},{"synthetic-test-key"},{now})
        api.json("/test");api.json("/test");assertEquals(3,calls)
    }
    @Test fun expirationRenewsWithoutRefreshToken()=runTest {
        var clock=now;var posts=0;val api=GarminApi(GarminTransport{m,_,_,_->if(m=="POST"){posts++;GarminResponse(200,jwt(clock.plusSeconds(120)))}else GarminResponse(200,"{}")},{"synthetic-test-key"},{clock})
        api.json("/test");clock=clock.plusSeconds(70);api.json("/test");assertEquals(2,posts)
    }
    @Test fun unauthorizedRetriesOnlyOnce()=runTest {
        var posts=0;var gets=0;val api=GarminApi(GarminTransport{m,_,_,_->if(m=="POST"){posts++;GarminResponse(200,jwt(now.plusSeconds(7200)))}else{gets++;GarminResponse(401,"secret remote body")}},{"synthetic-test-key"},{now})
        try{api.json("/test");fail()}catch(e: GarminFailure){assertEquals(GarminError.AUTHORIZATION,e.code);assertFalse(e.toString().contains("secret remote body"))};assertEquals(2,posts);assertEquals(2,gets)
    }
    @Test fun invalidKeyNeverRequestsData()=runTest {
        var calls=0;val api=GarminApi(GarminTransport{_,_,_,_->calls++;GarminResponse(403,"private-key")},{"synthetic-test-key"},{now})
        try{api.json("/test");fail()}catch(e:GarminFailure){assertEquals(GarminError.AUTHORIZATION,e.code)};assertEquals(1,calls)
    }
    @Test fun missingKeyHasNoNetworkRequest()=runTest {
        var calls=0;val api=GarminApi(GarminTransport{_,_,_,_->calls++;GarminResponse(500)},{null},{now})
        try{api.json("/test");fail()}catch(e:GarminFailure){assertEquals(GarminError.KEY_REQUIRED,e.code)};assertEquals(0,calls)
    }
    @Test fun rateLimitPreventsImmediatePolling()=runTest {
        var calls=0;val api=GarminApi(GarminTransport{m,_,_,_->calls++;if(m=="POST")GarminResponse(200,jwt(now.plusSeconds(7200)))else GarminResponse(429,retryAfter=120)},{"synthetic-test-key"},{now})
        repeat(2){try{api.json("/test");fail()}catch(e:GarminFailure){assertEquals(GarminError.RATE_LIMIT,e.code)}};assertEquals(2,calls)
    }
    @Test fun malformedTokenDoesNotInventEnvelope()=runTest {
        val api=GarminApi(GarminTransport{_,_,_,_->GarminResponse(200,"{\"access_token\":\"invented\"}")},{"synthetic-test-key"},{now})
        try{api.json("/test");fail()}catch(e:GarminFailure){assertEquals(GarminError.MALFORMED,e.code)}
    }
    @Test fun expiredTokenIsRejected()=runTest {
        val api=GarminApi(GarminTransport{_,_,_,_->GarminResponse(200,jwt(now.minusSeconds(1)))},{"synthetic-test-key"},{now})
        try{api.json("/test");fail()}catch(e:GarminFailure){assertEquals(GarminError.MALFORMED,e.code)}
    }
    @Test fun malformedDataResponseIsNotEmptySuccess()=runTest {
        val api=GarminApi(GarminTransport{m,_,_,_->GarminResponse(200,if(m=="POST")jwt(now.plusSeconds(7200))else"bad json")},{"synthetic-test-key"},{now})
        try{api.json("/test");fail()}catch(e:GarminFailure){assertEquals(GarminError.MALFORMED,e.code)}
    }
    @Test fun diagnosticsRedactCredentialBodies(){assertFalse(GarminToken("test-secret",now).toString().contains("test-secret"));assertFalse(GarminResponse(401,"test-secret").toString().contains("test-secret"));assertFalse(GarminFailure(GarminError.AUTHORIZATION).toString().contains("test-secret"))}
    @Test fun summaryMapsDocumentedFieldsAndMissingStaysMissing(){
        val root=JSONObject("""{"data":[{"source":"garmin","calendarDate":"2026-11-05","movement":{"steps":{"value":12345,"distanceInMeters":8500}},"calories":{"burnedTotal":2300},"heartRate":{"restingValue":54},"bodyBattery":{"latestValue":62},"stress":{"avgLevel":-1},"intensityMinutes":{"moderate":20,"vigorous":10}}]}""")
        val d=GarminMapping.summaries(root,now).values.single();assertEquals(12345.0,d.value(Metric.STEPS)!!,0.0);assertEquals(8.5,d.value(Metric.DISTANCE)!!,0.0);assertEquals(54.0,d.value(Metric.RESTING_HEART)!!,0.0);assertEquals(62.0,d.value(Metric.BATTERY)!!,0.0);assertEquals(30.0,d.value(Metric.ACTIVE)!!,0.0);assertNull(d.value(Metric.STRESS));assertNull(d.value(Metric.HEART));assertFalse(d.complete)
    }
    @Test fun duplicateDaysReplaceInsteadOfAdding(){val root=JSONObject("""{"data":[{"source":"garmin","calendarDate":"2026-11-05","movement":{"steps":{"value":100}}},{"source":"garmin","calendarDate":"2026-11-05","movement":{"steps":{"value":200}}}]}""");val d=GarminMapping.summaries(root,now);assertEquals(1,d.size);assertEquals(200.0,d.values.single().value(Metric.STEPS)!!,0.0)}
    @Test fun zeroIsDifferentFromAbsent(){val d=GarminMapping.summaries(JSONObject("""{"data":[{"source":"garmin","calendarDate":"2026-11-05","movement":{"steps":{"value":0}}}]}"""),now).values.single();assertEquals(0.0,d.value(Metric.STEPS)!!,0.0);assertNull(d.value(Metric.SLEEP))}
    @Test fun emptySummaryIsValidButWrongShapeRejected(){assertTrue(GarminMapping.summaries(JSONObject("{\"data\":[]}"),now).isEmpty());try{GarminMapping.summaries(JSONArray(),now);fail()}catch(e:GarminFailure){assertEquals(GarminError.MALFORMED,e.code)}}
    @Test fun sleepStagesUseGarminCalendarDateAndSeconds(){val days=mutableMapOf<LocalDate,DayData>();GarminMapping.sleep(JSONArray("""[{"calendarDate":"2026-11-05","sleepTimeSeconds":25200,"deepSleepSeconds":3600,"lightSleepSeconds":14400,"remSleepSeconds":7200,"awakeSleepSeconds":0}]"""),days,now);val d=days.values.single();assertEquals(LocalDate.of(2026,11,5),d.date);assertEquals(7.0,d.value(Metric.SLEEP)!!,0.0);assertEquals(1.0,d.sleepStages["Deep"]!!,0.0);assertEquals(0.0,d.sleepStages["Awake"]!!,0.0)}
    @Test fun heartUsesDescriptorIndexAndDropsInvalidSamples(){val days=mutableMapOf<LocalDate,DayData>();GarminMapping.heart(JSONObject("""{"calendarDate":"2026-11-05","heartRateValueDescriptors":[{"index":0,"key":"heartrate"},{"index":1,"key":"timestamp"}],"heartRateValues":[[60,1],[80,2],[-1,3],[null,4]]}"""),days,now);assertEquals(70.0,days.values.single().value(Metric.HEART)!!,0.0)}
    @Test fun hrvUsesNightlyNotWeeklyAverage(){val days=mutableMapOf<LocalDate,DayData>();GarminMapping.hrv(JSONObject("""{"hrvSummaries":[{"calendarDate":"2026-11-05","weeklyAvg":50,"lastNightAvg":60}]}"""),days,now);assertEquals(60.0,days.values.single().value(Metric.HRV)!!,0.0)}
    @Test fun activitiesDeduplicateAndCountEmptyDates(){val d=LocalDate.of(2026,11,5);val o=JSONObject("""{"activityId":1,"activityName":"Test walk","activityType":{"typeKey":"walking"},"startTimeLocal":"2026-11-05 10:00:00","distance":2000,"duration":1200}""");val days=mutableMapOf<LocalDate,DayData>();GarminMapping.activities(listOf(o,o),days,d.minusDays(1),d,now);assertEquals(1.0,days[d]!!.value(Metric.ACTIVITIES)!!,0.0);assertEquals(0.0,days[d.minusDays(1)]!!.value(Metric.ACTIVITIES)!!,0.0);assertEquals(2.0,days[d]!!.activities.single().distanceKm!!,0.0)}
    @Test fun curatedCacheRoundTripsAndMarksOfflineStale(){val d=DayData(LocalDate.of(2026,11,5),mapOf(Metric.STEPS to Reading(100.0,DataState.READY,now)),sleepStages=mapOf("Deep" to 1.0));val raw=GarminMapping.encode(d);assertEquals(d,GarminMapping.decode(raw));assertEquals(DataState.STALE,GarminMapping.decode(raw,true).readings[Metric.STEPS]!!.state);assertFalse(raw.contains("Authorization"));assertFalse(raw.contains("apiKey"))}
    @Test fun sourceSelectionPreservesExistingPreferences(){val old=PreferencesCodec.decode("v1|true|HEALTH_CONNECT|8000|7.5|LIGHT|false|true|true|false");val new=old.copy(mode=DataMode.GARMIN);assertEquals(new,PreferencesCodec.decode(PreferencesCodec.encode(new)));assertEquals(8000,new.stepGoal);assertEquals("LIGHT",new.theme);assertEquals(DataMode.HEALTH_CONNECT,old.mode)}
}
