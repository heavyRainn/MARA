package com.care.voice.brain.memory.extract

import com.care.voice.brain.memory.MemoryMessage
import com.care.voice.brain.memory.fact.MemoryCandidate

interface MemoryExtractor {
    suspend fun extract(
        userMessage: MemoryMessage,
        recentContext: List<MemoryMessage>
    ): List<MemoryCandidate>
}
