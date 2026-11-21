package com.pulse.app

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.pulse.app.core.DataMode
import com.pulse.app.core.Preferences
import com.pulse.app.data.AchievementProgressEntity
import com.pulse.app.integration.googlehealth.GoogleHealthBatch
import com.pulse.app.integration.googlehealth.GoogleHealthCategory
import com.pulse.app.integration.googlehealth.GoogleHealthCipher
import com.pulse.app.integration.googlehealth.GoogleHealthConnection
import com.pulse.app.integration.googlehealth.GoogleHealthStorage
import com.pulse.app.ui.GoogleHealthSetup
import com.pulse.app.ui.PulseTheme
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class GoogleHealthDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun encryptedAccountMeasurementsAndAwardsSurviveRestartAndRemoveIndependently() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testId = UUID.randomUUID().toString()
        val root = File(context.noBackupFilesDir, "google-health-test-$testId")
        val key = "pulse.google.health.test.$testId"
        val storage = GoogleHealthStorage(root, GoogleHealthCipher(key))
        val account = GoogleHealthConnection("test-account@example.test", "test-health-id", setOf(GoogleHealthCategory.SLEEP.scope))
        val other = GoogleHealthConnection("other-test@example.test", "test-health-other", account.scopes)
        val day = LocalDate.parse("2026-11-16")
        val batch = GoogleHealthBatch(day, day.plusDays(1), Instant.parse("2026-11-16T13:00:00Z"),
            listOf(JSONObject().put("marker", "synthetic-measurement-test-value")))
        try {
            storage.setConnection(account)
            storage.save(account, "sleep", batch)
            storage.save(other, "sleep", batch)
            val award = AchievementProgressEntity(account.sourceScope, "fixture-award", "test", null, 1.0, true, day.toString())
            storage.record(account.sourceScope, listOf(award))
            storage.record(account.sourceScope, listOf(award))
            assertEquals(setOf(award.instance), storage.pending(account.sourceScope))
            storage.acknowledge(account.sourceScope, listOf(award.instance))
            storage.record(account.sourceScope, listOf(award))
            val reopened = GoogleHealthStorage(root, GoogleHealthCipher(key))
            assertEquals(account.account, reopened.connection()!!.account)
            assertEquals(batch.points.first().toString(), reopened.batch(account, "sleep", day)!!.points.first().toString())
            assertEquals(listOf(award), reopened.achievements(account.sourceScope))
            assertTrue(reopened.pending(account.sourceScope).isEmpty())
            root.walkTopDown().filter(File::isFile).forEach { file ->
                val ciphertext = String(file.readBytes(), Charsets.ISO_8859_1)
                assertFalse(ciphertext.contains(account.account))
                assertFalse(ciphertext.contains("synthetic-measurement-test-value"))
                assertFalse(ciphertext.contains(award.instance))
            }
            reopened.remove(account)
            reopened.setConnection(null)
            assertNull(reopened.connection())
            assertNull(reopened.batch(account, "sleep", day))
            assertTrue(reopened.achievements(account.sourceScope).isEmpty())
            assertNotNull(reopened.batch(other, "sleep", day))
        } finally {
            storage.removeAll()
        }
    }

    @Test fun connectingWithoutGooglePlayServicesShowsRecoveryAndCannotFinishOnboarding() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS)
        val prefs = Preferences(setupSource = DataMode.GOOGLE_HEALTH, animations = false)
        val vm = PulseViewModel(context.applicationContext as Application, SavedStateHandle())
        compose.setContent {
            PulseTheme(prefs) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    GoogleHealthSetup(vm, prefs) { fail("Consent and identity verification are required") }
                }
            }
        }
        compose.onNodeWithText("Continue").assertDoesNotExist()
        compose.onNodeWithText("Connect with Google").performScrollTo().performClick()
        val expected = "Install or update Google Play services to connect Google Health."
        compose.waitUntil(10_000) { compose.onAllNodesWithText(expected).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(expected).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Continue").assertDoesNotExist()
    }
}
