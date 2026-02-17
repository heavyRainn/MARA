package com.care.voice.data.history

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String?,
    val age: Int?,
    val conditions: String?,     // "диабет, давление"
    val medications: String?,    // "метформин"
    val notes: String?           // любые важные факты
)
