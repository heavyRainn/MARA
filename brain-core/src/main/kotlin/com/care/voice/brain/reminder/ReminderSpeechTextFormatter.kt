package com.care.voice.brain.reminder

object ReminderSpeechTextFormatter {

    private const val MAX_SPEECH_LENGTH = 250
    private const val DEFAULT_USER_NAME = "Владимир"

    fun format(content: String, userName: String = DEFAULT_USER_NAME): String {
        val normalized = content
            .trim()
            .replace(Regex("\\s+"), " ")
            .trimEnd('.', '!', '?', ',', ';', ':')
        if (normalized.isBlank()) return ""

        val truncated = if (normalized.length > MAX_SPEECH_LENGTH) {
            normalized.take(MAX_SPEECH_LENGTH).trimEnd() + "…"
        } else {
            normalized
        }
        return "$userName. Напоминаю: $truncated."
    }

    fun contentLengthForLog(content: String): Int = content.trim().length
}
