package com.care.voice.data.repository

import androidx.room.*
import com.care.voice.data.history.ConversationSessionEntity

@Dao
interface ConversationSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: ConversationSessionEntity)

    @Query("SELECT * FROM conversation_sessions WHERE id = :id LIMIT 1")
    suspend fun get(id: String): ConversationSessionEntity?

    @Query("SELECT * FROM conversation_sessions ORDER BY last_activity_at DESC LIMIT 1")
    suspend fun latest(): ConversationSessionEntity?

    @Query("UPDATE conversation_sessions SET exclude_from_extraction = 1 WHERE id = :id")
    suspend fun markExclude(id: String)

    @Query("SELECT exclude_from_extraction FROM conversation_sessions WHERE id = :id LIMIT 1")
    suspend fun isExcluded(id: String): Boolean?
}
