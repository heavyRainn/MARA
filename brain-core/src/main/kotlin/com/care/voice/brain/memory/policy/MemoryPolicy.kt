package com.care.voice.brain.memory.policy

import com.care.voice.brain.memory.fact.MemoryCandidate
import com.care.voice.brain.memory.fact.MemoryOperation
import com.care.voice.brain.memory.fact.MemorySensitivity
import com.care.voice.brain.memory.fact.MemorySubject
import com.care.voice.brain.memory.fact.MemoryType

/**
 * Rule-based gate before persisting memory candidates.
 */
class MemoryPolicy(
    private val minAutoSaveConfidence: Double = 0.75
) {

    fun evaluate(candidate: MemoryCandidate): PolicyDecision {
        if (candidate.operation == MemoryOperation.NOOP) return PolicyDecision.REJECT

        if (candidate.subject is MemorySubject.Unknown) return PolicyDecision.REJECT

        if (candidate.confidence < 0.0 || candidate.confidence > 1.0) return PolicyDecision.REJECT

        if (candidate.key.isBlank()) return PolicyDecision.REJECT

        if (candidate.operation != MemoryOperation.DELETE && candidate.value.isNullOrBlank()) {
            return PolicyDecision.REJECT
        }

        if (isMedical(candidate.type) && isQuestion(candidate.reason)) {
            return PolicyDecision.REJECT
        }

        if (isMedical(candidate.type) && candidate.subject is MemorySubject.RelatedPerson) {
            return PolicyDecision.REJECT
        }

        if (candidate.type == MemoryType.ASSISTANT_NOTE && candidate.sensitivity >= MemorySensitivity.HEALTH) {
            return PolicyDecision.REJECT
        }

        if (requiresMandatoryConfirmation(candidate.type)) {
            return PolicyDecision.REQUIRE_CONFIRMATION
        }

        if (candidate.requiresConfirmation) {
            return PolicyDecision.REQUIRE_CONFIRMATION
        }

        if (candidate.confidence < minAutoSaveConfidence) {
            return if (isSafeAutoType(candidate.type)) PolicyDecision.REQUIRE_CONFIRMATION
            else PolicyDecision.REJECT
        }

        return PolicyDecision.AUTO_APPLY
    }

    private fun isMedical(type: MemoryType): Boolean = type in MEDICAL_TYPES

    private fun isSafeAutoType(type: MemoryType): Boolean = type in AUTO_SAFE_TYPES

    private fun requiresMandatoryConfirmation(type: MemoryType): Boolean = type in MANDATORY_CONFIRM_TYPES

    private fun isQuestion(reason: String): Boolean {
        val r = reason.lowercase()
        return r.contains("вопрос") || r.contains("question") || r.endsWith("?")
    }

    companion object {
        val MEDICAL_TYPES = setOf(
            MemoryType.HEALTH_CONDITION,
            MemoryType.MEDICATION,
            MemoryType.MEDICATION_DOSAGE,
            MemoryType.ALLERGY,
            MemoryType.CARE_INSTRUCTION
        )

        val MANDATORY_CONFIRM_TYPES = MEDICAL_TYPES

        val AUTO_SAFE_TYPES = setOf(
            MemoryType.IDENTITY,
            MemoryType.PREFERENCE,
            MemoryType.COMMUNICATION_PREFERENCE
        )
    }
}

enum class PolicyDecision {
    AUTO_APPLY,
    REQUIRE_CONFIRMATION,
    REJECT
}
