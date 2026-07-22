package com.care.voice.brain.memory.consolidate

import com.care.voice.brain.memory.MemoryRepository
import com.care.voice.brain.memory.MemoryStore
import com.care.voice.brain.memory.fact.ConsolidationResult
import com.care.voice.brain.memory.fact.MemoryConsolidator
import com.care.voice.brain.memory.fact.MemoryConfirmationStatus
import com.care.voice.brain.memory.fact.MemoryFact
import com.care.voice.brain.memory.fact.MemoryMutation
import com.care.voice.brain.memory.fact.MemoryOperation
import com.care.voice.brain.memory.fact.MemoryQuery
import com.care.voice.brain.memory.fact.MemoryStatus
import com.care.voice.brain.memory.fact.MemoryType
import com.care.voice.brain.profile.UserProfileProjector
import java.time.Instant

class DefaultMemoryConsolidator(
    private val memoryRepository: MemoryRepository,
    private val memoryStore: MemoryStore
) : MemoryConsolidator {

    override suspend fun consolidate(now: Instant): ConsolidationResult {
        val all = memoryRepository.findRelevant(MemoryQuery("", now = now))
        var expired = 0
        var duplicates = 0
        var conflicts = 0

        val active = all.filter { it.status == MemoryStatus.ACTIVE }
        val byKey = active.groupBy { "${subjectKey(it)}|${it.type}|${it.key.lowercase()}" }

        for ((_, group) in byKey) {
            if (group.size <= 1) continue
            val sorted = group.sortedByDescending { it.updatedAt }
            val keeper = sorted.first()
            for (duplicate in sorted.drop(1)) {
                if (duplicate.type in MEDICAL) {
                    conflicts++
                    continue
                }
                if (duplicate.value == keeper.value) {
                    memoryRepository.applyMutation(
                        MemoryMutation(
                            operation = MemoryOperation.DELETE,
                            fact = duplicate.copy(status = MemoryStatus.SUPERSEDED, updatedAt = now),
                            supersededFactId = duplicate.id,
                            source = null
                        )
                    )
                    duplicates++
                }
            }
        }

        for (fact in active) {
            val until = fact.validUntil ?: continue
            if (now.isAfter(until) && fact.status == MemoryStatus.ACTIVE) {
                memoryRepository.applyMutation(
                    MemoryMutation(
                        operation = MemoryOperation.UPDATE,
                        fact = fact.copy(status = MemoryStatus.EXPIRED, updatedAt = now),
                        supersededFactId = fact.id,
                        source = null
                    )
                )
                expired++
            }
        }

        val profile = UserProfileProjector.project(
            memoryRepository.findRelevant(MemoryQuery("", now = now))
                .filter { it.status == MemoryStatus.ACTIVE }
        )
        memoryStore.saveUserProfile(profile)

        return ConsolidationResult(
            expiredCount = expired,
            duplicatesMerged = duplicates,
            conflictsDetected = conflicts,
            profileRebuilt = true
        )
    }

    private fun subjectKey(fact: MemoryFact): String = when (val s = fact.subject) {
        is com.care.voice.brain.memory.fact.MemorySubject.User -> "user"
        is com.care.voice.brain.memory.fact.MemorySubject.RelatedPerson -> "rel:${s.relation}"
        com.care.voice.brain.memory.fact.MemorySubject.Unknown -> "unknown"
    }

    companion object {
        private val MEDICAL = setOf(
            MemoryType.HEALTH_CONDITION,
            MemoryType.MEDICATION,
            MemoryType.MEDICATION_DOSAGE,
            MemoryType.ALLERGY,
            MemoryType.CARE_INSTRUCTION
        )
    }
}
