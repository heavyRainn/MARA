package com.care.voice.data.history

import androidx.room.*

@Entity(
    tableName = "messages",
    indices = [Index("session_id"), Index("ts")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: String,
    val role: String,          // "user" | "assistant" | "system" (если понадобится)
    val content: String,
    val ts: Long = System.currentTimeMillis()
)
