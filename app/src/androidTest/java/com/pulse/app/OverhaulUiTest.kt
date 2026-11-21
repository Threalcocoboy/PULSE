package com.pulse.app
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.pulse.app.core.*
import com.pulse.app.ui.*
import org.junit.*
import org.junit.Assert.*
import java.time.LocalDate
class OverhaulUiTest {
 @get:Rule val rule=createComposeRule()
 @Test fun displayNameCanBeSavedAndDeletedFromProfile() {
     var p by mutableStateOf(Preferences(animations = false))
     rule.setContent { PulseTheme(p) { DisplayNameEditor(p) { update -> p = update(p) } } }
     rule.onNode(hasSetTextAction()).performTextInput("Alex")
     rule.onNodeWithText("Save name").performClick()
     rule.runOnIdle { assertEquals("Alex", p.displayName) }
     rule.onNodeWithText("Delete name").performClick()
     rule.runOnIdle { assertEquals("", p.displayName) }
 }
 @Test fun choosingSourceOnlySelectsTheCard() {
     var selected by mutableStateOf(DataMode.NONE)
     rule.setContent { PulseTheme(Preferences(animations = false)) { SourceChoices(selected) { selected = it } } }
     rule.onNodeWithText("Garmin Personal API").performClick()
     rule.runOnIdle { assertEquals(DataMode.GARMIN, selected) }
     rule.onNodeWithText("Google Health").performClick()
     rule.runOnIdle { assertEquals(DataMode.GOOGLE_HEALTH, selected) }
     rule.onNodeWithText("Health Connect").performClick()
     rule.runOnIdle { assertEquals(DataMode.HEALTH_CONNECT, selected) }
 }
 @Test fun healthConnectRequiresVerifiedAccessBeforeContinuing() {
     var snapshot by mutableStateOf(HealthSnapshot(mode = DataMode.HEALTH_CONNECT))
     var requests = 0
     var advances = 0
     rule.setContent {
         PulseTheme(Preferences(animations = false)) {
             Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                 HealthConnectSetup(androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE, snapshot,
                     grant = { requests++ }, verified = snapshot.access.verified, advance = { advances++ })
             }
         }
     }
     rule.onNodeWithText("Continue").assertDoesNotExist()
     rule.onNodeWithText("Grant access").performScrollTo().performClick()
     rule.runOnIdle {
         assertEquals(1, requests)
         snapshot = snapshot.copy(access = ProviderAccess.DENIED)
     }
     rule.onNodeWithText("Continue").assertDoesNotExist()
     rule.onNodeWithText("Try again").performScrollTo().performClick()
     rule.runOnIdle {
         assertEquals(2, requests)
         snapshot = snapshot.copy(access = ProviderAccess.PARTIAL)
     }
     rule.onNodeWithText("Partial access").assertExists()
     rule.onNodeWithText("Access is verified. No health records have been imported yet.").assertExists()
     rule.onNodeWithText("Continue").performScrollTo().performClick()
     rule.runOnIdle { assertEquals(1, advances) }
 }
 @Test fun multipleUnlocksAreVisibleTogetherAndContinueAcknowledgesThem(){val items=AchievementRegistry.all.take(2).map{UserAchievement(it,it.target,true,LocalDate.now(),"Complete")};var ids=emptyList<String>();rule.setContent{PulseTheme(Preferences(animations=false)){UnlockCelebration(items){ids=it}}};rule.onNodeWithText(items[0].definition.name).assertIsDisplayed();rule.onNodeWithText(items[1].definition.name).assertIsDisplayed();rule.onNodeWithText("Continue").assertIsDisplayed().performClick();rule.runOnIdle{assertEquals(items.map{it.instanceId},ids)}}
 @Test fun reducedMotionIntroCompletesOnceAcrossRecomposition(){var tick by mutableIntStateOf(0);var done=0;rule.setContent{PulseTheme(Preferences(animations=false)){Column{BrandLogo(Modifier.size(160.dp,40.dp),true){done++};androidx.compose.material3.Text("$tick")}}};rule.runOnIdle{assertEquals(1,done);tick++};rule.runOnIdle{assertEquals(1,done)}}
 @Test fun newGoalAppearsAndPersistsOnlyOnSave() {
     var prefs by mutableStateOf(Preferences(mode = DataMode.HEALTH_CONNECT, animations = false))
     rule.setContent {
         PulseTheme(prefs) {
             Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                 GoalEditor(prefs, { update -> prefs = update(prefs) })
             }
         }
     }
     rule.onNodeWithText("New goal").performScrollTo().performClick()
     rule.onNodeWithTag("goal-metric-list").performScrollToNode(hasText("Sleep duration"))
     rule.onNode(hasText("Sleep duration") and hasAnyAncestor(hasTestTag("goal-metric-list"))).assertIsDisplayed()
     rule.onNodeWithText("Search metrics").performClick().performTextInput("HRV")
     rule.onNodeWithText("Heart-rate variability (HRV)").assertIsDisplayed()
     rule.onNodeWithText("Search metrics").performTextReplacement("workout count")
     rule.onNodeWithText("Workout count").performClick()
     rule.onNodeWithText("Weekly").performClick()
     rule.onNodeWithText("Target (sessions)").performTextInput("4")
     rule.runOnIdle { assertFalse(Goals.active(prefs).any { it.metric.source == Metric.ACTIVITIES }) }
     rule.onNodeWithText("Save goal").performScrollTo().performClick()
     rule.runOnIdle { assertTrue(Goals.active(prefs).any { it.metric.source == Metric.ACTIVITIES && it.target == 4.0 && it.cadence == GoalCadence.WEEKLY }) }
 }

 @Test fun historyChartSelectionReplacesAndRestoresThePrimaryValue() {
     val today = LocalDate.now()
     val data = HealthSnapshot(days = listOf(
         DayData(today.minusDays(1), mapOf(Metric.STEPS to Reading(3636.0, DataState.READY))),
         DayData(today, mapOf(Metric.STEPS to Reading(18150.0, DataState.READY)))
     ))
     rule.setContent { PulseTheme(Preferences(animations = false)) { HistoryScreen(Preferences(), data) {} } }
     rule.onNodeWithText(number(21786.0)).assertIsDisplayed()
     val chart = rule.onNodeWithContentDescription("Steps recorded values", substring = true)
     chart.performScrollTo().performTouchInput { click(androidx.compose.ui.geometry.Offset(width * .78f, center.y)) }
     rule.onNodeWithText(number(3636.0)).assertExists()
     rule.onNodeWithText(number(21786.0)).assertDoesNotExist()
     rule.onNodeWithText("Clear selection").performScrollTo().performClick()
     rule.onNodeWithText(number(21786.0)).assertExists()
 }
}
