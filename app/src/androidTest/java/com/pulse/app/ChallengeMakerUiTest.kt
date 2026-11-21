package com.pulse.app

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.pulse.app.core.*
import com.pulse.app.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalTestApi::class)
class ChallengeMakerUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun checkboxMakerValidatesOnSubmitAndCreatesWithTheChosenIdentity() {
        var saved: PersonalChallenge? = null
        compose.setContent {
            PulseTheme(Preferences(theme = "LIGHT", animations = false)) {
                ChallengeMakerForm(setOf(Metric.STEPS), DataMode.HEALTH_CONNECT, {}, { saved = it; true })
            }
        }
        compose.onNodeWithText("Enter a challenge name.").assertDoesNotExist()
        compose.onNodeWithText("Custom category name").assertDoesNotExist()
        compose.onNodeWithText("Create Challenge").assertIsDisplayed().performClick()
        compose.onNodeWithText("Enter a challenge name.").assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasText("Challenge name")).assertIsFocused().performTextInput("Read")
        compose.onNode(hasSetTextAction() and hasText("Description (optional)")).performScrollTo().performTextInput("After lunch")
        compose.onNodeWithContentDescription("Moon icon").performScrollTo().performClick()
        compose.onNodeWithText("Category").performScrollTo().performClick()
        compose.onNodeWithText("Custom category").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("Custom category name")).performScrollTo().performTextInput("Learning")
        compose.onNodeWithContentDescription("Moon icon").assertIsSelected()
        compose.onNodeWithText("Tracking").performScrollTo().performClick()
        compose.onNodeWithText("Each tap adds one completion. You can check off more than once a day.").assertExists()
        compose.onNodeWithText("Unit").assertDoesNotExist()
        compose.onNodeWithText("Reward").assertDoesNotExist()
        compose.onNodeWithText("Check off 5 times within 7 days.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Create Challenge").assertIsDisplayed().performClick()
        compose.runOnIdle {
            val challenge = checkNotNull(saved)
            assertEquals("Read", challenge.name)
            assertEquals("After lunch", challenge.description)
            assertEquals("Learning", challenge.category)
            assertEquals("moon", challenge.icon)
            assertEquals("times", challenge.unit)
        }
    }

    @Test fun trackingAndRulesShowOnlyRelevantFieldsAndKeepTheSelectedDate() {
        var saved: PersonalChallenge? = null
        compose.setContent {
            PulseTheme(Preferences(theme = "LIGHT", animations = false)) {
                ChallengeMakerForm(setOf(Metric.STEPS), DataMode.HEALTH_CONNECT, {}, { saved = it; true })
            }
        }
        compose.onNode(hasSetTextAction() and hasText("Challenge name")).performTextInput("Walk")
        compose.onNodeWithText("Tracking").performScrollTo().performClick()
        compose.onNodeWithText("Tracking method").performScrollTo().performClick()
        compose.onNode(hasText("Enter a number") and hasClickAction()).performClick()
        compose.onNodeWithText("Unit").assertExists()
        compose.onNodeWithText("Tracking method").performScrollTo().performClick()
        compose.onNode(hasText("Track automatically") and hasClickAction()).performClick()
        compose.onNodeWithText("Unit").assertDoesNotExist()
        compose.onNodeWithText("Health metric").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Goal & Rules").performScrollTo().performClick()
        compose.onNodeWithText("Goal rule").performScrollTo().performClick()
        compose.onNode(hasText("Consecutive days") and hasClickAction()).performClick()
        compose.onNodeWithText("Days in a row").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Start date").performScrollTo().performClick()
        val tomorrow = LocalDate.now().plusDays(1)
        if (YearMonth.from(tomorrow) != YearMonth.now()) {
            compose.onNodeWithContentDescription("Next month").performClick()
        }
        compose.onNodeWithContentDescription(tomorrow.displayDate()).performScrollTo().performClick()
        compose.onNodeWithText("Set date").assertIsDisplayed().performClick()
        compose.onNodeWithText("Create Challenge").performClick()
        compose.runOnIdle {
            assertEquals(ChallengeTracking.AUTOMATIC, saved?.tracking)
            assertEquals(ChallengeRule.STREAK, saved?.rule)
            assertEquals(tomorrow, saved?.start)
        }
    }

    @Test fun identityDialogLoadsRealValuesAndArchiveRequiresConfirmation() {
        val challenge = PersonalChallenge(name = "Walk", description = "After lunch")
        var editing by mutableStateOf(true)
        var archiving by mutableStateOf(false)
        var saved: Pair<String, String>? = null
        var archived = false
        compose.setContent {
            PulseTheme(Preferences(theme = "LIGHT", animations = false)) {
                if (editing) EditChallengeIdentity(challenge, { editing = false }) { name, description ->
                    saved = name to description
                    editing = false
                }
                if (archiving) ArchiveChallengeDialog(challenge.name, { archiving = false }) { archived = true }
            }
        }
        compose.onNodeWithText("Walk").assertIsDisplayed()
        compose.onNodeWithText("After lunch").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertNull(saved); editing = true }
        compose.onNode(hasSetTextAction() and hasText("Challenge name")).performTextReplacement("Walk outside")
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals("Walk outside" to "After lunch", saved); archiving = true }
        compose.onNodeWithText("Archive challenge?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertFalse(archived); archiving = true }
        compose.onNodeWithText("Archive").performClick()
        compose.runOnIdle { assertTrue(archived) }
    }

    @Test fun animatedLogoFinishesWithTheStaticPixels() {
        var finished = false
        compose.setContent {
            PulseTheme(Preferences(theme = "LIGHT", animations = true)) {
                Column {
                    BrandLogo(Modifier.size(160.dp, 32.dp).testTag("animated"), animate = true) { finished = true }
                    BrandLogo(Modifier.size(160.dp, 32.dp).testTag("static"))
                }
            }
        }
        compose.waitUntil(3_000) { finished }
        val animated = compose.onNodeWithTag("animated").captureToImage().asAndroidBitmap()
        val static = compose.onNodeWithTag("static").captureToImage().asAndroidBitmap()
        assertTrue(animated.sameAs(static))
    }

    @Test fun loadingChangesTheLogoPixelsAndRestoresTheStaticLogo() {
        var loading by mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PulseTheme(Preferences(theme = "LIGHT", animations = true)) {
                Column {
                    BrandLogo(Modifier.size(160.dp, 32.dp).testTag("loading"), loading = loading)
                    BrandLogo(Modifier.size(160.dp, 32.dp).testTag("static"))
                }
            }
        }
        val logo = compose.onNodeWithTag("loading")
        compose.mainClock.advanceTimeBy(280)
        val first = logo.captureToImage().asAndroidBitmap()
        compose.mainClock.advanceTimeBy(360)
        val second = logo.captureToImage().asAndroidBitmap()
        assertFalse(first.sameAs(second))
        compose.runOnIdle { loading = false }
        compose.mainClock.advanceTimeByFrame()
        assertTrue(logo.captureToImage().asAndroidBitmap().sameAs(
            compose.onNodeWithTag("static").captureToImage().asAndroidBitmap()
        ))
    }
}
