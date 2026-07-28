package com.care.voice.data.repository

import androidx.room.*
import com.care.voice.brain.reminder.ReminderStatus
import com.care.voice.data.history.ReminderEntity

@Dao
interface ReminderDao {
    @Insert
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE status = :status AND triggerAt > :now")
    suspend fun findByStatusAfterTrigger(status: ReminderStatus, now: Long): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE status = :status")
    suspend fun findByStatus(status: ReminderStatus): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE status IN (:statuses)")
    suspend fun findByStatuses(statuses: List<ReminderStatus>): List<ReminderEntity>

    @Query(
        """
        UPDATE reminders SET
            voiceDeliveryStatus = 'PENDING',
            voiceRequestedAt = :now,
            updatedAt = :now
        WHERE id = :id AND voiceDeliveryStatus = 'NOT_REQUESTED'
        """
    )
    suspend fun tryMarkVoicePending(id: Long, now: Long): Int

    @Query("UPDATE reminders SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: ReminderStatus, updatedAt: Long)

    @Delete
    suspend fun delete(reminder: ReminderEntity)
}
