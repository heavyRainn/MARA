package com.care.voice.data.history

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversation_summary")
data class ConversationSummary(
    @PrimaryKey val sessionId: String,
    val summary: String
)
