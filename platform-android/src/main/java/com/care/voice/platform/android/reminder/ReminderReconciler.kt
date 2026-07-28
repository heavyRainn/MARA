package com.care.voice.platform.android.reminder

import com.care.voice.brain.reminder.ReminderDeliveryCoordinator
import com.care.voice.brain.reminder.ReminderFailureCode
import com.care.voice.brain.reminder.ReminderLogEvent
import com.care.voice.brain.reminder.ReminderStatus
import com.care.voice.data.reminder.AlarmScheduleOutcome
import com.care.voice.data.reminder.ReminderAlarmScheduler
import com.care.voice.data.repository.ReminderDao

class ReminderReconciler(
    private val reminderDao: ReminderDao,
    private val alarmScheduler: ReminderAlarmScheduler,
    private val deliveryCoordinator: ReminderDeliveryCoordinator,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val staleSchedulingThresholdMs: Long = 5 * 60 * 1000L
) {
    suspend fun reconcile(): ReconcileReport {
        val now = nowMillis()
        var restored = 0
        var missed = 0
        var staleResolved = 0
        var redelivered = 0

        for (reminder in reminderDao.findByStatus(ReminderStatus.SCHEDULING)) {
            if (now - reminder.updatedAt <= staleSchedulingThresholdMs) continue
            staleResolved += reconcileStaleScheduling(reminder, now)
        }

        for (reminder in reminderDao.findByStatus(ReminderStatus.SCHEDULED)) {
            if (reminder.triggerAt <= now) {
                ReminderStateUpdater.markFailed(
                    reminderDao,
                    reminder.id,
                    ReminderFailureCode.MISSED_AFTER_DEADLINE,
                    "missed_after_deadline",
                    now
                )
                alarmScheduler.cancel(reminder.id)
                missed++
            } else {
                alarmScheduler.cancel(reminder.id)
                when (alarmScheduler.schedule(reminder.id, reminder.triggerAt, reminder.precision)) {
                    AlarmScheduleOutcome.Success -> {
                        reminderDao.update(reminder.copy(updatedAt = now, failureReason = null))
                        restored++
                        ReminderLog.event(
                            reminderId = reminder.id,
                            event = ReminderLogEvent.RESTORED,
                            statusFrom = ReminderStatus.SCHEDULED.name,
                            statusTo = ReminderStatus.SCHEDULED.name
                        )
                    }
                    else -> {
                        ReminderStateUpdater.markFailed(
                            reminderDao,
                            reminder.id,
                            ReminderFailureCode.RESCHEDULE_FAILED,
                            "reschedule_failed",
                            now
                        )
                    }
                }
            }
        }

        for (reminder in reminderDao.findByStatus(ReminderStatus.TRIGGERED)) {
            val result = ReminderRedeliveryHandler.handle(
                entity = reminder,
                coordinator = deliveryCoordinator,
                now = now
            )
            if (result is RedeliveryHandleResult.Delivered) {
                redelivered++
            }
        }

        val report = ReconcileReport(restored, missed, staleResolved, redelivered)
        ReminderLog.reconciler(
            "restored=$restored missed=$missed staleResolved=$staleResolved redelivered=$redelivered"
        )
        return report
    }

    private suspend fun reconcileStaleScheduling(
        reminder: com.care.voice.data.history.ReminderEntity,
        now: Long
    ): Int {
        if (reminder.triggerAt <= now) {
            ReminderStateUpdater.markFailed(
                reminderDao,
                reminder.id,
                ReminderFailureCode.STALE_SCHEDULING,
                "stale_scheduling_past_trigger",
                now
            )
            return 1
        }
        alarmScheduler.cancel(reminder.id)
        return when (alarmScheduler.schedule(reminder.id, reminder.triggerAt, reminder.precision)) {
            AlarmScheduleOutcome.Success -> {
                ReminderStateUpdater.applyTransition(
                    dao = reminderDao,
                    id = reminder.id,
                    to = ReminderStatus.SCHEDULED,
                    now = now
                ) { it.copy(scheduledAt = now) }
                ReminderLog.event(
                    reminderId = reminder.id,
                    event = ReminderLogEvent.RESTORED,
                    statusFrom = ReminderStatus.SCHEDULING.name,
                    statusTo = ReminderStatus.SCHEDULED.name,
                    detail = "stale_scheduling"
                )
                1
            }
            else -> {
                ReminderStateUpdater.markFailed(
                    reminderDao,
                    reminder.id,
                    ReminderFailureCode.STALE_SCHEDULING,
                    "stale_scheduling_alarm_failed",
                    now
                )
                1
            }
        }
    }

    data class ReconcileReport(
        val restored: Int,
        val missed: Int,
        val staleResolved: Int,
        val redelivered: Int
    )
}
