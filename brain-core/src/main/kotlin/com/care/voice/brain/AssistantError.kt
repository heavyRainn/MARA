package com.care.voice.brain

sealed interface AssistantError {
    data object NetworkUnavailable : AssistantError
    data object LanguageModelUnavailable : AssistantError
    data object MemoryUnavailable : AssistantError
    data object InvalidModelResponse : AssistantError
    data object InvalidReminder : AssistantError
    data object ActionExecutionFailed : AssistantError

    data class Unexpected(val debugMessage: String? = null) : AssistantError
}
