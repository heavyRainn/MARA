package com.care.voice.data.history

import androidx.room.*

@Dao
interface MessagesDao {

    @Insert
    suspend fun insert(e: MessageEntity): Long

    @Query("SELECT * FROM messages WHERE message_uid = :uid LIMIT 1")
    suspend fun getByUid(uid: String): MessageEntity?

    @Query("""
        SELECT * FROM messages 
        WHERE session_id = :sid AND state = 'ACTIVE'
        ORDER BY ts DESC 
        LIMIT :limit
    """)
    suspend fun lastActiveN(sid: String, limit: Int): List<MessageEntity>

    @Query("""
        SELECT * FROM messages 
        WHERE session_id = :sid 
        ORDER BY ts DESC 
        LIMIT :limit
    """)
    suspend fun lastN(sid: String, limit: Int): List<MessageEntity>

    @Query("""
        UPDATE messages SET state = 'ARCHIVED'
        WHERE session_id = :sid AND state = 'ACTIVE' AND id NOT IN (
            SELECT id FROM messages 
            WHERE session_id = :sid AND state = 'ACTIVE'
            ORDER BY ts DESC 
            LIMIT :keepActive
        )
    """)
    suspend fun archiveOldActive(sid: String, keepActive: Int)

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

    @Query("SELECT COUNT(*) FROM messages WHERE session_id = :sessionId AND state = 'ACTIVE'")
    suspend fun countActive(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM messages WHERE session_id = :sessionId")
    suspend fun count(sessionId: String): Int

    @Query("""
        SELECT * FROM messages
        WHERE session_id = :sessionId AND state != 'DELETED'
        AND (:afterId IS NULL OR id > (SELECT id FROM messages WHERE message_uid = :afterId LIMIT 1))
        ORDER BY ts ASC
    """)
    suspend fun messagesForSummary(sessionId: String, afterId: String?): List<MessageEntity>

    @Query("""
    UPDATE messages SET state = 'DELETED'
    WHERE id NOT IN (
        SELECT id FROM messages 
        WHERE session_id = :sessionId 
        ORDER BY ts DESC 
        LIMIT :keep
    )
    AND session_id = :sessionId AND state = 'ACTIVE'
    """)
    suspend fun deleteOldExceptLast(sessionId: String, keep: Int)
}
