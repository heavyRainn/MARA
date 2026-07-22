package com.care.voice.data.history

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pending_actions",
    indices = [Index("state"), Index("expires_at")]
)
data class PendingActionEntity(
    @PrimaryKey val id: String,
    val type: String,
    val payload: String,
    val state: String,
    @androidx.room.ColumnInfo(name = "created_at") val createdAt: Long,
    @androidx.room.ColumnInfo(name = "expires_at") val expiresAt: Long
)
