package com.care.voice.platform.android.reminder

import com.care.voice.brain.reminder.ReminderDeliveryPersistence
import com.care.voice.brain.reminder.ReminderDeliverySnapshot
import com.care.voice.brain.reminder.ReminderStatus
import com.care.voice.brain.reminder.ReminderStatusTransition
import com.care.voice.brain.reminder.VoiceDeliveryStatus
import com.care.voice.brain.reminder.VoiceSkipReason
import com.care.voice.brain.reminder.ReminderVoiceStateStore
import com.care.voice.data.history.ReminderEntity
import com.care.voice.data.repository.ReminderDao

fun ReminderEntity.toDeliverySnapshot(): ReminderDeliverySnapshot =
    ReminderDeliverySnapshot(
        id = id,
        text = text,
        status = status,
        voiceDeliveryStatus = voiceDeliveryStatus
    )

class RoomReminderDeliveryPersistence(
    private val dao: ReminderDao
) : ReminderDeliveryPersistence {

    override suspend fun getSnapshot(reminderId: Long): ReminderDeliverySnapshot? =
        dao.getById(reminderId)?.toDeliverySnapshot()

    override suspend fun markNotificationDelivered(reminderId: Long, nowEpochMillis: Long): Boolean {
        if (!ReminderStatusTransition.canMarkDelivered(
                dao.getById(reminderId)?.status ?: return false
            )
        ) {
            return false
        }
        val result = ReminderStateUpdater.applyTransition(
            dao = dao,
            id = reminderId,
            to = ReminderStatus.DELIVERED,
            now = nowEpochMillis
        ) { it.copy(deliveredAt = nowEpochMillis) }
        return result is StatusUpdateResult.Applied
    }
}

class RoomReminderVoiceStateStore(
    private val dao: ReminderDao
) : ReminderVoiceStateStore {

    override suspend fun getStatus(reminderId: Long): VoiceDeliveryStatus =
        dao.getById(reminderId)?.voiceDeliveryStatus ?: VoiceDeliveryStatus.NOT_REQUESTED

    override suspend fun tryMarkPending(reminderId: Long, nowEpochMillis: Long): Boolean =
        dao.tryMarkVoicePending(reminderId, nowEpochMillis) == 1

    override suspend fun markSpoken(reminderId: Long, nowEpochMillis: Long) {
        updateVoice(reminderId, nowEpochMillis) {
            it.copy(
                voiceDeliveryStatus = VoiceDeliveryStatus.SPOKEN,
                voiceDeliveredAt = nowEpochMillis,
                voiceSkipReason = null,
                voiceFailureCode = null
            )
        }
    }

    override suspend fun markSkipped(reminderId: Long, reason: VoiceSkipReason, nowEpochMillis: Long) {
        updateVoice(reminderId, nowEpochMillis) {
            it.copy(
                voiceDeliveryStatus = VoiceDeliveryStatus.SKIPPED,
                voiceSkipReason = reason.name,
                voiceFailureCode = null
            )
        }
    }

    override suspend fun markFailed(reminderId: Long, code: String, nowEpochMillis: Long) {
        updateVoice(reminderId, nowEpochMillis) {
            it.copy(
                voiceDeliveryStatus = VoiceDeliveryStatus.FAILED,
                voiceFailureCode = code,
                voiceSkipReason = null
            )
        }
    }

    override suspend fun resetForNewDeliveryCycle(reminderId: Long) {
        val entity = dao.getById(reminderId) ?: return
        dao.update(
            entity.copy(
                voiceDeliveryStatus = VoiceDeliveryStatus.NOT_REQUESTED,
                voiceDeliveredAt = null,
                voiceSkipReason = null,
                voiceFailureCode = null,
                voiceRequestedAt = null
            )
        )
    }

    private suspend fun updateVoice(
        reminderId: Long,
        now: Long,
        patch: (ReminderEntity) -> ReminderEntity
    ) {
        val entity = dao.getById(reminderId) ?: return
        dao.update(patch(entity).copy(updatedAt = now))
    }
}
