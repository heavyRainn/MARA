package com.care.voice.platform.android.reminder

import com.care.voice.brain.reminder.ReminderFailureCode
import com.care.voice.brain.reminder.ReminderLogEvent
import com.care.voice.brain.reminder.ReminderStatus
import com.care.voice.brain.reminder.ReminderStatusTransition
import com.care.voice.data.history.ReminderEntity
import com.care.voice.data.repository.ReminderDao

sealed class StatusUpdateResult {
    data object Applied : StatusUpdateResult()
    data object SkippedIdempotent : StatusUpdateResult()
    data class Rejected(val reason: String) : StatusUpdateResult()
}

object ReminderStateUpdater {

    suspend fun applyTransition(
        dao: ReminderDao,
        id: Long,
        to: ReminderStatus,
        now: Long,
        patch: (ReminderEntity) -> ReminderEntity = { it }
    ): StatusUpdateResult {
        val entity = dao.getById(id) ?: return StatusUpdateResult.Rejected("not_found")
        if (entity.status == to) return StatusUpdateResult.SkippedIdempotent
        if (!ReminderStatusTransition.canTransition(entity.status, to)) {
            ReminderLog.event(
                reminderId = id,
                event = ReminderLogEvent.FAILED,
                statusFrom = entity.status.name,
                statusTo = to.name,
                detail = "invalid_transition"
            )
            return StatusUpdateResult.Rejected("invalid_transition:${entity.status}->$to")
        }
        val updated = patch(entity.copy(status = to, updatedAt = now))
        dao.update(updated)
        return StatusUpdateResult.Applied
    }

    suspend fun markFailed(
        dao: ReminderDao,
        id: Long,
        code: ReminderFailureCode,
        message: String,
        now: Long
    ): StatusUpdateResult {
        val entity = dao.getById(id) ?: return StatusUpdateResult.Rejected("not_found")
        if (ReminderStatusTransition.isTerminal(entity.status)) {
            return StatusUpdateResult.SkippedIdempotent
        }
        dao.update(
            entity.copy(
                status = ReminderStatus.FAILED,
                failureCode = code.name,
                failureMessage = message,
                failureReason = message,
                failedAt = now,
                updatedAt = now
            )
        )
        ReminderLog.event(
            reminderId = id,
            event = ReminderLogEvent.FAILED,
            statusFrom = entity.status.name,
            statusTo = ReminderStatus.FAILED.name,
            detail = code.name
        )
        return StatusUpdateResult.Applied
    }
}
