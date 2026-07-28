package com.care.voice.brain.pending

import com.care.voice.brain.ReminderPendingCommand
import com.care.voice.brain.util.JsonExtractor
import java.time.Instant
import java.util.UUID

object PendingActionCodec {

    private const val REMINDER_TTL_HOURS = 24L
    private const val MEMORY_TTL_HOURS = 48L

    fun reminderCreate(command: ReminderPendingCommand.ScheduleReminder, now: Instant): PendingAction {
        val payload = """{"title":${jsonStr(command.title)},"triggerAt":${command.triggerAtEpochMillis},"repeating":${command.isRepeating},"repeatMs":${command.repeatIntervalMillis ?: "null"},"humanTime":${jsonStr(command.humanReadableTime)},"precision":${jsonStr(command.precision.name)}}"""
        return PendingAction(
            id = UUID.randomUUID().toString(),
            type = PendingActionType.CREATE_REMINDER,
            payload = payload,
            state = PendingActionState.WAITING_CONFIRMATION,
            createdAt = now,
            expiresAt = now.plusSeconds(REMINDER_TTL_HOURS * 3600)
        )
    }

    fun memoryConfirm(candidatePayload: String, now: Instant): PendingAction =
        PendingAction(
            id = UUID.randomUUID().toString(),
            type = PendingActionType.CONFIRM_MEMORY,
            payload = candidatePayload,
            state = PendingActionState.WAITING_CONFIRMATION,
            createdAt = now,
            expiresAt = now.plusSeconds(MEMORY_TTL_HOURS * 3600)
        )

    fun decodeReminder(payload: String): ReminderPendingCommand.ScheduleReminder? {
        val json = JsonExtractor.extractObject(payload) ?: payload
        val title = JsonExtractor.stringField(json, "title") ?: return null
        val triggerAt = JsonExtractor.longField(json, "triggerAt") ?: return null
        val repeating = JsonExtractor.booleanField(json, "repeating") ?: false
        val repeatMs = JsonExtractor.longField(json, "repeatMs")
        val humanTime = JsonExtractor.stringField(json, "humanTime") ?: ""
        val precisionRaw = JsonExtractor.stringField(json, "precision")
        val precision = precisionRaw?.let {
            runCatching { com.care.voice.brain.reminder.ReminderPrecision.valueOf(it) }.getOrNull()
        } ?: com.care.voice.brain.reminder.ReminderPrecision.EXACT
        return ReminderPendingCommand.ScheduleReminder(
            title = title,
            triggerAtEpochMillis = triggerAt,
            isRepeating = repeating,
            repeatIntervalMillis = repeatMs,
            humanReadableTime = humanTime,
            precision = precision
        )
    }

    fun isExpired(action: PendingAction, now: Instant): Boolean =
        action.state == PendingActionState.WAITING_CONFIRMATION && now.isAfter(action.expiresAt)

    private fun jsonStr(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
}
