package com.care.voice.brain.memory.retrieve

import com.care.voice.brain.memory.fact.MemoryConfirmationStatus
import com.care.voice.brain.memory.fact.MemoryFact
import com.care.voice.brain.memory.fact.MemoryQuery
import com.care.voice.brain.memory.fact.MemoryStatus
import com.care.voice.brain.memory.fact.MemoryTopicHint
import com.care.voice.brain.memory.fact.MemoryType
import java.time.Instant

class MemoryRetriever {

    fun rank(facts: List<MemoryFact>, query: MemoryQuery): List<MemoryFact> {
        val now = query.now
        val filtered = facts.filter { fact ->
            fact.status == MemoryStatus.ACTIVE &&
                !isExpired(fact, now) &&
                isAllowedForTopic(fact, query.topicHint)
        }

        return filtered
            .sortedByDescending { score(it, query) }
            .let { sorted ->
                val profile = sorted.filter { it.type != MemoryType.EPISODIC }.take(query.maxProfileFacts)
                val episodic = sorted.filter { it.type == MemoryType.EPISODIC }.take(query.maxEpisodicFacts)
                (profile + episodic).distinctBy { it.id }
            }
    }

    fun classifyTopic(userText: String): MemoryTopicHint {
        val t = userText.lowercase()
        return when {
            t.contains("забудь") || t.contains("не запоминай") -> MemoryTopicHint.FORGET
            t.contains("лекар") || t.contains("таблет") || t.contains("aspirin") || t.contains("аспирин") ->
                MemoryTopicHint.MEDICATION
            t.contains("аллерг") -> MemoryTopicHint.HEALTH
            t.contains("еда") || t.contains("кофе") || t.contains("обед") -> MemoryTopicHint.FOOD
            t.contains("фильм") || t.contains("кино") -> MemoryTopicHint.PREFERENCE
            t.contains("зовут") || t.contains("имя") -> MemoryTopicHint.IDENTITY
            t.contains("где") || t.contains("город") || t.contains("дом") -> MemoryTopicHint.LOCATION
            else -> MemoryTopicHint.GENERAL
        }
    }

    private fun isExpired(fact: MemoryFact, now: Instant): Boolean =
        fact.validUntil?.let { now.isAfter(it) } == true

    private fun isAllowedForTopic(fact: MemoryFact, hint: MemoryTopicHint): Boolean = when (hint) {
        MemoryTopicHint.GENERAL -> true
        MemoryTopicHint.FOOD -> fact.type in setOf(
            MemoryType.PREFERENCE, MemoryType.ALLERGY, MemoryType.HEALTH_CONDITION
        )
        MemoryTopicHint.HEALTH, MemoryTopicHint.MEDICATION -> fact.type in MemoryPolicyMedicalTypes
        MemoryTopicHint.LOCATION -> fact.type == MemoryType.LOCATION || fact.type == MemoryType.EPISODIC
        MemoryTopicHint.PREFERENCE -> fact.type in setOf(
            MemoryType.PREFERENCE, MemoryType.COMMUNICATION_PREFERENCE, MemoryType.ROUTINE
        )
        MemoryTopicHint.IDENTITY -> fact.type == MemoryType.IDENTITY
        MemoryTopicHint.FORGET -> true
    }

    private fun score(fact: MemoryFact, query: MemoryQuery): Double {
        var s = fact.confidence * 2.0 + fact.importance * 0.3
        if (fact.confirmationStatus == MemoryConfirmationStatus.CONFIRMED) s += 1.5
        if (containsKeyword(fact, query.userText)) s += 2.0
        s += freshnessBoost(fact, query.now)
        return s
    }

    private fun containsKeyword(fact: MemoryFact, text: String): Boolean {
        val t = text.lowercase()
        return fact.key.lowercase() in t || fact.value.lowercase().split(' ').any { it.length > 3 && it in t }
    }

    private fun freshnessBoost(fact: MemoryFact, now: Instant): Double {
        val anchor = fact.validUntil ?: fact.lastConfirmedAt ?: fact.updatedAt ?: fact.createdAt
        val hours = (now.toEpochMilli() - anchor.toEpochMilli()).coerceAtLeast(0) / 3_600_000.0
        return (72.0 - hours).coerceIn(0.0, 72.0) / 72.0
    }

    companion object {
        private val MemoryPolicyMedicalTypes = setOf(
            MemoryType.HEALTH_CONDITION,
            MemoryType.MEDICATION,
            MemoryType.MEDICATION_DOSAGE,
            MemoryType.ALLERGY,
            MemoryType.CARE_INSTRUCTION
        )
    }
}
