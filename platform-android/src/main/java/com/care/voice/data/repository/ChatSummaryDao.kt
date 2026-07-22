package com.care.voice.data.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.care.voice.data.history.ChatSummaryEntity

@Dao
interface ChatSummaryDao {

    @Query("SELECT * FROM chat_summary WHERE sessionId = :sessionId")
    suspend fun get(sessionId: String): ChatSummaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: ChatSummaryEntity)
}
