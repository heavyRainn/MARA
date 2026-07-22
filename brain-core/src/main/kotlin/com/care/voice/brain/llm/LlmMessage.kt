package com.care.voice.brain.llm

data class LlmMessage(
    val role: LlmRole,
    val content: String
)
