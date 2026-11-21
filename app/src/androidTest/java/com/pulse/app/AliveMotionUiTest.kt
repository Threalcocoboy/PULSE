package com.pulse.app

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.pulse.app.core.*
import com.pulse.app.data.ChallengeRepository
import com.pulse.app.data.PreferenceStore
import com.pulse.app.ui.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class AliveMotionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tabIndicatorRetargetsWhileItIsMoving() {
        var tab by mutableStateOf("home")
        compose.setContent {
            CompositionLocalProvider(LocalAnimations provides true) {
                PulseBottomNavigation(tab) { tab = it }
            }
        }
        val indicator = compose.onNodeWithTag("pulse-tab-indicator")
        fun x() = indicator.fetchSemanticsNode().boundsInRoot.center.x
        val initial = x()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Collection").performClick()
        compose.mainClock.advanceTimeBy(96)
        val halfway = x()
        val destination = compose.onNodeWithText("Collection").fetchSemanticsNode().boundsInRoot.center.x
        assertTrue("The indicator travels between tab centers", halfway > initial && halfway < destination)
        compose.onNodeWithText("History").performClick()
        compose.mainClock.advanceTimeBy(540)
        compose.onNodeWithText("History").assertIsSelected()
        val history = compose.onNodeWithText("History").fetchSemanticsNode().boundsInRoot.center.x
        assertEquals(history, x(), 2f)
    }

    @Test fun reducedMotionMovesTheIndicatorWithoutIntermediateFrames() {
        var tab by mutableStateOf("home")
        compose.setContent {
            CompositionLocalProvider(LocalAnimations provides false) {
                PulseBottomNavigation(tab) { tab = it }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("History").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("History").assertIsSelected()
        val history = compose.onNodeWithText("History").fetchSemanticsNode().boundsInRoot.center.x
        val indicator = compose.onNodeWithTag("pulse-tab-indicator").fetchSemanticsNode().boundsInRoot.center.x
        assertEquals(history, indicator, 2f)
    }

    @Test fun populatedRoutesKeepSelectionAndReverseNestedNavigation() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val prefs = Preferences(onboarded = true, mode = DataMode.HEALTH_CONNECT, animations = true, haptics = false)
        val today = LocalDate.now()
        val fixture = HealthSnapshot(
            days = (0 until 14).map { age ->
                DayData(today.minusDays(age.toLong()), mapOf(
                    Metric.STEPS to Reading((6420 + 413 * age).toDouble(), DataState.READY),
                    Metric.SLEEP to Reading(7.1 + (age % 4) * .3, DataState.READY)
                ))
            }, available = setOf(Metric.STEPS, Metric.SLEEP), mode = prefs.mode, access = ProviderAccess.CONNECTED
        )
        val store = PreferenceStore(app)
        val original = runBlocking { store.preferences.first() }
        val originalModes = runBlocking { store.dashboardModes() }
        val challenge = PersonalChallenge(name = "Motion test · mindfulness", target = 1.0, source = DataMode.NONE)
        val repo = ChallengeRepository(app)
        lateinit var dispatchBack: () -> Unit
        runBlocking {
            store.update { prefs.copy(mode = DataMode.NONE) }
            store.saveModes(PulseModes.initial())
            repo.create(challenge)
        }

        try {
            compose.setContent {
                val activity = LocalActivity.current as ComponentActivity
                SideEffect { dispatchBack = activity.onBackPressedDispatcher::onBackPressed }
                val vm: PulseViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                PulseTheme(prefs) { PulseApp(vm, prefs, fixture, {}, 0) }
            }
            compose.onNodeWithContentDescription("Steps widget").assertExists()
            compose.onNodeWithText("History").performClick()
            val chart = compose.onNodeWithContentDescription("Steps recorded values", substring = true)
            chart.assertIsDisplayed().performTouchInput { click(Offset(width * .78f, center.y)) }
            compose.onNodeWithText("Clear selection").assertIsDisplayed().performClick()
            compose.onNodeWithText("30D").performClick().assertIsSelected()
            compose.onNode(hasText("Sleep") and isSelectable()).performClick().assertIsSelected()

            compose.mainClock.autoAdvance = false
            compose.onNodeWithText("Home").performClick()
            compose.mainClock.advanceTimeBy(64)
            compose.onNodeWithText("Collection").performClick()
            compose.mainClock.advanceTimeBy(64)
            compose.onNode(hasText("History") and isSelectable()).performClick()
            compose.mainClock.advanceTimeBy(48)
            assertTrue(compose.onAllNodesWithTag("pulse-tab-page").fetchSemanticsNodes().size <= 2)
            compose.mainClock.advanceTimeBy(600)
            compose.mainClock.autoAdvance = true
            compose.onNode(hasText("History") and isSelected()).assertIsDisplayed()
            compose.onNodeWithContentDescription("Sleep recorded values", substring = true).assertExists()

            compose.runOnIdle { dispatchBack() }
            compose.onNodeWithText("Home").assertIsSelected()
            compose.onNodeWithText("Everyday").performClick()
            compose.onNodeWithText("Fitness").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Fitness").fetchSemanticsNodes().size == 1 }
            compose.onNodeWithContentDescription("Workout duration widget").assertExists()
            compose.onNodeWithContentDescription("More options").performClick()
            compose.onNodeWithText("Settings").performClick()
            compose.onNodeWithText("Appearance").assertIsDisplayed().performClick()
            compose.onNodeWithText("Animations").assertIsDisplayed()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText("Settings").assertIsDisplayed()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText("Fitness").assertIsDisplayed()

            compose.onNodeWithText("Collection").performClick()
            compose.onNodeWithText("Challenges").performClick()
            compose.onNodeWithText(challenge.name).performClick()
            compose.onNodeWithText("Mark completion").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Achievement unlocked").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Continue").performClick()
            compose.onNodeWithText("Badge earned · View it in Collection").assertExists()
        } finally {
            compose.mainClock.autoAdvance = true
            runBlocking {
                repo.update { entries -> entries.filterNot { it.id == challenge.id } }
                store.update { original }
                store.saveModes(originalModes)
            }
        }
    }
}
