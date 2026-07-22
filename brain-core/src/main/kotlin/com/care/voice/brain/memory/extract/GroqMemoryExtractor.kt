package com.care.voice.brain.memory.extract

import com.care.voice.brain.llm.LanguageModel
import com.care.voice.brain.llm.LlmMessage
import com.care.voice.brain.llm.LlmRequest
import com.care.voice.brain.llm.LlmResult
import com.care.voice.brain.llm.LlmRole
import com.care.voice.brain.memory.MemoryMessage
import com.care.voice.brain.memory.fact.MemoryCandidate
import java.time.Instant

class GroqMemoryExtractor(
    private val languageModel: LanguageModel,
    private val nowProvider: () -> Instant = { Instant.now() }
) : MemoryExtractor {

    override suspend fun extract(
        userMessage: MemoryMessage,
        recentContext: List<MemoryMessage>
    ): List<MemoryCandidate> {
        if (userMessage.role != LlmRole.USER) return emptyList()

        val contextBlock = recentContext.takeLast(4).joinToString("\n") { msg ->
            "${msg.role.name.lowercase()}: ${msg.content.take(120)}"
        }

        val prompt = """
            Извлеки устойчивые факты ТОЛЬКО из ПРЯМЫХ утверждений пользователя в последнем сообщении.
            Не извлекай из ответов ассистента, цитат, статей, вопросов, гипотез, рассказов о других людях как факты пользователя.
            Не диагностируй. Не придумывай дозировки. Сохраняй оригинальное написание в value.
            Учитывай отрицания и отмену информации.
            Если новых устойчивых фактов нет — верни один объект с operation NOOP.

            Ответ строго JSON-массив объектов:
            [
              {
                "operation": "ADD|UPDATE|DELETE|NOOP",
                "subjectType": "USER|RELATED_PERSON|UNKNOWN",
                "relation": string | null,
                "subjectName": string | null,
                "type": "IDENTITY|PREFERENCE|...",
                "key": string,
                "value": string | null,
                "confidence": 0.0-1.0,
                "sensitivity": "NORMAL|PERSONAL|HEALTH|CRITICAL_HEALTH",
                "requiresConfirmation": boolean,
                "validFromEpochMillis": number | null,
                "validUntilEpochMillis": number | null,
                "reason": string
              }
            ]

            Недавний контекст:
            $contextBlock

            Последнее сообщение пользователя:
            ${userMessage.content}
        """.trimIndent()

        val result = languageModel.generate(
            LlmRequest(
                messages = listOf(
                    LlmMessage(LlmRole.SYSTEM, prompt),
                    LlmMessage(LlmRole.USER, userMessage.content)
                ),
                temperature = 0.0
            )
        )
        if (result is LlmResult.Failure) return emptyList()
        return MemoryCandidateParser.parse((result as LlmResult.Success).value.content, nowProvider())
    }
}
