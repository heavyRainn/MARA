package com.care.voice.brain.speech

object SpeechChunker {
    private const val TARGET_MIN = 150
    private const val TARGET_MAX = 350
    private const val HARD_MAX = 500

    private val sentenceEndRegex = Regex("""(?<=[.!?…])\s+""")
    private val decimalRegex = Regex("""\d+[.,]\d+""")
    private val timeRegex = Regex("""\b\d{1,2}[:.]\d{2}\b""")
    private val initialsRegex = Regex("""\b[А-ЯA-Z]\.\s?[А-ЯA-Z]\.""")

    fun chunk(
        text: String,
        requestId: String,
        chunkPauseMs: Int = SpeechTuning.DEFAULT_CHUNK_PAUSE_MS,
        paragraphPauseMs: Int = SpeechTuning.DEFAULT_PARAGRAPH_PAUSE_MS,
    ): List<SpeechChunk> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        val paragraphs = trimmed
            .split(RussianSpeechTextNormalizer.PARAGRAPH_MARKER)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val rawChunks = mutableListOf<Pair<String, Boolean>>()
        paragraphs.forEachIndexed { paragraphIndex, paragraph ->
            val isLastParagraph = paragraphIndex == paragraphs.lastIndex
            val paragraphChunks = chunkParagraph(paragraph)
            paragraphChunks.forEachIndexed { index, chunkText ->
                val isLastInParagraph = index == paragraphChunks.lastIndex
                rawChunks += chunkText to (isLastInParagraph && !isLastParagraph)
            }
        }

        if (rawChunks.isEmpty()) return emptyList()

        return rawChunks.mapIndexed { index, (chunkText, endsParagraph) ->
            val isLastOverall = index == rawChunks.lastIndex
            SpeechChunk(
                chunkId = "$requestId-chunk-$index",
                index = index,
                text = chunkText,
                pauseAfterMs = when {
                    isLastOverall -> 0
                    endsParagraph -> paragraphPauseMs
                    else -> chunkPauseMs
                },
            )
        }
    }

    private fun chunkParagraph(paragraph: String): List<String> {
        val protected = protectBoundaries(paragraph)
        val rawSentences = protected.text.split(sentenceEndRegex).map { it.trim() }.filter { it.isNotEmpty() }
        val merged = mergeShortSentences(rawSentences)
        return splitOversized(merged).map { restoreBoundaries(it, protected.placeholders) }
    }

    private data class ProtectedText(
        val text: String,
        val placeholders: Map<String, String>,
    )

    private fun protectBoundaries(text: String): ProtectedText {
        val placeholders = linkedMapOf<String, String>()
        var current = text
        fun protect(regex: Regex, prefix: String) {
            regex.findAll(current).toList().reversed().forEach { match ->
                val key = "§${prefix}${placeholders.size}§"
                placeholders[key] = match.value
                current = current.replaceRange(match.range, key)
            }
        }
        protect(decimalRegex, "D")
        protect(timeRegex, "T")
        protect(initialsRegex, "I")
        return ProtectedText(current, placeholders)
    }

    private fun restoreBoundaries(text: String, placeholders: Map<String, String>): String {
        var result = text
        placeholders.forEach { (key, value) ->
            result = result.replace(key, value)
        }
        return result.trim()
    }

    private fun mergeShortSentences(sentences: List<String>): List<String> {
        if (sentences.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        var buffer = StringBuilder()
        for (sentence in sentences) {
            if (buffer.isEmpty()) {
                buffer.append(sentence)
            } else if (buffer.length < TARGET_MIN) {
                buffer.append(' ').append(sentence)
            } else {
                result += buffer.toString()
                buffer = StringBuilder(sentence)
            }
        }
        if (buffer.isNotEmpty()) result += buffer.toString()
        return result
    }

    private fun splitOversized(chunks: List<String>): List<String> {
        val result = mutableListOf<String>()
        for (chunk in chunks) {
            if (chunk.length <= HARD_MAX) {
                result += chunk
                continue
            }
            var start = 0
            while (start < chunk.length) {
                val end = (start + TARGET_MAX).coerceAtMost(chunk.length)
                val sliceEnd = if (end == chunk.length) {
                    end
                } else {
                    chunk.lastIndexOf(' ', end).takeIf { it > start } ?: end
                }
                result += chunk.substring(start, sliceEnd).trim()
                start = sliceEnd
            }
        }
        return result.filter { it.isNotEmpty() }
    }
}
