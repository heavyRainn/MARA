package com.care.voice.data.history

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memory_tombstones",
    indices = [Index("subject_type"), Index("type"), Index("tombstone_key")]
)
data class MemoryTombstoneEntity(
    @PrimaryKey val id: String,
    @androidx.room.ColumnInfo(name = "subject_type") val subjectType: String,
    @androidx.room.ColumnInfo(name = "subject_relation") val subjectRelation: String?,
    @androidx.room.ColumnInfo(name = "subject_name") val subjectName: String?,
    val type: String,
    @androidx.room.ColumnInfo(name = "tombstone_key") val tombstoneKey: String,
    @androidx.room.ColumnInfo(name = "value_hash") val valueHash: String?,
    @androidx.room.ColumnInfo(name = "created_at") val createdAt: Long,
    val reason: String
)
