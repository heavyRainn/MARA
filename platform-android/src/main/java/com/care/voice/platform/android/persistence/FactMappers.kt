package com.care.voice.platform.android.persistence

import com.care.voice.brain.memory.fact.*
import com.care.voice.brain.pending.PendingAction
import com.care.voice.brain.pending.PendingActionState
import com.care.voice.brain.pending.PendingActionType
import com.care.voice.brain.session.ConversationSession
import com.care.voice.data.history.*
import java.time.Instant

internal fun MemoryFactEntity.toBrain(): MemoryFact = MemoryFact(
    id = id,
    subject = subjectType.toSubject(subjectRelation, subjectName),
    type = MemoryType.valueOf(type),
    key = factKey,
    value = value,
    confidence = confidence,
    importance = importance,
    validFrom = validFrom?.let { Instant.ofEpochMilli(it) },
    validUntil = validUntil?.let { Instant.ofEpochMilli(it) },
    status = MemoryStatus.valueOf(status),
    confirmationStatus = MemoryConfirmationStatus.valueOf(confirmationStatus),
    sensitivity = MemorySensitivity.valueOf(sensitivity),
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    lastConfirmedAt = lastConfirmedAt?.let { Instant.ofEpochMilli(it) },
    lastUsedAt = lastUsedAt?.let { Instant.ofEpochMilli(it) }
)

internal fun MemoryFact.toEntity(): MemoryFactEntity {
    val (subjectType, relation, name) = subject.toStorage()
    return MemoryFactEntity(
        id = id,
        subjectType = subjectType,
        subjectRelation = relation,
        subjectName = name,
        type = type.name,
        factKey = key,
        value = value,
        confidence = confidence,
        importance = importance,
        validFrom = validFrom?.toEpochMilli(),
        validUntil = validUntil?.toEpochMilli(),
        status = status.name,
        confirmationStatus = confirmationStatus.name,
        sensitivity = sensitivity.name,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli(),
        lastConfirmedAt = lastConfirmedAt?.toEpochMilli(),
        lastUsedAt = lastUsedAt?.toEpochMilli()
    )
}

internal fun MemoryFactSource.toEntity(): MemoryFactSourceEntity = MemoryFactSourceEntity(
    id = id,
    memoryFactId = memoryFactId,
    messageId = messageId,
    sourceType = sourceType.name,
    excerpt = excerpt,
    createdAt = createdAt.toEpochMilli()
)

internal fun MemoryFactSourceEntity.toBrain(): MemoryFactSource = MemoryFactSource(
    id = id,
    memoryFactId = memoryFactId,
    messageId = messageId,
    sourceType = MemorySourceType.valueOf(sourceType),
    excerpt = excerpt,
    createdAt = Instant.ofEpochMilli(createdAt)
)

internal fun MemoryTombstone.toEntity(): MemoryTombstoneEntity {
    val (subjectType, relation, name) = subject.toStorage()
    return MemoryTombstoneEntity(
        id = id,
        subjectType = subjectType,
        subjectRelation = relation,
        subjectName = name,
        type = type.name,
        tombstoneKey = key,
        valueHash = valueHash,
        createdAt = createdAt.toEpochMilli(),
        reason = reason
    )
}

internal fun PendingActionEntity.toBrain(): PendingAction = PendingAction(
    id = id,
    type = PendingActionType.valueOf(type),
    payload = payload,
    state = PendingActionState.valueOf(state),
    createdAt = Instant.ofEpochMilli(createdAt),
    expiresAt = Instant.ofEpochMilli(expiresAt)
)

internal fun PendingAction.toEntity(): PendingActionEntity = PendingActionEntity(
    id = id,
    type = type.name,
    payload = payload,
    state = state.name,
    createdAt = createdAt.toEpochMilli(),
    expiresAt = expiresAt.toEpochMilli()
)

internal fun ConversationSessionEntity.toBrain(): ConversationSession = ConversationSession(
    id = id,
    startedAt = Instant.ofEpochMilli(startedAt),
    lastActivityAt = Instant.ofEpochMilli(lastActivityAt),
    excludeFromExtraction = excludeFromExtraction
)

internal fun ConversationSession.toEntity(): ConversationSessionEntity = ConversationSessionEntity(
    id = id,
    startedAt = startedAt.toEpochMilli(),
    lastActivityAt = lastActivityAt.toEpochMilli(),
    excludeFromExtraction = excludeFromExtraction
)

private fun String.toSubject(relation: String?, name: String?): MemorySubject = when (this) {
    "USER" -> MemorySubject.User
    "RELATED_PERSON" -> MemorySubject.RelatedPerson(relation.orEmpty(), name)
    else -> MemorySubject.Unknown
}

internal fun MemorySubject.toStorage(): Triple<String, String?, String?> = when (this) {
    MemorySubject.User -> Triple("USER", null, null)
    is MemorySubject.RelatedPerson -> Triple("RELATED_PERSON", relation, name)
    MemorySubject.Unknown -> Triple("UNKNOWN", null, null)
}

internal fun String.toMessageState(): com.care.voice.brain.memory.MessageState =
    runCatching { com.care.voice.brain.memory.MessageState.valueOf(this) }
        .getOrDefault(com.care.voice.brain.memory.MessageState.ACTIVE)

internal fun com.care.voice.brain.memory.MessageState.toStorage(): String = name
