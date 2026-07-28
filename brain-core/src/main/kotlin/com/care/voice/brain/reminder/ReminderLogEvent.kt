package com.care.voice.brain.reminder

enum class ReminderLogEvent {
    CREATED,
    SCHEDULING,
    SCHEDULED,
    TRIGGERED,
    DELIVERED,
    SNOOZED,
    COMPLETED,
    FAILED,
    RESTORED,
    SKIPPED_IDEMPOTENT,
    CANCELLED,
    VOICE_REQUESTED,
    VOICE_SPOKEN,
    VOICE_SKIPPED,
    VOICE_FAILED
}
