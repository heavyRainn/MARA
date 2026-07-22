package com.care.voice.data.repository

import androidx.room.*
import com.care.voice.data.history.PendingActionEntity

@Dao
interface PendingActionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PendingActionEntity)

    @Query("SELECT * FROM pending_actions WHERE state = 'WAITING_CONFIRMATION' ORDER BY created_at DESC LIMIT 1")
    suspend fun loadActive(): PendingActionEntity?

    @Query("SELECT * FROM pending_actions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PendingActionEntity?

    @Query("UPDATE pending_actions SET state = :state WHERE id = :id")
    suspend fun updateState(id: String, state: String)

    @Query("UPDATE pending_actions SET state = 'EXPIRED' WHERE state = 'WAITING_CONFIRMATION' AND expires_at < :now")
    suspend fun expireOld(now: Long)
}
