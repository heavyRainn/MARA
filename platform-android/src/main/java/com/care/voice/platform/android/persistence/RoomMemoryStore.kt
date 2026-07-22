package com.care.voice.platform.android.persistence

import com.care.voice.brain.memory.ConversationMemory
import com.care.voice.brain.memory.ConversationSummary
import com.care.voice.brain.memory.MemoryMessage
import com.care.voice.brain.memory.MemoryStore
import com.care.voice.brain.memory.UserProfile
import com.care.voice.data.history.ChatHistoryRepository
import com.care.voice.data.repository.ChatSummaryDao
import com.care.voice.data.repository.UserProfileDao

/**
 * Room-backed implementation of [MemoryStore] (legacy facade + v2 compatibility).
 */
class RoomMemoryStore(
    private val history: ChatHistoryRepository,
    private val userProfileDao: UserProfileDao,
    private val summaryDao: ChatSummaryDao,
    private val historyTail: Int = 8
) : MemoryStore {

    override suspend fun loadConversationContext(sessionId: String, tailSize: Int): ConversationMemory {
        val profile = userProfileDao.get()?.toBrain() ?: UserProfile()
        val summaryEntity = summaryDao.get(sessionId)
        val recentMessages = history.activeTail(sessionId, tailSize).map { it.toBrain() }
        return ConversationMemory(
            sessionId = sessionId,
            recentMessages = recentMessages,
            summary = summaryEntity?.summary,
            messageCountAtSummary = summaryEntity?.messageCountAtSummary ?: 0,
            profile = profile
        )
    }

    override suspend fun saveMessage(sessionId: String, message: MemoryMessage) {
        history.append(sessionId, message)
    }

    override suspend fun saveSummary(summary: ConversationSummary) {
        summaryDao.save(summary.toEntity())
    }

    override suspend fun loadUserProfile(): UserProfile =
        userProfileDao.get()?.toBrain() ?: UserProfile()

    override suspend fun saveUserProfile(profile: UserProfile) {
        userProfileDao.save(profile.toEntity())
    }

    override suspend fun countMessages(sessionId: String): Int =
        history.countActive(sessionId)

    override suspend fun pruneOldMessages(sessionId: String, keep: Int) {
        history.archiveOldActive(sessionId, keep)
    }

    override suspend fun archiveOldMessages(sessionId: String, keepActive: Int) {
        history.archiveOldActive(sessionId, keepActive)
    }

    override suspend fun recentMessagesForSummary(sessionId: String, limit: Int): List<MemoryMessage> =
        history.tail(sessionId, limit).map { it.toBrain() }
}
