package com.care.voice.brain

sealed interface AssistantResult {
    data class Reply(val text: String) : AssistantResult
    data class ConfirmationRequired(val text: String, val pendingActionId: String) : AssistantResult
    data class ActionCompleted(val text: String) : AssistantResult
    data class ActionCancelled(val text: String) : AssistantResult
    data class Failure(val error: AssistantError, val userMessage: String) : AssistantResult
}
