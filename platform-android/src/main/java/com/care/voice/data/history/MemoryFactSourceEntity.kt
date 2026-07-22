package com.care.voice.data.history

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memory_fact_sources",
    indices = [Index("memory_fact_id"), Index("message_id")]
)
data class MemoryFactSourceEntity(
    @PrimaryKey val id: String,
    @androidx.room.ColumnInfo(name = "memory_fact_id") val memoryFactId: String,
    @androidx.room.ColumnInfo(name = "message_id") val messageId: String,
    @androidx.room.ColumnInfo(name = "source_type") val sourceType: String,
    val excerpt: String,
    @androidx.room.ColumnInfo(name = "created_at") val createdAt: Long
)
