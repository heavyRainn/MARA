package com.care.voice.platform.android.reminder

import com.care.voice.brain.reminder.ReminderStatus
import com.care.voice.data.reminder.AlarmScheduleOutcome
import com.care.voice.data.reminder.ReminderAlarmScheduler
import com.care.voice.data.repository.ReminderDao

class ReminderRescheduler(
    private val reminderDao: ReminderDao,
    private val alarmScheduler: ReminderAlarmScheduler,
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) {
    suspend fun rescheduleAllFutureScheduled(): RescheduleReport {
        val now = nowMillis()
        val allScheduled = reminderDao.findByStatus(ReminderStatus.SCHEDULED)
        var rescheduled = 0
        var skippedPast = 0

        for (reminder in allScheduled) {
            if (reminder.triggerAt <= now) {
                skippedPast++
                ReminderStateUpdater.markFailed(
                    reminderDao,
                    reminder.id,
                    com.care.voice.brain.reminder.ReminderFailureCode.MISSED_AFTER_REBOOT,
                    "missed_after_reboot",
                    now
                )
                continue
            }
            alarmScheduler.cancel(reminder.id)
            when (val outcome = alarmScheduler.schedule(reminder.id, reminder.triggerAt, reminder.precision)) {
                AlarmScheduleOutcome.Success -> {
                    rescheduled++
                    reminderDao.update(
                        reminder.copy(updatedAt = now, failureReason = null)
                    )
                }
                else -> {
                    ReminderStateUpdater.markFailed(
                        reminderDao,
                        reminder.id,
                        com.care.voice.brain.reminder.ReminderFailureCode.RESCHEDULE_FAILED,
                        "reschedule_failed:${outcome::class.simpleName}",
                        now
                    )
                }
            }
        }

        ReminderLog.rescheduler(rescheduled, skippedPast)
        return RescheduleReport(rescheduled, skippedPast)
    }

    data class RescheduleReport(val rescheduled: Int, val skippedPast: Int)
}
