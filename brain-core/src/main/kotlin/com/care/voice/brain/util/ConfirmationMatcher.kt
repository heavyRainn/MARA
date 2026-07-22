package com.care.voice.brain.util

object ConfirmationMatcher {
    fun isYes(text: String): Boolean {
        val t = text.lowercase().trim()
        return t in YES_WORDS ||
            t.startsWith("да ") ||
            t.contains("поставь") ||
            t.contains("подтверждаю")
    }

    fun isNo(text: String): Boolean {
        val t = text.lowercase().trim()
        return t in NO_WORDS ||
            t.startsWith("нет ") ||
            t.contains("не надо") ||
            t.contains("отмени") ||
            t.contains("не ставь")
    }

    private val YES_WORDS = setOf(
        "да", "ага", "угу", "конечно", "подтверждаю", "ставь", "поставь", "хорошо", "ок", "окей"
    )

    private val NO_WORDS = setOf(
        "нет", "не", "не надо", "отмена", "отмени", "не ставь"
    )
}
