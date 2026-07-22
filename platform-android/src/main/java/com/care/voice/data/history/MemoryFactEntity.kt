package com.care.voice.data.history

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memory_facts",
    indices = [
        Index("type"),
        Index("fact_key"),
        Index("status"),
        Index("valid_until"),
        Index("subject_type"),
        Index("updated_at")
    ]
)
data class MemoryFactEntity(
    @PrimaryKey val id: String,
    @androidx.room.ColumnInfo(name = "subject_type") val subjectType: String,
    @androidx.room.ColumnInfo(name = "subject_relation") val subjectRelation: String?,
    @androidx.room.ColumnInfo(name = "subject_name") val subjectName: String?,
    val type: String,
    @androidx.room.ColumnInfo(name = "fact_key") val factKey: String,
    val value: String,
    val confidence: Double,
    val importance: Int,
    @androidx.room.ColumnInfo(name = "valid_from") val validFrom: Long?,
    @androidx.room.ColumnInfo(name = "valid_until") val validUntil: Long?,
    val status: String,
    @androidx.room.ColumnInfo(name = "confirmation_status") val confirmationStatus: String,
    val sensitivity: String,
    @androidx.room.ColumnInfo(name = "created_at") val createdAt: Long,
    @androidx.room.ColumnInfo(name = "updated_at") val updatedAt: Long,
    @androidx.room.ColumnInfo(name = "last_confirmed_at") val lastConfirmedAt: Long?,
    @androidx.room.ColumnInfo(name = "last_used_at") val lastUsedAt: Long?
)
