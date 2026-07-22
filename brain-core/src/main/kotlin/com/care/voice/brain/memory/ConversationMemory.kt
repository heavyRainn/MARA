package com.care.voice.brain.memory

data class ConversationMemory(
    val sessionId: String,
    val recentMessages: List<MemoryMessage>,
    val summary: String?,
    val messageCountAtSummary: Int = 0,
    val profile: UserProfile
)
