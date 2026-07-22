package com.care.voice.brain.memory

import com.care.voice.brain.llm.LlmRole
import com.care.voice.brain.memory.MessageState
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory [MemoryStore] for JVM unit tests.
 */
class FakeMemoryStore : MemoryStore {

    private val messages = ConcurrentHashMap<String, MutableList<MemoryMessage>>()
    private val summaries = ConcurrentHashMap<String, ConversationSummary>()
    private var profile: UserProfile = UserProfile()

    override suspend fun loadConversationContext(sessionId: String, tailSize: Int): ConversationMemory {
        val all = messages[sessionId].orEmpty()
        val tail = if (tailSize <= 0) emptyList() else all.filter { it.state == MessageState.ACTIVE }.takeLast(tailSize)
        val summary = summaries[sessionId]
        return ConversationMemory(
            sessionId = sessionId,
            recentMessages = tail,
            summary = summary?.text,
            messageCountAtSummary = summary?.messageCountAtSummary ?: 0,
            profile = profile
        )
    }

    override suspend fun saveMessage(sessionId: String, message: MemoryMessage) {
        messages.computeIfAbsent(sessionId) { mutableListOf() }.add(message)
    }

    override suspend fun saveSummary(summary: ConversationSummary) {
        summaries[summary.sessionId] = summary
    }

    override suspend fun loadUserProfile(): UserProfile = profile

    override suspend fun saveUserProfile(profile: UserProfile) {
        this.profile = profile
    }

    override suspend fun countMessages(sessionId: String): Int =
        messages[sessionId]?.size ?: 0

    override suspend fun pruneOldMessages(sessionId: String, keep: Int) {
        archiveOldMessages(sessionId, keep)
    }

    override suspend fun archiveOldMessages(sessionId: String, keepActive: Int) {
        val list = messages[sessionId] ?: return
        if (list.size <= keepActive) return
        val active = list.filter { it.state == MessageState.ACTIVE }
        if (active.size <= keepActive) return
        val archiveIds = active.dropLast(keepActive).map { it.id }.toSet()
        messages[sessionId] = list.map { msg ->
            if (msg.id in archiveIds) msg.copy(state = MessageState.ARCHIVED) else msg
        }.toMutableList()
    }

    override suspend fun recentMessagesForSummary(sessionId: String, limit: Int): List<MemoryMessage> {
        val all = messages[sessionId].orEmpty()
        return if (limit <= 0) emptyList() else all.takeLast(limit)
    }
}
