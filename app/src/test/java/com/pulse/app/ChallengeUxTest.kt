package com.pulse.app

import androidx.compose.ui.geometry.Offset
import com.pulse.app.core.*
import com.pulse.app.data.ChallengeCodec
import com.pulse.app.ui.LogoGeometry
import com.pulse.app.ui.parseChallengeNumber
import com.pulse.app.ui.calendarMonthDays
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.YearMonth
import java.util.Locale

class ChallengeUxTest {
    @Test fun logoWaveJoinsTheLastEDotAndFitsTheCanvas() {
        val g = LogoGeometry
        assertEquals(g.letterDot(4, 3, 3), g.anchor)
        assertEquals(g.baseline.last(), g.wave[1])
        assertEquals(g.anchor.y, g.wave.last().y)
        assertTrue(g.wave.zipWithNext().all { (a, b) -> a.x < b.x })
        for ((width, height) in listOf(160f to 32f, 420f to 84f, 106f to 76f, 600f to 40f)) {
            val cell = g.cell(width, height)
            val origin = Offset((width - g.width * cell) / 2, (height - g.height * cell) / 2)
            for (point in g.wave + g.baseline) {
                val scaled = origin + point * cell
                val radius = g.dotRadius * cell
                assertTrue(scaled.x - radius >= 0 && scaled.x + radius <= width)
                assertTrue(scaled.y - radius >= 0 && scaled.y + radius <= height)
            }
        }
    }

    @Test fun localizedDecimalsAndCalendarDatesRoundTrip() {
        assertEquals(12.5, parseChallengeNumber("12,5", Locale.FRANCE)!!, 0.0)
        assertEquals(12.5, parseChallengeNumber("12.5", Locale.FRANCE)!!, 0.0)
        assertEquals(12.5, parseChallengeNumber("12.5", Locale.US)!!, 0.0)
        listOf("0", "-1", "12,5x", "1e6", "NaN", "1,000").forEach {
            assertNull(parseChallengeNumber(it, Locale.US))
        }
        val month = YearMonth.of(2026, 3)
        val days = calendarMonthDays(month, DayOfWeek.MONDAY)
        assertEquals(6, days.indexOfFirst { it != null })
        assertEquals(month.atEndOfMonth(), days.filterNotNull().last())
        val selected = LocalDate.of(2026, 3, 29)
        assertTrue(selected in days)
        val challenge = PersonalChallenge(name = "Walk", start = selected)
        assertEquals(selected, ChallengeCodec.decode(ChallengeCodec.encode(listOf(challenge))).single().start)
    }

    @Test fun previewAndExistingRewardKeepTheSameRuleAndProgress() {
        val day = LocalDate.of(2026, 11, 12)
        val saved = PersonalChallenge(name = "Read", tracking = ChallengeTracking.CHECKBOX,
            target = 1.0, start = day, events = listOf(ChallengeEvent(date = day, amount = 1.0)))
        val completed = ChallengeEvaluator.evaluate(saved, HealthSnapshot(), day)
        assertEquals("Check off 1 time within 7 days.", completed.summary())
        assertTrue(completed.reward().unlocked)
        val archived = completed.copy(status = ChallengeStatus.ARCHIVED, rewardAcknowledged = true)
        val restored = ChallengeCodec.decode(ChallengeCodec.encode(listOf(archived))).single()
        assertEquals(archived, restored)
        assertEquals(completed.reward().definition.id, restored.reward().definition.id)
        assertEquals(archived, ChallengeEvaluator.evaluate(restored, HealthSnapshot(), day.plusDays(8)))
        assertTrue(saved.copy(rule = ChallengeRule.FLEXIBLE).summary().contains("5 days out of 7 days"))
        assertTrue(saved.copy(rule = ChallengeRule.STREAK).summary().contains("5 days in a row"))
        assertTrue(saved.copy(rule = ChallengeRule.ONE_TIME).summary().contains("a single day"))
    }
}
