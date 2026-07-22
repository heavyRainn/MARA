package com.care.voice.platform.android.llm

import com.care.voice.brain.llm.LlmMessage
import com.care.voice.brain.llm.LlmRequest
import com.care.voice.brain.llm.LlmRole
import com.care.voice.data.net.ChatRequest
import com.care.voice.data.net.Message

internal fun LlmRequest.toNetwork(model: String): ChatRequest =
    ChatRequest(
        model = model,
        messages = messages.map { it.toNetwork() },
        stream = false,
        temperature = temperature
    )

internal fun LlmMessage.toNetwork(): Message =
    Message(role = role.toWire(), content = content)

internal fun LlmRole.toWire(): String = when (this) {
    LlmRole.SYSTEM -> "system"
    LlmRole.USER -> "user"
    LlmRole.ASSISTANT -> "assistant"
}
