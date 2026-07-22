package com.care.voice.data.history

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversation_sessions")
data class ConversationSessionEntity(
    @PrimaryKey val id: String,
    @androidx.room.ColumnInfo(name = "started_at") val startedAt: Long,
    @androidx.room.ColumnInfo(name = "last_activity_at") val lastActivityAt: Long,
    @androidx.room.ColumnInfo(name = "exclude_from_extraction") val excludeFromExtraction: Boolean = false
)
