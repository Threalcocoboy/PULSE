package com.pulse.app
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import com.pulse.app.core.*
import com.pulse.app.ui.*
import org.junit.*
import org.junit.Assert.*
class FollowupUiTest {
 @get:Rule val rule=createComposeRule()
 @Test fun circleGoalsHaveCompleteMeasuredStrings(){rule.setContent{PulseTheme(Preferences(animations=false)){Box(Modifier.size(180.dp)){MetricWidget(Widget("g",Metric.GOAL,WidgetShape.CIRCLE),Preferences(enabledGoals=Goals.supported),null,emptyList(),false)}}};rule.onAllNodes(hasText("of",substring=true)).fetchSemanticsNodes().forEach{node->val results=mutableListOf<TextLayoutResult>();node.config.getOrElse(SemanticsActions.GetTextLayoutResult){error("Missing text layout")}.action!!(results);assertTrue(results.isNotEmpty());assertFalse(results.first().hasVisualOverflow)}}
 @Test fun distanceNumberAndUnitRemainSeparate(){rule.setContent{PulseTheme(Preferences(animations=false)){Box(Modifier.size(160.dp)){MetricWidget(Widget("d",Metric.DISTANCE),Preferences(),DayData(java.time.LocalDate.now(),mapOf(Metric.DISTANCE to Reading(123.45,DataState.READY))),emptyList(),false)}}};rule.onNodeWithText("km").assertIsDisplayed();rule.onNodeWithText("Distance").assertIsDisplayed()}
 @Test fun addSheetPlacesWithoutPreviewStage(){var placed=false;rule.setContent{PulseTheme(Preferences(animations=false)){WidgetSheet(Widget("new",Metric.STEPS),dismiss={},change={placed=true},remove={},isNew=true)}};rule.onNodeWithText("Add").assertIsDisplayed().performClick();rule.runOnIdle{assertTrue(placed)} }
 @Test fun reducedMotionClockAndEcgRender(){rule.setContent{PulseTheme(Preferences(animations=false)){Column{PulseBottomNavigation("history"){};EcgSync()}}};rule.onNodeWithContentDescription("Synchronizing health data").assertExists();rule.onNodeWithText("History").assertExists()}
 @Test fun longPullTriggersRefreshCallback(){var refreshes=0;rule.setContent{PulseTheme(Preferences(animations=false)){RefreshSurface(false,{refreshes++}){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){Spacer(Modifier.height(1500.dp))}}}};rule.onRoot().performTouchInput{swipe(androidx.compose.ui.geometry.Offset(center.x,50f),androidx.compose.ui.geometry.Offset(center.x,height*.8f),1200)};rule.runOnIdle{assertTrue(refreshes>0)}}

}
