package com.care.voice.platform.android.persistence

import com.care.voice.brain.memory.*
import com.care.voice.brain.memory.fact.ForgetMemoryRequest
import com.care.voice.brain.memory.fact.MemoryFact
import com.care.voice.brain.memory.fact.MemoryFactSource
import com.care.voice.brain.memory.fact.MemoryMutation
import com.care.voice.brain.memory.fact.MemoryQuery
import com.care.voice.brain.memory.fact.MemoryStatus
import com.care.voice.brain.memory.fact.MemorySubject
import com.care.voice.brain.memory.fact.MemoryTombstone
import com.care.voice.brain.memory.fact.MemoryType
import com.care.voice.brain.memory.retrieve.MemoryRetriever
import com.care.voice.data.history.ChatHistoryRepository
import com.care.voice.data.repository.ChatSummaryDao
import com.care.voice.data.repository.MemoryFactDao
import com.care.voice.data.repository.UserProfileDao
import java.util.UUID

class RoomConversationRepository(
    private val history: ChatHistoryRepository
) : ConversationRepository {

    override suspend fun saveMessage(sessionId: String, message: MemoryMessage): MemoryMessage {
        history.append(sessionId, message)
        return message
    }

    override suspend fun loadTail(sessionId: String, limit: Int): List<MemoryMessage> =
        history.activeTail(sessionId, limit).map { it.toBrain() }

    override suspend fun archiveOldMessages(sessionId: String, keepActive: Int) {
        history.archiveOldActive(sessionId, keepActive)
    }

    override suspend fun loadMessagesForSummary(sessionId: String, afterMessageId: String?): List<MemoryMessage> =
        history.messagesForSummary(sessionId, afterMessageId).map { it.toBrain() }

    override suspend fun countActiveMessages(sessionId: String): Int =
        history.countActive(sessionId)

    override suspend fun getMessageById(messageId: String): MemoryMessage? =
        history.getByUid(messageId)?.toBrain()
}

class RoomMemoryRepository(
    private val memoryFactDao: MemoryFactDao
) : MemoryRepository {

    private val retriever = MemoryRetriever()

    override suspend fun findActiveByKey(
        subject: MemorySubject,
        type: MemoryType,
        key: String
    ): List<MemoryFact> {
        val (subjectType, relation, _) = subject.toStorage()
        return memoryFactDao.findActiveByKey(subjectType, relation, type.name, key).map { it.toBrain() }
    }

    override suspend fun findRelevant(query: MemoryQuery): List<MemoryFact> {
        val active = memoryFactDao.findAllActive().map { it.toBrain() }
        return retriever.rank(active, query)
    }

    override suspend fun applyMutation(mutation: MemoryMutation) {
        val inserts = mutableListOf<com.care.voice.data.history.MemoryFactEntity>()
        val updates = mutableListOf<com.care.voice.data.history.MemoryFactEntity>()
        val sources = mutableListOf<com.care.voice.data.history.MemoryFactSourceEntity>()

        mutation.supersededFactId?.let { oldId ->
            memoryFactDao.getById(oldId)?.let { old ->
                updates.add(old.copy(status = MemoryStatus.SUPERSEDED.name, updatedAt = System.currentTimeMillis()))
            }
        }

        mutation.fact?.let { fact ->
            when (mutation.operation) {
                com.care.voice.brain.memory.fact.MemoryOperation.DELETE ->
                    updates.add(fact.toEntity())
                else -> inserts.add(fact.toEntity())
            }
        }

        mutation.source?.let { sources.add(it.toEntity()) }

        memoryFactDao.applyMutationTransaction(inserts, updates, sources, emptyList())
    }

    override suspend fun getSources(memoryFactId: String): List<MemoryFactSource> =
        memoryFactDao.getSources(memoryFactId).map { it.toBrain() }

    override suspend fun forget(request: ForgetMemoryRequest) {
        val (subjectType, relation, _) = request.subject.toStorage()
        val key = request.key ?: request.valueHint ?: return
        val facts = memoryFactDao.findActiveByKey(
            subjectType,
            relation,
            request.type?.name ?: MemoryType.PREFERENCE.name,
            key
        )
        val now = request.now.toEpochMilli()
        val tombstone = MemoryTombstone(
            id = UUID.randomUUID().toString(),
            subject = request.subject,
            type = request.type ?: MemoryType.PREFERENCE,
            key = key,
            valueHash = request.valueHint?.hashCode()?.toString(),
            createdAt = request.now,
            reason = request.reason
        )
        val updates = facts.map { entity ->
            entity.copy(status = MemoryStatus.DELETED.name, updatedAt = now)
        }
        memoryFactDao.applyMutationTransaction(
            insertFacts = emptyList(),
            updateFacts = updates,
            sources = emptyList(),
            tombstones = listOf(tombstone.toEntity())
        )
    }

    override suspend fun hasTombstone(
        subject: MemorySubject,
        type: MemoryType,
        key: String,
        valueHash: String?
    ): Boolean {
        val (subjectType, _, _) = subject.toStorage()
        return memoryFactDao.countTombstone(subjectType, type.name, key, valueHash) > 0
    }
}

class RoomSummaryRepository(
    private val summaryDao: com.care.voice.data.repository.ChatSummaryDao
) : SummaryRepository {
    override suspend fun load(sessionId: String): ConversationSummary? =
        summaryDao.get(sessionId)?.toBrain()

    override suspend fun save(summary: ConversationSummary) {
        summaryDao.save(summary.toEntity())
    }
}
