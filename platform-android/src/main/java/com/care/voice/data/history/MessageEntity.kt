package com.care.voice.data.history

import androidx.room.*

@Entity(
    tableName = "messages",
    indices = [Index("session_id"), Index("ts"), Index("message_uid"), Index("state")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "message_uid") val messageUid: String,
    val role: String,
    val content: String,
    val ts: Long = System.currentTimeMillis(),
    val state: String = "ACTIVE"
)
