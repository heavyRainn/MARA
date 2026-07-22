package com.care.voice.brain.memory.resolve

import com.care.voice.brain.memory.fact.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MemoryConflictResolverTest {

    private val now = Instant.parse("2026-07-22T12:00:00Z")
    private val resolver = MemoryConflictResolver { "new-fact-id" }

    @Test
    fun duplicateFactReturnsNoChange() {
        val existing = fact("Анна", key = "name")
        val candidate = candidate(MemoryOperation.ADD, "Анна", key = "name")
        val result = resolver.resolve(candidate, listOf(existing), "msg-1", now, confirmed = false)
        assertTrue(result is MemoryConflictResolver.ResolveResult.NoChange)
    }

    @Test
    fun newNameSupersedesOld() {
        val existing = fact("Нина", key = "name")
        val candidate = candidate(MemoryOperation.UPDATE, "Ирина", key = "name")
        val result = resolver.resolve(candidate, listOf(existing), "msg-2", now, confirmed = false)
        assertTrue(result is MemoryConflictResolver.ResolveResult.Apply)
        val mutation = (result as MemoryConflictResolver.ResolveResult.Apply).mutation
        assertEquals(MemoryOperation.UPDATE, mutation.operation)
        assertEquals("Ирина", mutation.fact?.value)
        assertEquals(existing.id, mutation.supersededFactId)
    }

    @Test
    fun deleteMarksFactDeleted() {
        val existing = fact("аспирин", type = MemoryType.MEDICATION, key = "medication")
        val candidate = candidate(MemoryOperation.DELETE, "аспирин", type = MemoryType.MEDICATION, key = "medication")
        val result = resolver.resolve(candidate, listOf(existing), "msg-3", now, confirmed = true)
        assertTrue(result is MemoryConflictResolver.ResolveResult.Apply)
        val mutation = (result as MemoryConflictResolver.ResolveResult.Apply).mutation
        assertEquals(MemoryStatus.DELETED, mutation.fact?.status)
    }

    @Test
    fun confirmedMedicationIsConfirmedStatus() {
        val candidate = candidate(MemoryOperation.ADD, "аспирин", type = MemoryType.MEDICATION, key = "medication")
        val result = resolver.resolve(candidate, emptyList(), "msg-4", now, confirmed = true)
        val mutation = (result as MemoryConflictResolver.ResolveResult.Apply).mutation
        assertEquals(MemoryConfirmationStatus.CONFIRMED, mutation.fact?.confirmationStatus)
    }

    private fun fact(
        value: String,
        key: String = "name",
        type: MemoryType = MemoryType.IDENTITY
    ) = MemoryFact(
        id = "existing-id",
        subject = MemorySubject.User,
        type = type,
        key = key,
        value = value,
        confidence = 0.9,
        importance = 5,
        validFrom = null,
        validUntil = null,
        status = MemoryStatus.ACTIVE,
        confirmationStatus = MemoryConfirmationStatus.INFERRED,
        sensitivity = MemorySensitivity.NORMAL,
        createdAt = now,
        updatedAt = now,
        lastConfirmedAt = null,
        lastUsedAt = null
    )

    private fun candidate(
        operation: MemoryOperation,
        value: String,
        key: String = "name",
        type: MemoryType = MemoryType.IDENTITY
    ) = MemoryCandidate(
        operation = operation,
        subject = MemorySubject.User,
        type = type,
        key = key,
        value = value,
        confidence = 0.95,
        sensitivity = MemorySensitivity.NORMAL,
        requiresConfirmation = false,
        validFrom = null,
        validUntil = null,
        reason = "explicit"
    )
}
