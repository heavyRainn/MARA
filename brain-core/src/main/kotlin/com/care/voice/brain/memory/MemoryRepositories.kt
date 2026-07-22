package com.care.voice.brain.memory

import com.care.voice.brain.memory.fact.ForgetMemoryRequest
import com.care.voice.brain.memory.fact.MemoryFact
import com.care.voice.brain.memory.fact.MemoryFactSource
import com.care.voice.brain.memory.fact.MemoryMutation
import com.care.voice.brain.memory.fact.MemoryQuery
import com.care.voice.brain.memory.fact.MemorySubject
import com.care.voice.brain.memory.fact.MemoryType

interface ConversationRepository {
    suspend fun saveMessage(sessionId: String, message: MemoryMessage): MemoryMessage
    suspend fun loadTail(sessionId: String, limit: Int): List<MemoryMessage>
    suspend fun archiveOldMessages(sessionId: String, keepActive: Int)
    suspend fun loadMessagesForSummary(sessionId: String, afterMessageId: String?): List<MemoryMessage>
    suspend fun countActiveMessages(sessionId: String): Int
    suspend fun getMessageById(messageId: String): MemoryMessage?
}

interface MemoryRepository {
    suspend fun findActiveByKey(subject: MemorySubject, type: MemoryType, key: String): List<MemoryFact>
    suspend fun findRelevant(query: MemoryQuery): List<MemoryFact>
    suspend fun applyMutation(mutation: MemoryMutation)
    suspend fun getSources(memoryFactId: String): List<MemoryFactSource>
    suspend fun forget(request: ForgetMemoryRequest)
    suspend fun hasTombstone(subject: MemorySubject, type: MemoryType, key: String, valueHash: String?): Boolean
}

interface SummaryRepository {
    suspend fun load(sessionId: String): ConversationSummary?
    suspend fun save(summary: ConversationSummary)
}
