package com.care.voice.brain.memory.policy

import com.care.voice.brain.memory.fact.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class MemoryPolicyTest {

    private val policy = MemoryPolicy()

    @Test
    fun identityHighConfidenceAutoApplies() {
        val candidate = baseCandidate(type = MemoryType.IDENTITY, confidence = 0.9)
        assertEquals(PolicyDecision.AUTO_APPLY, policy.evaluate(candidate))
    }

    @Test
    fun medicationRequiresConfirmation() {
        val candidate = baseCandidate(type = MemoryType.MEDICATION, confidence = 1.0)
        assertEquals(PolicyDecision.REQUIRE_CONFIRMATION, policy.evaluate(candidate))
    }

    @Test
    fun dosageAlwaysRequiresConfirmation() {
        val candidate = baseCandidate(type = MemoryType.MEDICATION_DOSAGE, confidence = 1.0)
        assertEquals(PolicyDecision.REQUIRE_CONFIRMATION, policy.evaluate(candidate))
    }

    @Test
    fun unknownSubjectRejected() {
        val candidate = baseCandidate(subject = MemorySubject.Unknown)
        assertEquals(PolicyDecision.REJECT, policy.evaluate(candidate))
    }

    @Test
    fun medicalRelatedPersonRejected() {
        val candidate = baseCandidate(
            type = MemoryType.MEDICATION,
            subject = MemorySubject.RelatedPerson("daughter", "Аня")
        )
        assertEquals(PolicyDecision.REJECT, policy.evaluate(candidate))
    }

    private fun baseCandidate(
        type: MemoryType = MemoryType.PREFERENCE,
        subject: MemorySubject = MemorySubject.User,
        confidence: Double = 0.8
    ) = MemoryCandidate(
        operation = MemoryOperation.ADD,
        subject = subject,
        type = type,
        key = "test",
        value = "value",
        confidence = confidence,
        sensitivity = MemorySensitivity.NORMAL,
        requiresConfirmation = false,
        validFrom = null,
        validUntil = null,
        reason = "explicit"
    )
}
