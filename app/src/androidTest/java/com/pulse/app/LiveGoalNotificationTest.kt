package com.pulse.app

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pulse.app.core.*
import com.pulse.app.data.PreferenceStore
import com.pulse.app.integration.LiveGoalNotifications
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class LiveGoalNotificationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val store = PreferenceStore(context)
    private val steps = PersonalGoal(id = "test_steps", metricId = "STEPS", target = 5530.0, aggregation = GoalAggregation.TOTAL)
    private val limit = steps.copy(id = "test_limit", condition = GoalCondition.MAXIMUM)

    private suspend fun active() = withTimeout(5_000) {
        var notification = posted()
        while (notification == null) {
            delay(50)
            notification = posted()
        }
        notification
    }

    private fun posted() = manager.activeNotifications.singleOrNull { it.notification.channelId == LiveGoalNotifications.CHANNEL }

    private fun snapshot(value: Double): HealthSnapshot {
        val now = Instant.now()
        val day = DayData(LocalDate.now(), mapOf(Metric.STEPS to Reading(value, DataState.READY, now)))
        return HealthSnapshot(listOf(day), synced = now, available = setOf(Metric.STEPS),
            mode = DataMode.HEALTH_CONNECT, access = ProviderAccess.CONNECTED)
    }

    @Test fun realSystemNotificationReplacesProgressAndCanBeStoppedAndDismissed() = runBlocking {
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        val previous = store.preferences.first()
        try {
            store.update { it.copy(onboarded = true, mode = DataMode.HEALTH_CONNECT, personalGoals = listOf(steps, limit),
                liveGoal = LiveGoalPreference().select(steps.id).enable(true)) }
            LiveGoalNotifications.publish(context, snapshot(3200.0))
            val first = active()
            val initial = first.notification
            assertTrue(initial.flags and Notification.FLAG_ONGOING_EVENT != 0)
            assertEquals(Notification.VISIBILITY_PRIVATE, initial.visibility)
            assertEquals("PULSE · Goal tracking", initial.publicVersion.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            assertNotNull(initial.contentIntent)
            if (Build.VERSION.SDK_INT >= 36) {
                assertTrue(initial.extras.getString(Notification.EXTRA_TEMPLATE).orEmpty().endsWith("ProgressStyle"))
                assertEquals(0, initial.flags and Notification.FLAG_PROMOTED_ONGOING)
            } else {
                assertEquals(57, initial.extras.getInt(Notification.EXTRA_PROGRESS))
            }

            LiveGoalNotifications.publish(context, snapshot(4000.0))
            withTimeout(5_000) {
                while (active().notification.extras.getCharSequence(Notification.EXTRA_TEXT) ==
                    initial.extras.getCharSequence(Notification.EXTRA_TEXT)) delay(50)
            }
            assertEquals(first.id, active().id)

            store.update { it.copy(liveGoal = it.liveGoal.select(limit.id).copy(hideOnLockScreen = true)) }
            LiveGoalNotifications.publish(context, snapshot(6000.0))
            withTimeout(5_000) { while (active().notification.visibility != Notification.VISIBILITY_SECRET) delay(50) }
            val replacement = active()
            assertEquals(first.id, replacement.id)
            if (Build.VERSION.SDK_INT >= 37) {
                assertTrue(replacement.notification.extras.getString(Notification.EXTRA_TEMPLATE).orEmpty().endsWith("MetricStyle"))
            }
            replacement.notification.deleteIntent.send()
            withTimeout(5_000) { store.preferences.first { it.liveGoal.dismissed } }
            LiveGoalNotifications.publish(context, snapshot(6200.0))
            assertFalse(manager.activeNotifications.any { it.id == first.id })

            store.update { it.copy(liveGoal = it.liveGoal.enable(true)) }
            LiveGoalNotifications.publish(context, snapshot(6200.0))
            active().notification.actions.single().actionIntent.send()
            withTimeout(5_000) { store.preferences.first { !it.liveGoal.enabled } }
            LiveGoalNotifications.publish(context, snapshot(6300.0))
            assertFalse(manager.activeNotifications.any { it.id == first.id })
        } finally {
            store.update { previous }
            LiveGoalNotifications.publish(context)
        }
    }
}
