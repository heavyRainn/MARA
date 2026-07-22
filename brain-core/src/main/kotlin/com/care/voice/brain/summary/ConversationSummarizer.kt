package com.care.voice.brain.summary

import com.care.voice.brain.llm.LanguageModel
import com.care.voice.brain.llm.LlmMessage
import com.care.voice.brain.llm.LlmRequest
import com.care.voice.brain.llm.LlmResult
import com.care.voice.brain.llm.LlmRole
import com.care.voice.brain.memory.ConversationSummary
import com.care.voice.brain.memory.MemoryStore

class ConversationSummarizer(
    private val languageModel: LanguageModel,
    private val memoryStore: MemoryStore,
    private val summaryThreshold: Int = 20,
    private val historyTail: Int = 8,
    private val recentForSummary: Int = 20
) {
    suspend fun updateIfNeeded(sessionId: String) {
        val messageCount = memoryStore.countMessages(sessionId)
        val memory = memoryStore.loadConversationContext(sessionId, historyTail)
        if (messageCount - memory.messageCountAtSummary < summaryThreshold) return

        val recentText = memoryStore.recentMessagesForSummary(sessionId, recentForSummary)
            .joinToString("\n") { message ->
                val role = when (message.role) {
                    LlmRole.USER -> "user"
                    LlmRole.ASSISTANT -> "assistant"
                    LlmRole.SYSTEM -> "system"
                }
                "$role: ${message.content}"
            }

        val prompt = """
            У тебя есть предыдущее резюме диалога:
            ${memory.summary.orEmpty()}

            И новые сообщения:
            $recentText

            Обнови резюме кратко, сохрани важные факты, договоренности, напоминания и личные данные пользователя.
            Верни только новый текст резюме.
        """.trimIndent()

        val result = languageModel.generate(
            LlmRequest(
                messages = listOf(LlmMessage(LlmRole.SYSTEM, prompt)),
                temperature = 0.2
            )
        )
        if (result is LlmResult.Failure) return

        val newSummary = (result as LlmResult.Success).value.content.trim()
        if (newSummary.isBlank()) return
        memoryStore.saveSummary(
            ConversationSummary(
                sessionId = sessionId,
                text = newSummary,
                messageCountAtSummary = messageCount
            )
        )
        memoryStore.archiveOldMessages(sessionId, historyTail)
    }
}
