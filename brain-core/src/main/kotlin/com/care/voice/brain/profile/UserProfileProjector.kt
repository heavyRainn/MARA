package com.care.voice.brain.profile

import com.care.voice.brain.memory.UserProfile
import com.care.voice.brain.memory.fact.MemoryConfirmationStatus
import com.care.voice.brain.memory.fact.MemoryFact
import com.care.voice.brain.memory.fact.MemoryStatus
import com.care.voice.brain.memory.fact.MemorySubject
import com.care.voice.brain.memory.fact.MemoryType

/**
 * Builds [UserProfile] projection from active confirmed facts (cache over atomic memory).
 */
object UserProfileProjector {

    fun project(facts: List<MemoryFact>): UserProfile {
        val active = facts.filter {
            it.status == MemoryStatus.ACTIVE &&
                it.subject is MemorySubject.User &&
                it.confirmationStatus != MemoryConfirmationStatus.REQUIRES_CONFIRMATION
        }

        var name: String? = null
        var age: Int? = null
        val conditions = mutableListOf<String>()
        val medications = mutableListOf<String>()
        val notes = mutableListOf<String>()

        for (fact in active) {
            when (fact.type) {
                MemoryType.IDENTITY -> when {
                    fact.key.contains("name", ignoreCase = true) ||
                        fact.key.contains("имя", ignoreCase = true) -> name = fact.value
                    fact.key.contains("age", ignoreCase = true) ||
                        fact.key.contains("возраст", ignoreCase = true) -> age = fact.value.toIntOrNull() ?: age
                }
                MemoryType.HEALTH_CONDITION -> conditions.add(fact.value)
                MemoryType.MEDICATION, MemoryType.MEDICATION_DOSAGE -> medications.add(fact.value)
                MemoryType.PREFERENCE, MemoryType.COMMUNICATION_PREFERENCE, MemoryType.ROUTINE ->
                    notes.add(fact.value)
                MemoryType.ALLERGY -> notes.add("аллергия: ${fact.value}")
                else -> Unit
            }
        }

        return UserProfile(
            name = name,
            age = age,
            conditions = conditions.joinToString("; ").ifBlank { null },
            medications = medications.joinToString("; ").ifBlank { null },
            notes = notes.joinToString("; ").ifBlank { null }
        )
    }
}
