package com.care.voice.data.repository

import androidx.room.*
import com.care.voice.data.history.ReminderEntity

@Dao
interface ReminderDao {
    @Insert
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ReminderEntity?

    @Delete
    suspend fun delete(reminder: ReminderEntity)
}
