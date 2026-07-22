package com.care.voice.brain

sealed interface AssistantInput {
    data class UserMessage(val text: String) : AssistantInput
    data class ConfirmationReceived(val accepted: Boolean) : AssistantInput
}
