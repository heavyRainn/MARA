package com.care.voice.data.history

import com.care.voice.brain.llm.LlmRole
import com.care.voice.brain.memory.MemoryMessage
import com.care.voice.brain.memory.MessageState
import java.util.UUID

class ChatHistoryRepository(
    private val dao: MessagesDao,
    private val maxPerSession: Int = 200
) {

    suspend fun append(sessionId: String, role: String, content: String) {
        dao.insert(
            MessageEntity(
                sessionId = sessionId,
                messageUid = UUID.randomUUID().toString(),
                role = role,
                content = content
            )
        )
        dao.pruneSession(sessionId, keep = maxPerSession)
    }

    suspend fun append(sessionId: String, message: MemoryMessage) {
        dao.insert(message.toEntity(sessionId))
        dao.pruneSession(sessionId, keep = maxPerSession)
    }

    suspend fun tail(sessionId: String, limit: Int): List<MessageEntity> =
        dao.lastN(sessionId, limit).asReversed()

    suspend fun activeTail(sessionId: String, limit: Int): List<MessageEntity> =
        dao.lastActiveN(sessionId, limit).asReversed()

    suspend fun archiveOldActive(sessionId: String, keepActive: Int) =
        dao.archiveOldActive(sessionId, keepActive)

    suspend fun count(sessionId: String): Int =
        dao.count(sessionId)

    suspend fun countActive(sessionId: String): Int =
        dao.countActive(sessionId)

    suspend fun deleteOldExceptLast(sessionId: String, keep: Int) =
        dao.deleteOldExceptLast(sessionId, keep)

    suspend fun messagesForSummary(sessionId: String, afterMessageId: String?): List<MessageEntity> =
        dao.messagesForSummary(sessionId, afterMessageId)

    suspend fun getByUid(messageUid: String): MessageEntity? =
        dao.getByUid(messageUid)

    private fun MemoryMessage.toEntity(sessionId: String): MessageEntity = MessageEntity(
        sessionId = sessionId,
        messageUid = id,
        role = when (role) {
            LlmRole.SYSTEM -> "system"
            LlmRole.ASSISTANT -> "assistant"
            else -> "user"
        },
        content = content,
        ts = timestampMillis.takeIf { it > 0L } ?: System.currentTimeMillis(),
        state = state.name
    )
}
