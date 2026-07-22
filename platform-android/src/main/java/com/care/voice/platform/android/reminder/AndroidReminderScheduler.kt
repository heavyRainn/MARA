package com.care.voice.platform.android.reminder

import com.care.voice.brain.reminder.ReminderRequest
import com.care.voice.brain.reminder.ReminderScheduleResult
import com.care.voice.brain.reminder.ReminderScheduler
import com.care.voice.data.history.ReminderEntity
import com.care.voice.data.repository.ReminderDao
import com.care.voice.data.reminder.ReminderScheduler as AlarmReminderScheduler

/**
 * Android implementation of portable [ReminderScheduler].
 */
class AndroidReminderScheduler(
    private val reminderDao: ReminderDao,
    private val alarmScheduler: AlarmReminderScheduler,
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) : ReminderScheduler {

    override suspend fun schedule(request: ReminderRequest): ReminderScheduleResult {
        if (request.triggerAtMillis <= nowMillis()) {
            return ReminderScheduleResult.Failure("Reminder time is in the past")
        }

        val entity = ReminderEntity(
            text = request.text,
            triggerAt = request.triggerAtMillis,
            isRepeating = request.isRepeating,
            repeatIntervalMillis = request.repeatIntervalMillis
        )

        val id = try {
            reminderDao.insert(entity)
        } catch (t: Throwable) {
            return ReminderScheduleResult.Failure(t.message ?: "Failed to save reminder")
        }

        val saved = entity.copy(id = id)
        val scheduled = alarmScheduler.schedule(saved)
        if (!scheduled) {
            runCatching { reminderDao.delete(saved) }
            alarmScheduler.cancel(id)
            return ReminderScheduleResult.Failure("Не удалось запланировать будильник")
        }

        return ReminderScheduleResult.Success(id)
    }
}
