package com.care.voice.platform.android.reminder

import com.care.voice.brain.reminder.CoordinatorDeliveryResult
import com.care.voice.brain.reminder.ReminderDeliveryCoordinator
import com.care.voice.brain.reminder.ReminderFailureCode
import com.care.voice.brain.reminder.ReminderLogEvent
import com.care.voice.brain.reminder.ReminderStatus
import com.care.voice.brain.reminder.ReminderStatusTransition
import com.care.voice.brain.reminder.VoiceDeliveryStatus
import com.care.voice.data.history.ReminderEntity
import com.care.voice.data.repository.ReminderDao

sealed class TriggerHandleResult {
    data object SkippedIdempotent : TriggerHandleResult()
    data object NotFound : TriggerHandleResult()
    data object Delivered : TriggerHandleResult()
    data class DeliveryFailed(val reason: String) : TriggerHandleResult()
    data class Rejected(val reason: String) : TriggerHandleResult()
}

object ReminderTriggerHandler {

    suspend fun handle(
        entity: ReminderEntity,
        dao: ReminderDao,
        coordinator: ReminderDeliveryCoordinator,
        now: Long
    ): TriggerHandleResult {
        if (ReminderStatusTransition.shouldSkipAlarmTrigger(entity.status)) {
            ReminderLog.event(
                reminderId = entity.id,
                event = ReminderLogEvent.SKIPPED_IDEMPOTENT,
                statusFrom = entity.status.name,
                detail = "duplicate_alarm_trigger"
            )
            return TriggerHandleResult.SkippedIdempotent
        }

        if (!ReminderStatusTransition.canAcceptAlarmTrigger(entity.status)) {
            return TriggerHandleResult.Rejected("invalid_status:${entity.status}")
        }

        val triggered = ReminderStateUpdater.applyTransition(
            dao = dao,
            id = entity.id,
            to = ReminderStatus.TRIGGERED,
            now = now
        ) { it.copy(triggeredAt = now) }

        if (triggered is StatusUpdateResult.Rejected) {
            return TriggerHandleResult.Rejected(triggered.reason)
        }

        ReminderLog.event(
            reminderId = entity.id,
            event = ReminderLogEvent.TRIGGERED,
            statusFrom = entity.status.name,
            statusTo = ReminderStatus.TRIGGERED.name
        )

        val snapshot = dao.getById(entity.id)?.toDeliverySnapshot()
            ?: return TriggerHandleResult.NotFound

        return mapCoordinatorResult(coordinator.deliverAfterTriggered(snapshot, now))
    }

    private fun mapCoordinatorResult(result: CoordinatorDeliveryResult): TriggerHandleResult =
        when (result) {
            is CoordinatorDeliveryResult.NotificationAndVoiceComplete,
            is CoordinatorDeliveryResult.NotificationDeliveredVoiceSkipped,
            is CoordinatorDeliveryResult.NotificationDeliveredVoiceFailed -> TriggerHandleResult.Delivered
            is CoordinatorDeliveryResult.NotificationFailed -> TriggerHandleResult.DeliveryFailed(result.reason)
            is CoordinatorDeliveryResult.SkippedIdempotent -> TriggerHandleResult.SkippedIdempotent
            is CoordinatorDeliveryResult.Rejected -> TriggerHandleResult.Rejected(result.reason)
        }
}

sealed class CompleteHandleResult {
    data object Applied : CompleteHandleResult()
    data object SkippedIdempotent : CompleteHandleResult()
    data object NotFound : CompleteHandleResult()
}

object ReminderCompleteHandler {

    suspend fun handle(
        reminderId: Long,
        dao: ReminderDao,
        cancelAlarm: (Long) -> Unit,
        cancelNotification: (Long) -> Unit,
        now: Long
    ): CompleteHandleResult {
        val entity = dao.getById(reminderId) ?: return CompleteHandleResult.NotFound

        if (entity.status == ReminderStatus.COMPLETED ||
            entity.status == ReminderStatus.CANCELLED ||
            entity.status == ReminderStatus.FAILED
        ) {
            ReminderLog.event(
                reminderId = reminderId,
                event = ReminderLogEvent.SKIPPED_IDEMPOTENT,
                statusFrom = entity.status.name,
                detail = "duplicate_complete"
            )
            return CompleteHandleResult.SkippedIdempotent
        }

        if (!ReminderStatusTransition.canComplete(entity.status)) {
            return CompleteHandleResult.SkippedIdempotent
        }

        cancelAlarm(reminderId)
        cancelNotification(reminderId)

        val result = ReminderStateUpdater.applyTransition(
            dao = dao,
            id = reminderId,
            to = ReminderStatus.COMPLETED,
            now = now
        ) { it.copy(completedAt = now) }

        return when (result) {
            is StatusUpdateResult.Applied -> {
                ReminderLog.event(
                    reminderId = reminderId,
                    event = ReminderLogEvent.COMPLETED,
                    statusFrom = entity.status.name,
                    statusTo = ReminderStatus.COMPLETED.name
                )
                CompleteHandleResult.Applied
            }
            is StatusUpdateResult.SkippedIdempotent -> CompleteHandleResult.SkippedIdempotent
            is StatusUpdateResult.Rejected -> CompleteHandleResult.SkippedIdempotent
        }
    }
}

sealed class SnoozeHandleResult {
    data object Applied : SnoozeHandleResult()
    data object SkippedIdempotent : SnoozeHandleResult()
    data object NotFound : SnoozeHandleResult()
    data class Failed(val code: ReminderFailureCode) : SnoozeHandleResult()
}

object ReminderSnoozeHandler {

    suspend fun handle(
        reminderId: Long,
        dao: ReminderDao,
        voiceStateStore: com.care.voice.brain.reminder.ReminderVoiceStateStore,
        scheduleAlarm: (Long, Long, com.care.voice.brain.reminder.ReminderPrecision) -> com.care.voice.data.reminder.AlarmScheduleOutcome,
        cancelAlarm: (Long) -> Unit,
        cancelNotification: (Long) -> Unit,
        now: Long,
        snoozeMillis: Long
    ): SnoozeHandleResult {
        val entity = dao.getById(reminderId) ?: return SnoozeHandleResult.NotFound

        if (ReminderStatusTransition.isSnoozeInProgress(entity.status)) {
            ReminderLog.event(
                reminderId = reminderId,
                event = ReminderLogEvent.SKIPPED_IDEMPOTENT,
                statusFrom = entity.status.name,
                detail = "snooze_in_progress"
            )
            return SnoozeHandleResult.SkippedIdempotent
        }

        if (entity.status == ReminderStatus.COMPLETED ||
            entity.status == ReminderStatus.CANCELLED ||
            entity.status == ReminderStatus.FAILED ||
            entity.status == ReminderStatus.SCHEDULING
        ) {
            return SnoozeHandleResult.SkippedIdempotent
        }

        if (!ReminderStatusTransition.canSnooze(entity.status)) {
            return SnoozeHandleResult.SkippedIdempotent
        }

        val newTrigger = now + snoozeMillis

        cancelNotification(reminderId)
        cancelAlarm(reminderId)

        val schedulingResult = ReminderStateUpdater.applyTransition(
            dao = dao,
            id = reminderId,
            to = ReminderStatus.SCHEDULING,
            now = now
        ) {
            it.copy(
                triggerAt = newTrigger,
                snoozeCount = it.snoozeCount + 1,
                lastSnoozedAt = now,
                failureCode = null,
                failureMessage = null,
                failureReason = null,
                failedAt = null,
                voiceDeliveryStatus = VoiceDeliveryStatus.NOT_REQUESTED,
                voiceDeliveredAt = null,
                voiceSkipReason = null,
                voiceFailureCode = null,
                voiceRequestedAt = null
            )
        }

        if (schedulingResult !is StatusUpdateResult.Applied) {
            return SnoozeHandleResult.SkippedIdempotent
        }

        voiceStateStore.resetForNewDeliveryCycle(reminderId)

        ReminderLog.event(
            reminderId = reminderId,
            event = ReminderLogEvent.SNOOZED,
            statusFrom = entity.status.name,
            statusTo = ReminderStatus.SCHEDULING.name
        )

        return when (scheduleAlarm(reminderId, newTrigger, entity.precision)) {
            com.care.voice.data.reminder.AlarmScheduleOutcome.Success -> {
                ReminderStateUpdater.applyTransition(
                    dao = dao,
                    id = reminderId,
                    to = ReminderStatus.SCHEDULED,
                    now = now
                ) { it.copy(scheduledAt = now) }
                ReminderLog.event(
                    reminderId = reminderId,
                    event = ReminderLogEvent.SCHEDULED,
                    statusFrom = ReminderStatus.SCHEDULING.name,
                    statusTo = ReminderStatus.SCHEDULED.name,
                    detail = "snooze"
                )
                SnoozeHandleResult.Applied
            }
            else -> {
                ReminderStateUpdater.markFailed(
                    dao = dao,
                    id = reminderId,
                    code = ReminderFailureCode.SNOOZE_SCHEDULE_FAILED,
                    message = "snooze_schedule_failed",
                    now = now
                )
                SnoozeHandleResult.Failed(ReminderFailureCode.SNOOZE_SCHEDULE_FAILED)
            }
        }
    }
}

sealed class RedeliveryHandleResult {
    data object Delivered : RedeliveryHandleResult()
    data object Skipped : RedeliveryHandleResult()
    data class Failed(val reason: String) : RedeliveryHandleResult()
}

object ReminderRedeliveryHandler {

    suspend fun handle(
        entity: ReminderEntity,
        coordinator: ReminderDeliveryCoordinator,
        now: Long
    ): RedeliveryHandleResult {
        if (entity.status != ReminderStatus.TRIGGERED) return RedeliveryHandleResult.Skipped

        return when (val result = coordinator.redeliverFromReconcile(entity.toDeliverySnapshot(), now)) {
            is CoordinatorDeliveryResult.NotificationAndVoiceComplete,
            is CoordinatorDeliveryResult.NotificationDeliveredVoiceSkipped,
            is CoordinatorDeliveryResult.NotificationDeliveredVoiceFailed -> RedeliveryHandleResult.Delivered
            is CoordinatorDeliveryResult.NotificationFailed -> RedeliveryHandleResult.Failed(result.reason)
            is CoordinatorDeliveryResult.SkippedIdempotent -> RedeliveryHandleResult.Skipped
            is CoordinatorDeliveryResult.Rejected -> RedeliveryHandleResult.Failed(result.reason)
        }
    }
}
