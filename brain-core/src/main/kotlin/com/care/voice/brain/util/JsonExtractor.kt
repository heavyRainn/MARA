package com.care.voice.brain.util

object JsonExtractor {
    fun extractObject(raw: String): String? {
        val cleaned = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        if (cleaned.equals("null", ignoreCase = true)) return null
        if (cleaned.startsWith("{") && cleaned.endsWith("}")) return cleaned
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        return if (start >= 0 && end > start) cleaned.substring(start, end + 1) else null
    }

    fun stringField(json: String, field: String): String? {
        val pattern = Regex(""""$field"\s*:\s*"((?:\\.|[^"\\])*)"""")
        return pattern.find(json)?.groupValues?.get(1)?.unescapeJson()
    }

    fun intField(json: String, field: String): Int? {
        val pattern = Regex(""""$field"\s*:\s*(-?\d+)""")
        return pattern.find(json)?.groupValues?.get(1)?.toIntOrNull()
    }

    fun booleanField(json: String, field: String): Boolean? {
        val pattern = Regex(""""$field"\s*:\s*(true|false)""", RegexOption.IGNORE_CASE)
        return pattern.find(json)?.groupValues?.get(1)?.toBooleanStrictOrNull()
    }

    fun longField(json: String, field: String): Long? {
        val pattern = Regex(""""$field"\s*:\s*(-?\d+)""")
        return pattern.find(json)?.groupValues?.get(1)?.toLongOrNull()
    }

    fun doubleField(json: String, field: String): Double? {
        val pattern = Regex(""""$field"\s*:\s*(-?\d+(?:\.\d+)?)""")
        return pattern.find(json)?.groupValues?.get(1)?.toDoubleOrNull()
    }

    private fun String.unescapeJson(): String =
        replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\\\", "\\")
}
