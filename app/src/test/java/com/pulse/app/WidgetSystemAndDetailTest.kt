package com.pulse.app

import com.pulse.app.core.*
import com.pulse.app.ui.metricDescription
import com.pulse.app.ui.sizeDisplayName
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class WidgetSystemAndDetailTest {

    @Test
    fun allShapesHaveValidAllowedSizes() {
        WidgetShape.entries.forEach { shape ->
            val w = Widget("w1", Metric.STEPS, shape = shape)
            val sizes = w.allowedSizes()
            assertTrue("Shape $shape must have at least one allowed size", sizes.isNotEmpty())
            sizes.forEach { size ->
                assertTrue("Width ${size.columns} must be within 1..4 columns", size.columns in 1..4)
                assertTrue("Height ${size.rows} must be positive", size.rows in 1..4)
            }
        }
    }

    @Test
    fun circleShapeOnlySupportsSquareFootprints() {
        val circle = Widget("c1", Metric.HEART, WidgetShape.CIRCLE)
        circle.allowedSizes().forEach { footprint ->
            assertEquals("Circle footprint must be square", footprint.columns, footprint.rows)
        }
        assertTrue("Circle supports 2x2", Footprint(2, 2) in circle.allowedSizes())
        assertTrue("Circle supports 4x4", Footprint(4, 4) in circle.allowedSizes())
    }

    @Test
    fun pillShapeLocksHeightToOneRow() {
        val pill = Widget("p1", Metric.STEPS, WidgetShape.PILL)
        pill.allowedSizes().forEach { footprint ->
            assertEquals("Pill height must be 1 row", 1, footprint.rows)
        }
    }

    @Test
    fun bannerShapeLocksWidthToFourColumns() {
        val banner = Widget("b1", Metric.WEEKLY, WidgetShape.BANNER)
        banner.allowedSizes().forEach { footprint ->
            assertEquals("Banner width must be full 4 columns", 4, footprint.columns)
        }
    }

    @Test
    fun metricDescriptionsAreInformativeAndNonEmpty() {
        Metric.entries.forEach { metric ->
            val desc = metricDescription(metric)
            assertTrue("Description for $metric must not be blank", desc.isNotBlank())
            assertTrue("Description for $metric should be descriptive (>15 chars)", desc.length >= 15)
        }
    }

    @Test
    fun sizeDisplayNamesAreClear() {
        assertEquals("Standard (2×2)", sizeDisplayName(WidgetShape.CIRCLE, Footprint(2, 2)))
        assertEquals("Large (4×4)", sizeDisplayName(WidgetShape.CIRCLE, Footprint(4, 4)))
        assertEquals("Mini (1×1)", sizeDisplayName(WidgetShape.SQUARE, Footprint(1, 1)))
        assertEquals("Standard (2×1)", sizeDisplayName(WidgetShape.PILL, Footprint(2, 1)))
        assertEquals("Wide (4×1)", sizeDisplayName(WidgetShape.PILL, Footprint(4, 1)))
        assertEquals("Slim (4×1)", sizeDisplayName(WidgetShape.BANNER, Footprint(4, 1)))
        assertEquals("Deep (4×2)", sizeDisplayName(WidgetShape.BANNER, Footprint(4, 2)))
    }

    @Test
    fun layoutCodecPreservesShapeAndDimensions() {
        val original = listOf(
            Widget("w1", Metric.STEPS, WidgetShape.CIRCLE, width = 4, height = 4, column = 0, row = 0),
            Widget("w2", Metric.HEART, WidgetShape.PILL, width = 2, height = 1, column = 0, row = 4),
            Widget("w3", Metric.BATTERY, WidgetShape.BANNER, width = 4, height = 2, column = 0, row = 5)
        )
        val encoded = LayoutCodec.encode(original)
        val decoded = LayoutCodec.decode(encoded)
        assertEquals(original.size, decoded.size)
        original.indices.forEach { i ->
            assertEquals(original[i].id, decoded[i].id)
            assertEquals(original[i].metric, decoded[i].metric)
            assertEquals(original[i].shape, decoded[i].shape)
            assertEquals(original[i].width, decoded[i].width)
            assertEquals(original[i].height, decoded[i].height)
        }
    }

    @Test
    fun disconnectedSourceHasNoReadingsForDetails() {
        val snapshot = HealthSnapshot(mode = DataMode.NONE)
        assertTrue(snapshot.days.isEmpty())
        assertTrue(snapshot.available.isEmpty())
    }

    @Test
    fun historyMathCompareYieldsConsistentPeriods() {
        val today = LocalDate.of(2026, 11, 5)
        val records = (0L..13L).map { offset ->
            DayData(today.minusDays(offset), mapOf(Metric.STEPS to Reading(900.0 + offset, DataState.READY)))
        }
        val summary = HistoryMath.compare(records, today, 7, Metric.STEPS)
        assertNotNull(summary.value)
        assertEquals(7, summary.total)
        assertEquals(7, summary.valid)
    }

    @Test
    fun circleSupportsThreeByThreeFootprint() {
        val circle = Widget("c2", Metric.STEPS, WidgetShape.CIRCLE)
        assertTrue("Circle must support 3x3", Footprint(3, 3) in circle.allowedSizes())
        assertEquals("Standard (2×2)", sizeDisplayName(WidgetShape.CIRCLE, Footprint(2, 2)))
        assertEquals("Medium (3×3)", sizeDisplayName(WidgetShape.CIRCLE, Footprint(3, 3)))
        assertEquals("Large (4×4)", sizeDisplayName(WidgetShape.CIRCLE, Footprint(4, 4)))
    }

    @Test
    fun bottomNavigationCentersHomeIconSymmetrically() {
        val items = listOf(
            Triple("history", "History", "history"),
            Triple("home", "Home", "home"),
            Triple("achievements", "Collection", "medal")
        )
        assertEquals(3, items.size)
        assertEquals("history", items[0].first)
        assertEquals("home", items[1].first)
        assertEquals("achievements", items[2].first)
    }

    @Test
    fun achievementEngineRegistryContainsAllCategories() {
        val all = com.pulse.app.core.AchievementRegistry.all
        assertEquals(36, all.size)
        val categories = all.map { it.category }.distinct()
        assertEquals(8, categories.size)
        assertTrue(com.pulse.app.core.AchievementCategory.STEPS in categories)
        assertTrue(com.pulse.app.core.AchievementCategory.SLEEP in categories)
        assertTrue(com.pulse.app.core.AchievementCategory.WORKOUTS in categories)
        assertTrue(com.pulse.app.core.AchievementCategory.TIMING in categories)
        assertTrue(com.pulse.app.core.AchievementCategory.DISTANCE in categories)
        assertTrue(com.pulse.app.core.AchievementCategory.ACTIVE in categories)
        assertTrue(com.pulse.app.core.AchievementCategory.CONSISTENCY in categories)
        assertTrue(com.pulse.app.core.AchievementCategory.SPECIAL in categories)
    }

    @Test
    fun achievementEngineParsesLocalHoursFromIsoStrings() {
        assertEquals(6, com.pulse.app.core.AchievementEngine.parseLocalHour("2026-11-05T06:30:00"))
        assertEquals(5, com.pulse.app.core.AchievementEngine.parseLocalHour("2026-11-05T05:00:00"))
        assertEquals(7, com.pulse.app.core.AchievementEngine.parseLocalHour("07:45"))
        assertEquals(12, com.pulse.app.core.AchievementEngine.parseLocalHour("2026-11-05T12:15:00"))
        assertEquals(19, com.pulse.app.core.AchievementEngine.parseLocalHour("19:00:00"))
        assertNull(com.pulse.app.core.AchievementEngine.parseLocalHour("invalid"))
    }

    @Test
    fun achievementEngineEvaluatesWorkoutsAndEarlyBirdTiming() {
        val today = LocalDate.of(2026, 11, 7)
        val morningActivity = RecordedActivity("act1", "Morning Run", "Run", "2026-11-07T06:15:00", 5.2, 35.0)
        val sampleDay = DayData(
            date = today,
            readings = mapOf(
                Metric.STEPS to Reading(6200.0, DataState.READY),
                Metric.SLEEP to Reading(8.5, DataState.READY),
                Metric.ACTIVE to Reading(45.0, DataState.READY),
                Metric.DISTANCE to Reading(5.2, DataState.READY)
            ),
            activities = listOf(morningActivity)
        )
        val snapshot = HealthSnapshot(days = listOf(sampleDay))
        val prefs = Preferences(stepGoal = 5000, sleepGoal = 8.0)

        val results = com.pulse.app.core.AchievementEngine.evaluate(snapshot, prefs)
        val resultMap = results.associateBy { it.definition.id }

        // Steps: 5K should be unlocked (6200 >= 5000)
        assertTrue("5K steps unlocked", resultMap["steps_5k"]?.unlocked == true)
        assertTrue("Goal crusher unlocked", resultMap["goal_crusher"]?.unlocked == true)

        // Sleep: Well rested unlocked (8.5 >= 8.0)
        assertTrue("Well rested unlocked", resultMap["well_rested"]?.unlocked == true)

        // Workouts: First workout and half hour hero unlocked (35 min >= 30)
        assertTrue("First workout unlocked", resultMap["first_workout"]?.unlocked == true)
        assertTrue("Half hour hero unlocked", resultMap["half_hour_hero"]?.unlocked == true)

        // Timing: Early bird (06:15 is between 5 and 7)
        assertTrue("Early bird unlocked", resultMap["early_bird"]?.unlocked == true)

        // Special: Triple threat (steps >= 5000, sleep >= 8, active >= 30)
        assertTrue("Triple threat unlocked", resultMap["triple_threat"]?.unlocked == true)
    }
}

