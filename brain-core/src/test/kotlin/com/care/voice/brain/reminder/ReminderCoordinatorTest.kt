package com.care.voice.brain.reminder

import com.care.voice.brain.AssistantResult
import com.care.voice.brain.ReminderPendingCommand
import com.care.voice.brain.ReminderSetupKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderCoordinatorTest {

    private val coordinator = ReminderCoordinator(
        reminderTimeParser = ReminderTimeParser { 1_700_000_000_000L },
        nowMillis = { 1_700_000_000_000L }
    )

    @Test
    fun successMessageOnlyOnSuccess() = runTest {
        val schedule = ReminderPendingCommand.ScheduleReminder(
            title = "Таблетка",
            triggerAtEpochMillis = 1_700_086_400_000L,
            isRepeating = false,
            repeatIntervalMillis = null,
            humanReadableTime = "завтра в 9:00",
            precision = ReminderPrecision.EXACT
        )
        val scheduler = object : ReminderScheduler {
            override suspend fun schedule(request: ReminderRequest): ScheduleReminderResult =
                ScheduleReminderResult.Success(42L, 1_700_000_000_100L, ReminderPrecision.EXACT)
        }
        val result = coordinator.executeConfirmed(schedule, scheduler)
        assertTrue(result is AssistantResult.ActionCompleted)
        assertTrue((result as AssistantResult.ActionCompleted).text.startsWith("Готово. Напомню"))
    }

    @Test
    fun notificationPermissionRequiredDoesNotClaimSuccess() = runTest {
        val schedule = sampleSchedule()
        val scheduler = object : ReminderScheduler {
            override suspend fun schedule(request: ReminderRequest): ScheduleReminderResult =
                ScheduleReminderResult.NotificationPermissionRequired
        }
        val result = coordinator.executeConfirmed(schedule, scheduler)
        assertTrue(result is AssistantResult.ReminderSetupRequired)
        assertEquals(ReminderSetupKind.NOTIFICATION_PERMISSION, (result as AssistantResult.ReminderSetupRequired).kind)
    }

    @Test
    fun exactPermissionRequiredDoesNotClaimSuccess() = runTest {
        val schedule = sampleSchedule()
        val scheduler = object : ReminderScheduler {
            override suspend fun schedule(request: ReminderRequest): ScheduleReminderResult =
                ScheduleReminderResult.ExactAlarmPermissionRequired
        }
        val result = coordinator.executeConfirmed(schedule, scheduler)
        assertTrue(result is AssistantResult.ReminderSetupRequired)
        assertEquals(ReminderSetupKind.EXACT_ALARM_PERMISSION, (result as AssistantResult.ReminderSetupRequired).kind)
    }

    @Test
    fun failureDoesNotClaimSuccess() = runTest {
        val schedule = sampleSchedule()
        val scheduler = object : ReminderScheduler {
            override suspend fun schedule(request: ReminderRequest): ScheduleReminderResult =
                ScheduleReminderResult.Failure("alarm error")
        }
        val result = coordinator.executeConfirmed(schedule, scheduler)
        assertTrue(result is AssistantResult.Failure)
        assertTrue((result as AssistantResult.Failure).userMessage.contains("ничего не запланировала"))
    }

    private fun sampleSchedule() = ReminderPendingCommand.ScheduleReminder(
        title = "Таблетка",
        triggerAtEpochMillis = 1_700_086_400_000L,
        isRepeating = false,
        repeatIntervalMillis = null,
        humanReadableTime = "завтра в 9:00",
        precision = ReminderPrecision.EXACT
    )
}
