package com.care.voice.data.history

import androidx.room.*

@Dao
interface MessagesDao {

    @Insert
    suspend fun insert(e: MessageEntity)

    @Query("""
        SELECT * FROM messages 
        WHERE session_id = :sid 
        ORDER BY ts DESC 
        LIMIT :limit
    """)
    suspend fun lastN(sid: String, limit: Int): List<MessageEntity>

    @Query("""
        DELETE FROM messages 
        WHERE session_id = :sid AND id NOT IN (
            SELECT id FROM messages 
            WHERE session_id = :sid 
            ORDER BY ts DESC 
            LIMIT :keep
        )
    """)
    suspend fun pruneSession(sid: String, keep: Int)

    @Query("DELETE FROM messages WHERE session_id = :sid")
    suspend fun clearSession(sid: String)
}
