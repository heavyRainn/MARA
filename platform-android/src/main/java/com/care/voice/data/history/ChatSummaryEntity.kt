package com.care.voice.data.history

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_summary")
data class ChatSummaryEntity(
    @PrimaryKey val sessionId: String,
    val summary: String,
    val messageCountAtSummary: Int
)
