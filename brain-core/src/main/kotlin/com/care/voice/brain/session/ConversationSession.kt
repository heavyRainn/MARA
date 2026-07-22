package com.care.voice.brain.session

import java.time.Instant

data class ConversationSession(
    val id: String,
    val startedAt: Instant,
    val lastActivityAt: Instant,
    val excludeFromExtraction: Boolean = false
)

interface SessionManager {
    suspend fun currentSessionId(): String
    suspend fun touchActivity(now: Instant)
    suspend fun markExcludeFromExtraction(sessionId: String)
    suspend fun isExcludedFromExtraction(sessionId: String): Boolean
}
