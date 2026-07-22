package com.care.voice.brain.reminder

/**
 * Rule-based pre-filter before LLM reminder extraction.
 * Blocks obvious non-creation intents; allows likely creation intents through to LLM.
 */
object ReminderIntentGate {

    enum class Decision {
        /** Definitely not a reminder creation request — skip LLM. */
        REJECT,
        /** Likely a reminder creation request — call LLM extractor. */
        ALLOW_LLM
    }

    fun evaluate(userText: String): Decision {
        val text = userText.trim().lowercase().replace('ё', 'е')
        if (text.isBlank()) return Decision.REJECT

        if (NEGATIVE_PATTERNS.any { text.contains(it) }) return Decision.REJECT

        if (POSITIVE_PATTERNS.any { text.contains(it) }) return Decision.ALLOW_LLM

        if (TIME_HINT_PATTERNS.any { Regex(it).containsMatchIn(text) }) return Decision.ALLOW_LLM

        return Decision.REJECT
    }

    private val NEGATIVE_PATTERNS = listOf(
        "не создавай напоминание",
        "не ставь напоминание",
        "не надо напоминание",
        "не нужно напоминание",
        "как поставить напоминание",
        "как создать напоминание",
        "как работают напоминания",
        "расскажи, как поставить",
        "расскажи как поставить",
        "я уже поставил",
        "я уже поставила",
        "уже поставил напоминание",
        "уже поставила напоминание",
        "напомни, как назывался",
        "напомни как назывался",
        "напомни, что мы обсуждали",
        "напомни что мы обсуждали",
        "напомни, о чем мы говорили",
        "напомни о чем мы говорили",
        "напомни, что я говорил",
        "напомни что я говорил",
        "напомни, что я говорила",
        "напомни что я говорила"
    )

    private val POSITIVE_PATTERNS = listOf(
        "поставь напоминание",
        "создай напоминание",
        "поставь будильник",
        "не дай забыть",
        "не дай мне забыть",
        "не забыть принять",
        "напомни мне",
        "напомни через",
        "напомни завтра",
        "напомни сегодня",
        "напомни послезавтра",
        "напомни в ",
        "напомни утром",
        "напомни вечером"
    )

    private val TIME_HINT_PATTERNS = listOf(
        "через\\s+\\d+\\s*(мин|минут|час|часа|часов|день|дня|дней|нед)",
        "через\\s+полтора\\s+час",
        "через\\s+неделю",
        "завтра\\s+в\\s+\\d",
        "сегодня\\s+в\\s+\\d",
        "в\\s+\\d{1,2}[:.]\\d{2}",
        "каждый\\s+день"
    )
}
