package com.care.voice.platform.android.reminder

import com.care.voice.brain.reminder.Reminder
import com.care.voice.brain.reminder.ReminderDeliveryResult
import com.care.voice.brain.reminder.ReminderDeliveryService
import com.care.voice.brain.reminder.ReminderFailureCode
import com.care.voice.brain.reminder.ReminderLogEvent
import com.care.voice.brain.reminder.ReminderStatus
import com.care.voice.data.history.ReminderEntity
import com.care.voice.data.repository.ReminderDao

class ReminderEntityMapper {
    fun toDomain(entity: ReminderEntity): Reminder = Reminder(
        id = entity.id,
        text = entity.text,
        triggerAtEpochMillis = entity.triggerAt,
        status = entity.status,
        precision = entity.precision,
        deliveryMode = entity.deliveryMode,
        isRepeating = entity.isRepeating,
        repeatIntervalMillis = entity.repeatIntervalMillis,
        snoozeCount = entity.snoozeCount
    )
}

suspend fun ReminderDao.markTriggered(id: Long, now: Long): StatusUpdateResult =
    ReminderStateUpdater.applyTransition(this, id, ReminderStatus.TRIGGERED, now) {
        it.copy(triggeredAt = now)
    }

suspend fun ReminderDao.markDelivered(id: Long, now: Long): StatusUpdateResult =
    ReminderStateUpdater.applyTransition(this, id, ReminderStatus.DELIVERED, now) {
        it.copy(deliveredAt = now)
    }

suspend fun ReminderDao.markCompleted(id: Long, now: Long): StatusUpdateResult =
    ReminderStateUpdater.applyTransition(this, id, ReminderStatus.COMPLETED, now) {
        it.copy(completedAt = now)
    }

suspend fun ReminderDao.markCancelled(id: Long, now: Long): StatusUpdateResult =
    ReminderStateUpdater.applyTransition(this, id, ReminderStatus.CANCELLED, now)

suspend fun ReminderDao.markFailed(id: Long, reason: String, now: Long): StatusUpdateResult =
    ReminderStateUpdater.markFailed(
        dao = this,
        id = id,
        code = ReminderFailureCode.UNKNOWN,
        message = reason,
        now = now
    )
