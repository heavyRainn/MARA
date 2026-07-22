package com.care.voice.brain.memory

import com.care.voice.brain.llm.LlmRole
import com.care.voice.brain.memory.fact.ForgetMemoryRequest
import com.care.voice.brain.memory.fact.MemoryFact
import com.care.voice.brain.memory.fact.MemoryFactSource
import com.care.voice.brain.memory.fact.MemoryMutation
import com.care.voice.brain.memory.fact.MemoryQuery
import com.care.voice.brain.memory.fact.MemorySubject
import com.care.voice.brain.memory.fact.MemoryStatus
import com.care.voice.brain.memory.fact.MemoryType
import java.util.concurrent.ConcurrentHashMap

class FakeConversationRepository : ConversationRepository {
    val messages = ConcurrentHashMap<String, MutableList<MemoryMessage>>()

    override suspend fun saveMessage(sessionId: String, message: MemoryMessage): MemoryMessage {
        messages.computeIfAbsent(sessionId) { mutableListOf() }.add(message)
        return message
    }

    override suspend fun loadTail(sessionId: String, limit: Int): List<MemoryMessage> {
        val all = messages[sessionId].orEmpty().filter { it.state == MessageState.ACTIVE }
        return if (limit <= 0) emptyList() else all.takeLast(limit)
    }

    override suspend fun archiveOldMessages(sessionId: String, keepActive: Int) {
        val list = messages[sessionId] ?: return
        val active = list.filter { it.state == MessageState.ACTIVE }
        if (active.size <= keepActive) return
        val archive = active.dropLast(keepActive).map { it.id }.toSet()
        messages[sessionId] = list.map {
            if (it.id in archive) it.copy(state = MessageState.ARCHIVED) else it
        }.toMutableList()
    }

    override suspend fun loadMessagesForSummary(sessionId: String, afterMessageId: String?): List<MemoryMessage> =
        messages[sessionId].orEmpty().filter { it.state != MessageState.DELETED }

    override suspend fun countActiveMessages(sessionId: String): Int =
        messages[sessionId]?.count { it.state == MessageState.ACTIVE } ?: 0

    override suspend fun getMessageById(messageId: String): MemoryMessage? =
        messages.values.flatten().firstOrNull { it.id == messageId }
}

class FakeMemoryRepository : MemoryRepository {
    val facts = mutableListOf<MemoryFact>()
    val tombstones = mutableSetOf<String>()

    override suspend fun findActiveByKey(subject: MemorySubject, type: MemoryType, key: String): List<MemoryFact> =
        facts.filter { it.subject == subject && it.type == type && it.key.equals(key, true) }

    override suspend fun findRelevant(query: MemoryQuery): List<MemoryFact> = facts

    override suspend fun applyMutation(mutation: MemoryMutation) {
        mutation.supersededFactId?.let { oldId ->
            if (mutation.fact?.id != oldId) {
                val idx = facts.indexOfFirst { it.id == oldId }
                if (idx >= 0) {
                    facts[idx] = facts[idx].copy(status = MemoryStatus.SUPERSEDED)
                }
            }
        }
        mutation.fact?.let { fact ->
            val idx = facts.indexOfFirst { it.id == fact.id }
            if (idx >= 0) facts[idx] = fact else facts.add(fact)
        }
    }

    override suspend fun getSources(memoryFactId: String): List<MemoryFactSource> = emptyList()

    override suspend fun forget(request: ForgetMemoryRequest) {
        tombstones.add("${request.type}:${request.valueHint}")
    }

    override suspend fun hasTombstone(subject: MemorySubject, type: MemoryType, key: String, valueHash: String?): Boolean =
        tombstones.isNotEmpty()
}
