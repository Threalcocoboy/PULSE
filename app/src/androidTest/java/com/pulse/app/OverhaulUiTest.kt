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
 @Test fun localNameSurvivesBackAndCanBeDeleted(){var stored=Preferences(introSeen=true,animations=false);rule.setContent{var p by remember{mutableStateOf(stored)};PulseTheme(p){Onboarding(p,HealthSnapshot(),{f->p=f(p);stored=p},{}){}}};rule.onNodeWithText("Get started").performScrollTo().performClick();rule.onNode(hasSetTextAction()).performTextInput("Alex");rule.onNodeWithText("Continue").performScrollTo().performClick();rule.onNodeWithText("Back").performScrollTo().performClick();rule.onNodeWithText("Alex").assertExists();rule.onNodeWithText("Delete name").performScrollTo().performClick();rule.runOnIdle{assertEquals("",stored.displayName)}}
 @Test fun delayedNameSaveDoesNotResetTheTypingDraft(){var p by mutableStateOf(Preferences(animations=false));val saves=mutableListOf<(Preferences)->Preferences>();rule.setContent{PulseTheme(p){DisplayNameEditor(p){saves+=it}}};rule.onNode(hasSetTextAction()).performTextInput("A");rule.onNode(hasSetTextAction()).performTextInput("lex");rule.runOnIdle{p=saves.first()(p)};rule.onNode(hasSetTextAction()).assert(SemanticsMatcher("Editable text is Alex"){it.config[androidx.compose.ui.semantics.SemanticsProperties.EditableText].text=="Alex"});rule.onNodeWithText("Save name").performClick();rule.runOnIdle{p=saves.last()(p);assertEquals("Alex",p.displayName)}}
 @Test fun multipleUnlocksAreVisibleTogetherAndContinueAcknowledgesThem(){val items=AchievementRegistry.all.take(2).map{UserAchievement(it,it.target,true,LocalDate.now(),"Complete")};var ids=emptyList<String>();rule.setContent{PulseTheme(Preferences(animations=false)){UnlockCelebration(items){ids=it}}};rule.onNodeWithText(items[0].definition.name).assertIsDisplayed();rule.onNodeWithText(items[1].definition.name).assertIsDisplayed();rule.onNodeWithText("Continue").assertIsDisplayed().performClick();rule.runOnIdle{assertEquals(items.map{it.instanceId},ids)}}
 @Test fun reducedMotionIntroCompletesOnceAcrossRecomposition(){var tick by mutableIntStateOf(0);var done=0;rule.setContent{PulseTheme(Preferences(animations=false)){Column{BrandLogo(Modifier.size(160.dp,40.dp),true){done++};androidx.compose.material3.Text("$tick")}}};rule.runOnIdle{assertEquals(1,done);tick++};rule.runOnIdle{assertEquals(1,done)}}
 @Test fun newGoalAppearsWithoutSeparateDefinitions(){var prefs=Preferences(animations=false);rule.setContent{var p by remember{mutableStateOf(prefs)};PulseTheme(p){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){GoalEditor(p,{f->p=f(p);prefs=p})}}};rule.onNodeWithText("+ New goal").performScrollTo().performClick();rule.onNodeWithText("Distance").performClick();rule.onNodeWithContentDescription("Distance goal").assertExists();rule.runOnIdle{assertTrue(Metric.DISTANCE in prefs.enabledGoals)}}
}
