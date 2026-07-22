package com.care.voice.brain.memory.fact

import java.time.Instant

data class MemoryFact(
    val id: String,
    val subject: MemorySubject,
    val type: MemoryType,
    val key: String,
    val value: String,
    val confidence: Double,
    val importance: Int,
    val validFrom: Instant?,
    val validUntil: Instant?,
    val status: MemoryStatus,
    val confirmationStatus: MemoryConfirmationStatus,
    val sensitivity: MemorySensitivity,
    val createdAt: Instant,
    val updatedAt: Instant,
    val lastConfirmedAt: Instant?,
    val lastUsedAt: Instant?
)

enum class MemoryType {
    IDENTITY,
    PREFERENCE,
    COMMUNICATION_PREFERENCE,
    RELATIONSHIP,
    ROUTINE,
    LOCATION,
    EPISODIC,
    HEALTH_CONDITION,
    MEDICATION,
    MEDICATION_DOSAGE,
    ALLERGY,
    CARE_INSTRUCTION,
    ASSISTANT_NOTE
}

enum class MemoryStatus {
    ACTIVE,
    SUPERSEDED,
    EXPIRED,
    DELETED,
    REJECTED
}

enum class MemoryConfirmationStatus {
    CONFIRMED,
    INFERRED,
    REQUIRES_CONFIRMATION
}

enum class MemorySensitivity {
    NORMAL,
    PERSONAL,
    HEALTH,
    CRITICAL_HEALTH
}

sealed interface MemorySubject {
    data object User : MemorySubject

    data class RelatedPerson(
        val relation: String,
        val name: String?
    ) : MemorySubject

    data object Unknown : MemorySubject
}

data class MemoryCandidate(
    val operation: MemoryOperation,
    val subject: MemorySubject,
    val type: MemoryType,
    val key: String,
    val value: String?,
    val confidence: Double,
    val sensitivity: MemorySensitivity,
    val requiresConfirmation: Boolean,
    val validFrom: Instant?,
    val validUntil: Instant?,
    val reason: String
)

enum class MemoryOperation {
    ADD,
    UPDATE,
    DELETE,
    NOOP
}

data class MemoryFactSource(
    val id: String,
    val memoryFactId: String,
    val messageId: String,
    val sourceType: MemorySourceType,
    val excerpt: String,
    val createdAt: Instant
)

enum class MemorySourceType {
    EXPLICIT_USER_STATEMENT,
    USER_CONFIRMATION,
    REPEATED_USER_STATEMENT,
    ASSISTANT_INFERENCE,
    REMINDER,
    IMPORTED_DATA
}

data class MemoryTombstone(
    val id: String,
    val subject: MemorySubject,
    val type: MemoryType,
    val key: String,
    val valueHash: String?,
    val createdAt: Instant,
    val reason: String
)

data class MemoryMutation(
    val operation: MemoryOperation,
    val fact: MemoryFact?,
    val supersededFactId: String?,
    val source: MemoryFactSource?
)

data class MemoryQuery(
    val userText: String,
    val topicHint: MemoryTopicHint = MemoryTopicHint.GENERAL,
    val maxProfileFacts: Int = 8,
    val maxEpisodicFacts: Int = 3,
    val now: Instant
)

enum class MemoryTopicHint {
    GENERAL,
    FOOD,
    HEALTH,
    MEDICATION,
    LOCATION,
    PREFERENCE,
    IDENTITY,
    FORGET
}

data class ForgetMemoryRequest(
    val subject: MemorySubject,
    val type: MemoryType?,
    val key: String?,
    val valueHint: String?,
    val reason: String,
    val now: Instant
)

data class ConsolidationResult(
    val expiredCount: Int,
    val duplicatesMerged: Int,
    val conflictsDetected: Int,
    val profileRebuilt: Boolean
)

interface MemoryConsolidator {
    suspend fun consolidate(now: Instant): ConsolidationResult
}
