package com.care.voice.ui.feature.photo

object PhotoCommandMatcher {
    private val triggers = listOf(
        "сфотографируй",
        "сделай фото",
        "сделай снимок",
        "открой камеру",
        "открой фото",
        "включи камеру",
        "нажми фото",
    )

    fun matches(text: String): Boolean {
        val lower = text.trim().lowercase()
        if (lower.isBlank()) return false
        return triggers.any { lower.contains(it) }
    }
}
