package com.care.voice.platform.android.persistence

import com.care.voice.brain.llm.LlmRole
import com.care.voice.brain.memory.ConversationSummary as BrainConversationSummary
import com.care.voice.brain.memory.MemoryMessage
import com.care.voice.brain.memory.UserProfile
import com.care.voice.data.history.ChatSummaryEntity
import com.care.voice.data.history.MessageEntity
import com.care.voice.data.history.UserProfileEntity

internal fun MessageEntity.toBrain(): MemoryMessage =
    MemoryMessage(
        id = messageUid.ifBlank { "legacy-$id" },
        role = role.toLlmRole(),
        content = content,
        timestampMillis = ts,
        state = state.toMessageState()
    )

internal fun MemoryMessage.toEntity(sessionId: String): MessageEntity =
    MessageEntity(
        sessionId = sessionId,
        messageUid = id,
        role = role.toStorageRole(),
        content = content,
        ts = timestampMillis.takeIf { it > 0L } ?: System.currentTimeMillis(),
        state = state.toStorage()
    )

internal fun UserProfileEntity.toBrain(): UserProfile =
    UserProfile(
        name = name,
        age = age,
        conditions = conditions,
        medications = medications,
        notes = notes
    )

internal fun UserProfile.toEntity(): UserProfileEntity =
    UserProfileEntity(
        id = 1,
        name = name,
        age = age,
        conditions = conditions,
        medications = medications,
        notes = notes
    )

internal fun ChatSummaryEntity.toBrain(): BrainConversationSummary =
    BrainConversationSummary(
        sessionId = sessionId,
        text = summary,
        messageCountAtSummary = messageCountAtSummary
    )

internal fun BrainConversationSummary.toEntity(): ChatSummaryEntity =
    ChatSummaryEntity(
        sessionId = sessionId,
        summary = text,
        messageCountAtSummary = messageCountAtSummary
    )

private fun String.toLlmRole(): LlmRole = when (this) {
    "system" -> LlmRole.SYSTEM
    "assistant" -> LlmRole.ASSISTANT
    else -> LlmRole.USER
}

private fun LlmRole.toStorageRole(): String = when (this) {
    LlmRole.SYSTEM -> "system"
    LlmRole.USER -> "user"
    LlmRole.ASSISTANT -> "assistant"
}
