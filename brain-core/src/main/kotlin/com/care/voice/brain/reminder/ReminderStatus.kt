package com.care.voice.brain.reminder

enum class ReminderStatus {
    PENDING_CONFIRMATION,
    SCHEDULING,
    SCHEDULED,
    TRIGGERED,
    DELIVERED,
    SNOOZED,
    COMPLETED,
    CANCELLED,
    FAILED
}
