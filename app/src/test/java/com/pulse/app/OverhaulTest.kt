package com.pulse.app
import com.pulse.app.core.*
import com.pulse.app.data.PreferencesCodec
import com.pulse.app.ui.LogoGeometry
import org.junit.Assert.*
import org.junit.Test
import java.time.*
class OverhaulTest {
 @Test fun everyAchievementHasDistinctRelevantIdentity(){val all=AchievementRegistry.all.map{it.badgeIdentity()};assertEquals(36,all.size);assertEquals(36,all.distinct().size);assertEquals("sunrise",AchievementRegistry.all.first{it.id=="early_bird"}.badgeIdentity().symbol)}
 @Test fun axisContainsAllHeartObservations(){val v=listOf(83.0,90.0,103.0);val a=AxisMath.scale(v,Metric.HEART);assertTrue(a.minimum<=83);assertTrue(a.maximum>=103);assertTrue(a.maximum>a.minimum);assertTrue(a.ticks.zipWithNext().all{it.first<it.second})}
 @Test fun emptyAndFlatAxesNeverHaveZeroRange(){listOf(emptyList(),listOf(0.0),listOf(85.0,85.0)).forEach{v->val a=AxisMath.scale(v,Metric.HEART);assertTrue(a.maximum>a.minimum);assertTrue(a.maximum.isFinite())}}
 @Test fun countAndScoreAxesUseAppropriateZero(){assertEquals(0.0,AxisMath.scale(listOf(5000.0,8000.0),Metric.STEPS).minimum,0.0);assertEquals(100.0,AxisMath.scale(listOf(30.0),Metric.BATTERY).maximum,0.0);assertEquals("2k",AxisMath.label(2000.0,Metric.STEPS));assertEquals("1h 30m",AxisMath.label(1.5,Metric.SLEEP))}
 @Test fun outlierIsContainedWithoutChangingInput(){val data=listOf(50.0,null,100000.0);val axis=AxisMath.scale(data,Metric.HRV);assertTrue(axis.maximum>=100000);assertEquals(null,data[1]);assertEquals(1.0,axis.fraction(axis.maximum),0.0)}
 @Test fun localNameRoundTripsWithoutCorruptingDelimiter(){val p=Preferences(displayName="Alex | Zoë",introSeen=true,onboardingStep=4,progressNotifications=true,notificationMetrics=setOf(Metric.STEPS));assertEquals(p,PreferencesCodec.decode(PreferencesCodec.encode(p)));assertEquals("",PreferencesCodec.decode(PreferencesCodec.encode(p.copy(displayName=""))).displayName)}
 @Test fun priorPreferencesKeepTheirGoals(){val p=Preferences(stepGoal=12300);val raw=PreferencesCodec.encode(p).split('|').take(14).joinToString("|");val migrated=PreferencesCodec.decode(raw);assertEquals(p.stepGoal,migrated.stepGoal);assertFalse(migrated.progressNotifications);assertEquals("",migrated.displayName)}
 @Test fun logoUsesOneSharedBaseline(){assertTrue(LogoGeometry.wave.take(2).all{it.y==5.5f});assertEquals(5.5f,LogoGeometry.wave.last().y,0f);assertEquals(5,LogoGeometry.letters.size)}
 @Test fun rawHeartSamplesMapAcrossLocalMidnightWithoutInventingDays(){val t=Instant.parse("2026-11-07T23:30:00Z");val samples=listOf(HeartSample(t,60.0,"a"),HeartSample(t,60.0,"a"),HeartSample(t.plusSeconds(60),80.0,"a"));val days=HeartHistory.daily(samples,ZoneId.of("Europe/Paris"),LocalDate.of(2026,11,7),LocalDate.of(2026,11,8));assertEquals(1,days.size);assertEquals(70.0,days[LocalDate.of(2026,11,8)]!!,0.0)}
 @Test fun largeSamplePlotKeepsRealExtremaAndTimes(){val points=(0..9999).map{HeartSample(Instant.ofEpochSecond(it.toLong()),if(it==5000)250.0 else 60.0+it%30,"source")};val plotted=HistorySamples.plot(points);assertTrue(plotted.size<=600);assertTrue(plotted.all{it in points});assertEquals(250.0,plotted.maxOf{it.bpm},0.0);assertEquals(points.first(),plotted.first());assertEquals(points.last(),plotted.last())}

 @Test fun notificationOptOutAndEmptySelectionPersist(){val p=Preferences(progressNotifications=false,notificationMetrics=emptySet());assertEquals(p,PreferencesCodec.decode(PreferencesCodec.encode(p)))}
 @Test fun namesAreBoundedAndControlCharactersRemoved(){val p=Preferences(displayName="  Alex\n  ").sanitized();assertEquals("Alex",p.displayName);assertEquals(40,Preferences(displayName="a".repeat(100)).sanitized().displayName.length)}

 @Test fun failedAttemptsAreThrottledAndActiveReadsNotDuplicated(){val now=Instant.parse("2026-11-08T12:00:00Z");assertFalse(RefreshPolicy.allow(now.minusSeconds(10),false,now));assertTrue(RefreshPolicy.allow(now.minusSeconds(60),false,now));assertFalse(RefreshPolicy.allow(null,true,now))}
 @Test fun agedRowsOutsideHistoryWindowDoNotCauseRepeatedFullReads(){val today=LocalDate.of(2026,11,8);val now=Instant.parse("2026-11-08T12:00:00Z");val rows=listOf(today.minusDays(500) to now.minusSeconds(86400*100),today.minusDays(365) to now.minusSeconds(86400));assertFalse(RefreshPolicy.needsHistory(rows,today,now));assertTrue(RefreshPolicy.needsHistory(rows.map{it.first to now.minusSeconds(86400*8)},today,now))}

}
