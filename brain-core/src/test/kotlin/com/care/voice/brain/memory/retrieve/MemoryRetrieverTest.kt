package com.care.voice.brain.memory.retrieve

import com.care.voice.brain.memory.fact.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MemoryRetrieverTest {

    private val retriever = MemoryRetriever()
    private val now = Instant.parse("2026-07-25T10:00:00Z")

    @Test
    fun foodQueryIncludesPreferencesAndAllergies() {
        val facts = listOf(
            fact(MemoryType.PREFERENCE, "кофе", "drink"),
            fact(MemoryType.ALLERGY, "пенициллин", "allergy"),
            fact(MemoryType.MEDICATION, "аспирин", "med")
        )
        val query = MemoryQuery("что мне можно есть", MemoryTopicHint.FOOD, now = now)
        val ranked = retriever.rank(facts, query)
        assertTrue(ranked.any { it.type == MemoryType.PREFERENCE })
        assertTrue(ranked.any { it.type == MemoryType.ALLERGY })
        assertFalse(ranked.any { it.type == MemoryType.MEDICATION })
    }

    @Test
    fun filmQueryExcludesMedications() {
        val facts = listOf(
            fact(MemoryType.PREFERENCE, "детективы", "genre"),
            fact(MemoryType.MEDICATION, "аспирин", "med")
        )
        val query = MemoryQuery("посоветуй фильм", MemoryTopicHint.PREFERENCE, now = now)
        val ranked = retriever.rank(facts, query)
        assertFalse(ranked.any { it.type == MemoryType.MEDICATION })
    }

    @Test
    fun expiredLocationNotIncluded() {
        val facts = listOf(
            fact(
                MemoryType.LOCATION,
                "Варшава",
                "city",
                validUntil = Instant.parse("2026-07-24T00:00:00Z")
            )
        )
        val query = MemoryQuery("где я", MemoryTopicHint.LOCATION, now = now)
        assertTrue(retriever.rank(facts, query).isEmpty())
    }

    @Test
    fun limitsProfileAndEpisodicFacts() {
        val facts = (1..10).map { fact(MemoryType.PREFERENCE, "pref$it", "k$it") } +
            (1..5).map { fact(MemoryType.EPISODIC, "ep$it", "e$it") }
        val query = MemoryQuery("расскажи", MemoryTopicHint.GENERAL, now = now, maxProfileFacts = 5, maxEpisodicFacts = 2)
        val ranked = retriever.rank(facts, query)
        assertEquals(7, ranked.size)
        assertEquals(5, ranked.count { it.type != MemoryType.EPISODIC })
        assertEquals(2, ranked.count { it.type == MemoryType.EPISODIC })
    }

    @Test
    fun classifyTopicDetectsMedicationAndForget() {
        assertEquals(MemoryTopicHint.MEDICATION, retriever.classifyTopic("какие у меня лекарства"))
        assertEquals(MemoryTopicHint.FORGET, retriever.classifyTopic("забудь про кофе"))
    }

    private fun fact(
        type: MemoryType,
        value: String,
        key: String,
        validUntil: Instant? = null
    ) = MemoryFact(
        id = "$key-$value",
        subject = MemorySubject.User,
        type = type,
        key = key,
        value = value,
        confidence = 0.8,
        importance = 5,
        validFrom = null,
        validUntil = validUntil,
        status = MemoryStatus.ACTIVE,
        confirmationStatus = MemoryConfirmationStatus.CONFIRMED,
        sensitivity = MemorySensitivity.NORMAL,
        createdAt = now,
        updatedAt = now,
        lastConfirmedAt = now,
        lastUsedAt = null
    )
}
