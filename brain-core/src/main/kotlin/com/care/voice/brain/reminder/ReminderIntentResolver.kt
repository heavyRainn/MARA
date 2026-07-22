package com.care.voice.brain.reminder

import com.care.voice.brain.llm.LanguageModel
import com.care.voice.brain.llm.LlmMessage
import com.care.voice.brain.llm.LlmRequest
import com.care.voice.brain.llm.LlmResult
import com.care.voice.brain.llm.LlmRole
import com.care.voice.brain.util.JsonExtractor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ReminderIntent(
    val text: String,
    val timeExpression: String?,
    val repeatExpression: String?
)

class ReminderIntentResolver(
    private val languageModel: LanguageModel,
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) {
    suspend fun resolve(userText: String): ReminderIntent? {
        if (ReminderIntentGate.evaluate(userText) == ReminderIntentGate.Decision.REJECT) {
            return null
        }

        val nowText = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale("ru", "RU")).format(Date(nowMillis()))
        val systemPrompt = """
            Сейчас: $nowText
            Язык пользователя: русский.

            Определи, просит ли пользователь СОЗДАТЬ напоминание.
            Если это не просьба создать напоминание — верни строго null.

            Если это просьба создать напоминание — верни ТОЛЬКО JSON без markdown и пояснений:
            {
              "isReminder": true,
              "text": "короткий текст напоминания без слова напомни",
              "timeExpression": "человеческое выражение времени из сообщения",
              "repeatExpression": "выражение повтора или null"
            }

            Правила:
            - НЕ считай delayMillis.
            - НЕ придумывай дату, если времени нет.
            - Для "каждый день в 9" timeExpression = "в 9", repeatExpression = "каждый день".
            - Для "завтра утром" timeExpression = "завтра утром".
            - Для "через 10 минут" timeExpression = "через 10 минут".
            - Если пользователь просто обсуждает напоминания, но не просит создать — верни null.
        """.trimIndent()

        val result = languageModel.generate(
            LlmRequest(
                messages = listOf(
                    LlmMessage(LlmRole.SYSTEM, systemPrompt),
                    LlmMessage(LlmRole.USER, userText)
                ),
                temperature = 0.0
            )
        )
        if (result is LlmResult.Failure) return null

        val raw = (result as LlmResult.Success).value.content.trim()
        if (raw.equals("null", ignoreCase = true)) return null

        val json = JsonExtractor.extractObject(raw) ?: return null
        if (JsonExtractor.booleanField(json, "isReminder") != true) return null

        val text = JsonExtractor.stringField(json, "text")?.trim().orEmpty()
        if (text.length < 3) return null

        return ReminderIntent(
            text = text,
            timeExpression = JsonExtractor.stringField(json, "timeExpression"),
            repeatExpression = JsonExtractor.stringField(json, "repeatExpression")
        )
    }
}
