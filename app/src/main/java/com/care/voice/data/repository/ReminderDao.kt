package com.care.voice.data.repository

import androidx.room.*
import com.care.voice.data.history.ReminderEntity

@Dao
interface ReminderDao {

    @Insert
    suspend fun insert(reminder: ReminderEntity): Long

    @Query("SELECT * FROM reminders")
    suspend fun getAll(): List<ReminderEntity>

    @Delete
    suspend fun delete(reminder: ReminderEntity)
}