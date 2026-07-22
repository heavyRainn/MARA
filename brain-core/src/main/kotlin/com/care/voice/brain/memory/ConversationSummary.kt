package com.care.voice.brain.memory

data class ConversationSummary(
    val sessionId: String,
    val text: String,
    val messageCountAtSummary: Int
)
