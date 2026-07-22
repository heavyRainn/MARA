package com.care.voice.brain.memory.extract

import com.care.voice.brain.memory.fact.MemoryCandidate
import com.care.voice.brain.memory.fact.MemoryOperation
import com.care.voice.brain.memory.fact.MemorySensitivity
import com.care.voice.brain.memory.fact.MemorySubject
import com.care.voice.brain.memory.fact.MemoryType
import com.care.voice.brain.util.JsonExtractor
import java.time.Instant

/**
 * Safe parser for LLM memory extraction JSON array.
 * Invalid candidates are dropped without failing the whole batch.
 */
object MemoryCandidateParser {

    fun parse(raw: String, now: Instant): List<MemoryCandidate> {
        val json = extractArrayJson(raw) ?: return emptyList()
        val objects = splitTopLevelObjects(json)
        return objects.mapNotNull { parseOne(it, now) }
    }

    private fun extractArrayJson(raw: String): String? {
        val cleaned = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        if (cleaned.startsWith("[") && cleaned.endsWith("]")) return cleaned
        val start = cleaned.indexOf('[')
        val end = cleaned.lastIndexOf(']')
        return if (start >= 0 && end > start) cleaned.substring(start, end + 1) else null
    }

    private fun splitTopLevelObjects(arrayJson: String): List<String> {
        val inner = arrayJson.trim().removePrefix("[").removeSuffix("]").trim()
        if (inner.isBlank()) return emptyList()
        val result = mutableListOf<String>()
        var depth = 0
        var start = 0
        for (i in inner.indices) {
            when (inner[i]) {
                '{' -> {
                    if (depth == 0) start = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0) result.add(inner.substring(start, i + 1))
                }
            }
        }
        return result
    }

    private fun parseOne(json: String, now: Instant): MemoryCandidate? {
        val operation = parseEnum(
            JsonExtractor.stringField(json, "operation"),
            MemoryOperation.entries.map { it.name }
        )?.let { MemoryOperation.valueOf(it) } ?: return null

        if (operation == MemoryOperation.NOOP) {
            return MemoryCandidate(
                operation = MemoryOperation.NOOP,
                subject = MemorySubject.User,
                type = MemoryType.ASSISTANT_NOTE,
                key = "noop",
                value = null,
                confidence = 0.0,
                sensitivity = MemorySensitivity.NORMAL,
                requiresConfirmation = false,
                validFrom = null,
                validUntil = null,
                reason = JsonExtractor.stringField(json, "reason").orEmpty()
            )
        }

        val typeName = JsonExtractor.stringField(json, "type") ?: return null
        val type = parseEnum(typeName, MemoryType.entries.map { it.name })
            ?.let { runCatching { MemoryType.valueOf(it) }.getOrNull() } ?: return null

        val key = JsonExtractor.stringField(json, "key")?.trim().orEmpty()
        if (key.isBlank()) return null

        val value = JsonExtractor.stringField(json, "value")
        if (operation != MemoryOperation.DELETE && value.isNullOrBlank()) return null

        val confidence = JsonExtractor.doubleField(json, "confidence")?.coerceIn(0.0, 1.0) ?: return null

        val sensitivityName = JsonExtractor.stringField(json, "sensitivity") ?: "NORMAL"
        val sensitivity = parseEnum(sensitivityName, MemorySensitivity.entries.map { it.name })
            ?.let { runCatching { MemorySensitivity.valueOf(it) }.getOrNull() }
            ?: MemorySensitivity.NORMAL

        val subject = parseSubject(json)
        val requiresConfirmation = JsonExtractor.booleanField(json, "requiresConfirmation") ?: false
        val validUntilEpoch = JsonExtractor.longField(json, "validUntilEpochMillis")
        val validFromEpoch = JsonExtractor.longField(json, "validFromEpochMillis")

        return MemoryCandidate(
            operation = operation,
            subject = subject,
            type = type,
            key = key,
            value = value,
            confidence = confidence,
            sensitivity = sensitivity,
            requiresConfirmation = requiresConfirmation,
            validFrom = validFromEpoch?.let { Instant.ofEpochMilli(it) },
            validUntil = validUntilEpoch?.let { Instant.ofEpochMilli(it) },
            reason = JsonExtractor.stringField(json, "reason").orEmpty()
        )
    }

    private fun parseSubject(json: String): MemorySubject {
        val subjectType = JsonExtractor.stringField(json, "subjectType")?.uppercase()
        return when (subjectType) {
            "RELATED_PERSON", "RELATED" -> {
                val relation = JsonExtractor.stringField(json, "relation").orEmpty()
                val name = JsonExtractor.stringField(json, "subjectName")
                MemorySubject.RelatedPerson(relation, name)
            }
            "UNKNOWN" -> MemorySubject.Unknown
            else -> MemorySubject.User
        }
    }

    private fun parseEnum(value: String?, allowed: List<String>): String? {
        if (value.isNullOrBlank()) return null
        val normalized = value.trim().uppercase()
        return allowed.firstOrNull { it.equals(normalized, ignoreCase = true) }
    }
}
