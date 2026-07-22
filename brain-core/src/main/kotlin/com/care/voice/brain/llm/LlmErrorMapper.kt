package com.care.voice.brain.llm

import com.care.voice.brain.AssistantError

fun LlmError.toAssistantError(): AssistantError = when (this) {
    is LlmError.Network -> AssistantError.NetworkUnavailable
    is LlmError.Unauthorized,
    is LlmError.RateLimited,
    is LlmError.Server -> AssistantError.LanguageModelUnavailable
    is LlmError.Unknown -> AssistantError.Unexpected(message)
}

fun LlmError.toUserMessage(): String = message
