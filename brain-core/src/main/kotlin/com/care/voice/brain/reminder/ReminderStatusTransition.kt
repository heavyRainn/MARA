package com.care.voice.brain.reminder

object ReminderStatusTransition {

    private val TERMINAL = setOf(
        ReminderStatus.COMPLETED,
        ReminderStatus.CANCELLED,
        ReminderStatus.FAILED
    )

    private val TRIGGER_IDEMPOTENT_SKIP = setOf(
        ReminderStatus.TRIGGERED,
        ReminderStatus.DELIVERED,
        ReminderStatus.COMPLETED,
        ReminderStatus.CANCELLED,
        ReminderStatus.FAILED
    )

    private val ALLOWED = mapOf(
        ReminderStatus.SCHEDULING to setOf(
            ReminderStatus.SCHEDULED,
            ReminderStatus.FAILED
        ),
        ReminderStatus.SCHEDULED to setOf(
            ReminderStatus.TRIGGERED,
            ReminderStatus.CANCELLED,
            ReminderStatus.FAILED,
            ReminderStatus.SCHEDULING
        ),
        ReminderStatus.TRIGGERED to setOf(
            ReminderStatus.DELIVERED,
            ReminderStatus.FAILED,
            ReminderStatus.COMPLETED,
            ReminderStatus.SCHEDULING
        ),
        ReminderStatus.DELIVERED to setOf(
            ReminderStatus.COMPLETED,
            ReminderStatus.SCHEDULING
        ),
        ReminderStatus.SNOOZED to setOf(
            ReminderStatus.SCHEDULED,
            ReminderStatus.FAILED
        )
    )

    fun isTerminal(status: ReminderStatus): Boolean = status in TERMINAL

    fun shouldSkipAlarmTrigger(status: ReminderStatus): Boolean =
        status in TRIGGER_IDEMPOTENT_SKIP

    fun canTransition(from: ReminderStatus, to: ReminderStatus): Boolean {
        if (from == to) return true
        if (from in TERMINAL) return false
        return ALLOWED[from]?.contains(to) == true
    }

    fun canAcceptAlarmTrigger(status: ReminderStatus): Boolean =
        status == ReminderStatus.SCHEDULED

    fun canMarkDelivered(status: ReminderStatus): Boolean =
        status == ReminderStatus.TRIGGERED

    fun canComplete(status: ReminderStatus): Boolean =
        status in setOf(
            ReminderStatus.SCHEDULED,
            ReminderStatus.TRIGGERED,
            ReminderStatus.DELIVERED
        )

    fun canSnooze(status: ReminderStatus): Boolean =
        status in setOf(
            ReminderStatus.TRIGGERED,
            ReminderStatus.DELIVERED
        )

    fun isSnoozeInProgress(status: ReminderStatus): Boolean =
        status == ReminderStatus.SCHEDULING
}
