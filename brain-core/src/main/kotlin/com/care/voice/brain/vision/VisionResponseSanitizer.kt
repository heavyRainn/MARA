package com.care.voice.brain.vision

/**
 * Strips Qwen/Groq reasoning leakage before UI, Room history and TTS.
 *
 * Handles both closed `<think>...</think>` blocks and unclosed `<think>` prefixes.
 */
object VisionResponseSanitizer {

    private val closedThinkBlock = Regex(
        pattern = """(?is)<\s*think\s*>.*?<\s*/\s*think\s*>""",
    )
    private val openThinkTag = Regex(
        pattern = """(?is)<\s*think\s*>""",
    )
    private val leftoverCloseTag = Regex(
        pattern = """(?is)<\s*/\s*think\s*>""",
    )

    fun sanitize(content: String?, reasoningField: String? = null): String {
        // Separate API reasoning field must never reach the user.
        @Suppress("UNUSED_VARIABLE")
        val ignoredReasoning = reasoningField

        if (content.isNullOrBlank()) return ""

        var text = content
        text = closedThinkBlock.replace(text, " ")
        text = leftoverCloseTag.replace(text, " ")

        val openMatch = openThinkTag.find(text)
        if (openMatch != null) {
            // Unclosed <think>: drop everything from the tag onward.
            text = text.substring(0, openMatch.range.first)
        }

        return text
            .replace(Regex("""[ \t]+\n"""), "\n")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .replace(Regex("""[ \t]{2,}"""), " ")
            .trim()
    }
}
