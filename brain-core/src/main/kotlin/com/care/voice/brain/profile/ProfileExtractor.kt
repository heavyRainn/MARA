package com.care.voice.brain.profile

import com.care.voice.brain.llm.LanguageModel
import com.care.voice.brain.llm.LlmMessage
import com.care.voice.brain.llm.LlmRequest
import com.care.voice.brain.llm.LlmResult
import com.care.voice.brain.llm.LlmRole
import com.care.voice.brain.memory.UserProfile
import com.care.voice.brain.util.JsonExtractor

class ProfileExtractor(
    private val languageModel: LanguageModel
) {
    suspend fun extractAndMerge(text: String, current: UserProfile): UserProfile? {
        val prompt = """
            Извлеки факты о пользователе из текста.
            Ответ строго в JSON:
            {
              "name": String | null,
              "age": Int | null,
              "conditions": String | null,
              "medications": String | null,
              "notes": String | null
            }
            Если данных нет — null.
            Текст:
            $text
        """.trimIndent()

        val result = languageModel.generate(
            LlmRequest(
                messages = listOf(
                    LlmMessage(LlmRole.SYSTEM, prompt),
                    LlmMessage(LlmRole.USER, text)
                ),
                temperature = 0.0
            )
        )
        if (result is LlmResult.Failure) return null

        val json = JsonExtractor.extractObject((result as LlmResult.Success).value.content) ?: return null
        return UserProfile(
            name = JsonExtractor.stringField(json, "name") ?: current.name,
            age = JsonExtractor.intField(json, "age") ?: current.age,
            conditions = JsonExtractor.stringField(json, "conditions") ?: current.conditions,
            medications = JsonExtractor.stringField(json, "medications") ?: current.medications,
            notes = JsonExtractor.stringField(json, "notes") ?: current.notes
        )
    }
}
