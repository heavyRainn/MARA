package com.care.voice.brain.llm

data class LlmRequest(
    val messages: List<LlmMessage>,
    val temperature: Double = 0.3
)
