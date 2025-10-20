package com.care.voice.data.history

class ChatHistoryRepository(
    private val dao: MessagesDao,
    private val maxPerSession: Int = 200     // ограничение на рост
) {
    suspend fun append(sessionId: String, role: String, content: String) {
        dao.insert(MessageEntity(sessionId = sessionId, role = role, content = content))
        dao.pruneSession(sessionId, keep = maxPerSession)
    }

    /** Последние [limit] реплик в хронологическом порядке (user/assistant). */
    suspend fun tail(sessionId: String, limit: Int): List<MessageEntity> =
        dao.lastN(sessionId, limit).asReversed()

    suspend fun clear(sessionId: String) = dao.clearSession(sessionId)
}
