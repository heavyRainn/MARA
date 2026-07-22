package com.care.voice.brain.memory

/**
 * Local-first persistent memory for conversations and user facts.
 */
interface MemoryStore {
    suspend fun loadConversationContext(sessionId: String, tailSize: Int = 8): ConversationMemory

    suspend fun saveMessage(sessionId: String, message: MemoryMessage)

    suspend fun saveSummary(summary: ConversationSummary)

    suspend fun loadUserProfile(): UserProfile

    suspend fun saveUserProfile(profile: UserProfile)

    suspend fun countMessages(sessionId: String): Int

    suspend fun pruneOldMessages(sessionId: String, keep: Int)

    suspend fun archiveOldMessages(sessionId: String, keepActive: Int)

    suspend fun recentMessagesForSummary(sessionId: String, limit: Int): List<MemoryMessage>
}
