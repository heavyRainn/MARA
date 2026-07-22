package com.care.voice.brain.memory.consolidate

import com.care.voice.brain.memory.FakeMemoryRepository
import com.care.voice.brain.memory.FakeMemoryStore
import com.care.voice.brain.memory.fact.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DefaultMemoryConsolidatorTest {

    private val now = Instant.parse("2026-07-25T12:00:00Z")
    private val memoryStore = FakeMemoryStore()
    private val memoryRepository = FakeMemoryRepository()
    private val consolidator = DefaultMemoryConsolidator(memoryRepository, memoryStore)

    @Test
    fun expiresFactsPastValidUntil() {
        memoryRepository.facts.add(
            activeFact(
                id = "loc-1",
                type = MemoryType.LOCATION,
                value = "Варшава",
                key = "city",
                validUntil = Instant.parse("2026-07-24T00:00:00Z")
            )
        )
        val result = kotlinx.coroutines.runBlocking { consolidator.consolidate(now) }
        assertEquals(1, result.expiredCount)
        assertTrue(memoryRepository.facts.any { it.id == "loc-1" && it.status == MemoryStatus.EXPIRED })
    }

    @Test
    fun mergesDuplicateNonMedicalFacts() {
        memoryRepository.facts.addAll(
            listOf(
                activeFact("dup-1", MemoryType.PREFERENCE, "чай", "drink"),
                activeFact("dup-2", MemoryType.PREFERENCE, "чай", "drink")
            )
        )
        val result = kotlinx.coroutines.runBlocking { consolidator.consolidate(now) }
        assertTrue(result.duplicatesMerged >= 1)
    }

    @Test
    fun doesNotAutoMergeMedicalDuplicates() {
        memoryRepository.facts.addAll(
            listOf(
                activeFact("med-1", MemoryType.MEDICATION, "аспирин", "med"),
                activeFact("med-2", MemoryType.MEDICATION, "аспирин", "med")
            )
        )
        val result = kotlinx.coroutines.runBlocking { consolidator.consolidate(now) }
        assertEquals(1, result.conflictsDetected)
    }

    private fun activeFact(
        id: String,
        type: MemoryType,
        value: String,
        key: String,
        validUntil: Instant? = null
    ) = MemoryFact(
        id = id,
        subject = MemorySubject.User,
        type = type,
        key = key,
        value = value,
        confidence = 0.9,
        importance = 5,
        validFrom = null,
        validUntil = validUntil,
        status = MemoryStatus.ACTIVE,
        confirmationStatus = MemoryConfirmationStatus.INFERRED,
        sensitivity = MemorySensitivity.NORMAL,
        createdAt = now,
        updatedAt = now,
        lastConfirmedAt = null,
        lastUsedAt = null
    )
}
