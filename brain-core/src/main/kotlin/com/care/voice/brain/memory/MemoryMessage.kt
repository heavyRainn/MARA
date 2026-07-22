package com.care.voice.brain.memory

import com.care.voice.brain.llm.LlmRole
import java.util.UUID

data class MemoryMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: LlmRole,
    val content: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val state: MessageState = MessageState.ACTIVE
)
