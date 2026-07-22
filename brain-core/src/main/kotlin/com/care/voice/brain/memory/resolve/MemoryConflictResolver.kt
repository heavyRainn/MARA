package com.care.voice.brain.memory.resolve

import com.care.voice.brain.memory.fact.MemoryCandidate
import com.care.voice.brain.memory.fact.MemoryConfirmationStatus
import com.care.voice.brain.memory.fact.MemoryFact
import com.care.voice.brain.memory.fact.MemoryFactSource
import com.care.voice.brain.memory.fact.MemoryMutation
import com.care.voice.brain.memory.fact.MemoryOperation
import com.care.voice.brain.memory.fact.MemorySensitivity
import com.care.voice.brain.memory.fact.MemorySourceType
import com.care.voice.brain.memory.fact.MemoryStatus
import com.care.voice.brain.memory.fact.MemorySubject
import java.time.Instant
import java.util.UUID

class MemoryConflictResolver(
    private val idProvider: () -> String = { UUID.randomUUID().toString() }
) {

    fun resolve(
        candidate: MemoryCandidate,
        existingActive: List<MemoryFact>,
        messageId: String,
        now: Instant,
        confirmed: Boolean
    ): ResolveResult {
        val match = existingActive.firstOrNull { sameKey(it, candidate) }

        return when (candidate.operation) {
            MemoryOperation.NOOP -> ResolveResult.NoChange
            MemoryOperation.ADD -> resolveAdd(candidate, match, messageId, now, confirmed)
            MemoryOperation.UPDATE -> resolveUpdate(candidate, match, messageId, now, confirmed)
            MemoryOperation.DELETE -> resolveDelete(candidate, match, messageId, now, confirmed)
        }
    }

    private fun resolveAdd(
        candidate: MemoryCandidate,
        match: MemoryFact?,
        messageId: String,
        now: Instant,
        confirmed: Boolean
    ): ResolveResult {
        if (match != null) {
            if (match.value == candidate.value) return ResolveResult.NoChange
            return resolveUpdate(candidate, match, messageId, now, confirmed)
        }
        val fact = buildFact(candidate, now, confirmed)
        val source = buildSource(fact.id, messageId, sourceType(candidate, confirmed), candidate.value.orEmpty(), now)
        return ResolveResult.Apply(
            MemoryMutation(MemoryOperation.ADD, fact, supersededFactId = null, source = source)
        )
    }

    private fun resolveUpdate(
        candidate: MemoryCandidate,
        match: MemoryFact?,
        messageId: String,
        now: Instant,
        confirmed: Boolean
    ): ResolveResult {
        if (match == null) {
            val fact = buildFact(candidate, now, confirmed)
            val source = buildSource(fact.id, messageId, sourceType(candidate, confirmed), candidate.value.orEmpty(), now)
            return ResolveResult.Apply(
                MemoryMutation(MemoryOperation.ADD, fact, supersededFactId = null, source = source)
            )
        }
        if (match.value == candidate.value) return ResolveResult.NoChange
        val newFact = buildFact(candidate, now, confirmed)
        val source = buildSource(newFact.id, messageId, sourceType(candidate, confirmed), candidate.value.orEmpty(), now)
        return ResolveResult.Apply(
            MemoryMutation(MemoryOperation.UPDATE, newFact, supersededFactId = match.id, source = source)
        )
    }

    private fun resolveDelete(
        candidate: MemoryCandidate,
        match: MemoryFact?,
        messageId: String,
        now: Instant,
        confirmed: Boolean
    ): ResolveResult {
        if (match == null) return ResolveResult.NoChange
        val deleted = match.copy(
            status = MemoryStatus.DELETED,
            updatedAt = now,
            confirmationStatus = if (confirmed) MemoryConfirmationStatus.CONFIRMED else match.confirmationStatus
        )
        val source = buildSource(match.id, messageId, MemorySourceType.USER_CONFIRMATION, candidate.value.orEmpty(), now)
        return ResolveResult.Apply(
            MemoryMutation(MemoryOperation.DELETE, deleted, supersededFactId = match.id, source = source)
        )
    }

    private fun buildFact(candidate: MemoryCandidate, now: Instant, confirmed: Boolean): MemoryFact {
        val confirmation = when {
            confirmed -> MemoryConfirmationStatus.CONFIRMED
            candidate.requiresConfirmation -> MemoryConfirmationStatus.REQUIRES_CONFIRMATION
            else -> MemoryConfirmationStatus.INFERRED
        }
        return MemoryFact(
            id = idProvider(),
            subject = candidate.subject,
            type = candidate.type,
            key = candidate.key,
            value = candidate.value.orEmpty(),
            confidence = candidate.confidence.coerceIn(0.0, 1.0),
            importance = importanceFor(candidate.type),
            validFrom = candidate.validFrom,
            validUntil = candidate.validUntil,
            status = MemoryStatus.ACTIVE,
            confirmationStatus = confirmation,
            sensitivity = candidate.sensitivity,
            createdAt = now,
            updatedAt = now,
            lastConfirmedAt = if (confirmed) now else null,
            lastUsedAt = null
        )
    }

    private fun buildSource(
        factId: String,
        messageId: String,
        sourceType: MemorySourceType,
        excerpt: String,
        now: Instant
    ) = MemoryFactSource(
        id = idProvider(),
        memoryFactId = factId,
        messageId = messageId,
        sourceType = sourceType,
        excerpt = excerpt.take(200),
        createdAt = now
    )

    private fun sourceType(candidate: MemoryCandidate, confirmed: Boolean): MemorySourceType =
        when {
            confirmed -> MemorySourceType.USER_CONFIRMATION
            else -> MemorySourceType.EXPLICIT_USER_STATEMENT
        }

    private fun sameKey(fact: MemoryFact, candidate: MemoryCandidate): Boolean =
        subjectKey(fact.subject) == subjectKey(candidate.subject) &&
            fact.type == candidate.type &&
            fact.key.equals(candidate.key, ignoreCase = true)

    private fun subjectKey(subject: MemorySubject): String = when (subject) {
        MemorySubject.User -> "user"
        is MemorySubject.RelatedPerson -> "rel:${subject.relation}:${subject.name.orEmpty()}"
        MemorySubject.Unknown -> "unknown"
    }

    private fun importanceFor(type: com.care.voice.brain.memory.fact.MemoryType): Int = when (type) {
        com.care.voice.brain.memory.fact.MemoryType.ALLERGY,
        com.care.voice.brain.memory.fact.MemoryType.MEDICATION_DOSAGE -> 10
        com.care.voice.brain.memory.fact.MemoryType.MEDICATION,
        com.care.voice.brain.memory.fact.MemoryType.HEALTH_CONDITION -> 9
        com.care.voice.brain.memory.fact.MemoryType.IDENTITY -> 8
        com.care.voice.brain.memory.fact.MemoryType.COMMUNICATION_PREFERENCE -> 7
        else -> 5
    }

    sealed interface ResolveResult {
        data object NoChange : ResolveResult
        data class Apply(val mutation: MemoryMutation) : ResolveResult
    }
}
