package com.care.voice.brain.context

import com.care.voice.brain.llm.LlmMessage
import com.care.voice.brain.llm.LlmRole
import com.care.voice.brain.memory.ConversationMemory
import com.care.voice.brain.memory.MemoryMessage
import com.care.voice.brain.memory.fact.MemoryConfirmationStatus
import com.care.voice.brain.memory.fact.MemoryFact
import com.care.voice.brain.memory.fact.MemorySensitivity
import com.care.voice.brain.memory.fact.MemoryType

class ContextBuilder {

    fun buildChatRequest(
        memory: ConversationMemory,
        userText: String,
        relevantFacts: List<MemoryFact> = emptyList(),
        currentMessageOverrides: List<String> = emptyList()
    ): List<LlmMessage> = buildList {
        add(LlmMessage(LlmRole.SYSTEM, SYSTEM_PROMPT))
        add(LlmMessage(LlmRole.SYSTEM, MEMORY_SAFETY_RULES))

        if (currentMessageOverrides.isNotEmpty()) {
            add(LlmMessage(LlmRole.SYSTEM, currentMessageOverrides.joinToString("\n")))
        }

        val memoryBlock = formatRelevantFacts(relevantFacts)
        if (memoryBlock.isNotBlank()) {
            add(LlmMessage(LlmRole.SYSTEM, memoryBlock))
        }

        memory.summary?.takeIf { it.isNotBlank() }?.let {
            add(LlmMessage(LlmRole.SYSTEM, "Краткое резюме текущей сессии:\n$it"))
        }

        memory.recentMessages.forEach { message ->
            add(LlmMessage(message.role, message.content))
        }
        add(LlmMessage(LlmRole.USER, userText))
    }

    /** @deprecated use buildChatRequest with relevantFacts */
    fun buildChatRequest(memory: ConversationMemory, userText: String): List<LlmMessage> =
        buildChatRequest(memory, userText, relevantFacts = emptyList())

    fun profileContext(profile: com.care.voice.brain.memory.UserProfile): String {
        if (profile == com.care.voice.brain.memory.UserProfile()) return ""
        return buildString {
            append("Факты о пользователе:\n")
            profile.name?.let { append("Имя: $it\n") }
            profile.age?.let { append("Возраст: $it\n") }
            profile.conditions?.let { append("Заболевания: $it\n") }
            profile.medications?.let { append("Лекарства: $it\n") }
            profile.notes?.let { append("Дополнительно: $it\n") }
        }.trimEnd()
    }

    fun currentMessagePriorityNote(userText: String): List<String> = listOf(
        "ТЕКУЩЕЕ СООБЩЕНИЕ ПОЛЬЗОВАТЕЛЯ ИМЕЕТ ПРИОРИТЕТ НАД СОХРАНЁННОЙ ПАМЯТЬЮ.",
        "Если текущее сообщение противоречит памяти — следуй текущему сообщению и при необходимости уточни."
    )

    fun formatRelevantFacts(facts: List<MemoryFact>): String {
        if (facts.isEmpty()) return ""
        val confirmed = facts.filter {
            it.confirmationStatus != MemoryConfirmationStatus.REQUIRES_CONFIRMATION
        }
        if (confirmed.isEmpty()) return ""

        val profileLines = confirmed
            .filter { it.type != MemoryType.EPISODIC && it.type != MemoryType.LOCATION }
            .take(8)
            .map { formatFactLine(it) }

        val temporal = confirmed
            .filter { it.type == MemoryType.LOCATION || it.validUntil != null }
            .take(3)
            .map { formatFactLine(it) }

        return buildString {
            if (profileLines.isNotEmpty()) {
                append("ПОДТВЕРЖДЁННАЯ ПАМЯТЬ О ПОЛЬЗОВАТЕЛЕ:\n")
                profileLines.forEach { append("- ").append(it).append('\n') }
            }
            if (temporal.isNotEmpty()) {
                append("\nВРЕМЕННЫЙ КОНТЕКСТ:\n")
                temporal.forEach { append("- ").append(it).append('\n') }
            }
            append("\n").append(MEMORY_USAGE_RULES)
        }.trim()
    }

    private fun formatFactLine(fact: MemoryFact): String = when (fact.type) {
        MemoryType.IDENTITY -> "Пользователя зовут ${fact.value}."
        MemoryType.ALLERGY -> "Пользователь ранее сообщил об аллергии: ${fact.value}."
        MemoryType.MEDICATION, MemoryType.MEDICATION_DOSAGE ->
            "Пользователь ранее сообщил о лекарстве: ${fact.value}."
        MemoryType.COMMUNICATION_PREFERENCE -> "Пользователь просит: ${fact.value}."
        MemoryType.LOCATION -> "Пользователь сообщил о местоположении: ${fact.value}."
        else -> fact.value
    }

    companion object {
        const val SYSTEM_PROMPT =
            "Ты доброжелательный голосовой помощник для пожилых людей. " +
                "Отвечай просто и кратко. ОТВЕЧАЙ ТОЛЬКО НА РУССКОМ ЯЗЫКЕ. " +
                "Не обещай создать напоминание, если система явно не сообщила, что оно создано."

        const val MEMORY_SAFETY_RULES =
            "Память о лекарствах, диагнозах и аллергиях отражает только то, что ранее сообщил пользователь. " +
                "Она не является медицинским назначением. " +
                "Не давай указаний принять, отменить или изменить лекарство только на основании памяти."

        const val MEMORY_USAGE_RULES =
            "ПРАВИЛА ИСПОЛЬЗОВАНИЯ ПАМЯТИ:\n" +
                "- Используй только сведения, относящиеся к текущему вопросу.\n" +
                "- Текущее сообщение пользователя важнее сохранённой памяти.\n" +
                "- При конфликте не угадывай: уточни актуальную информацию.\n" +
                "- Медицинская память не является диагнозом или назначением.\n" +
                "- Не сообщай пользователю внутренние поля confidence, score или status."
    }
}
