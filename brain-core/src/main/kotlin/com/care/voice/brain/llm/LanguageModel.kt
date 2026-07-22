package com.care.voice.brain.llm

/**
 * Portable contract for text generation backends (Groq, local model, remote gateway, etc.).
 */
interface LanguageModel {
    suspend fun generate(request: LlmRequest): LlmResult<LlmResponse>
}
