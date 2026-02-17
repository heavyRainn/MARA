package com.care.voice.data.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.care.voice.data.history.ConversationSummary

@Dao
interface ConversationSummaryDao {

    @Query("SELECT * FROM conversation_summary WHERE sessionId = :sid")
    suspend fun get(sid: String): ConversationSummary?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(summary: ConversationSummary)

    @Query("DELETE FROM conversation_summary WHERE sessionId = :sid")
    suspend fun clear(sid: String)
}
