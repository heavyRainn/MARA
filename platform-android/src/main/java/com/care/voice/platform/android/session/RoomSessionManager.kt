package com.care.voice.platform.android.session

import com.care.voice.brain.session.ConversationSession
import com.care.voice.brain.session.SessionManager
import com.care.voice.data.history.ConversationSessionEntity
import com.care.voice.data.repository.ConversationSessionDao
import com.care.voice.platform.android.persistence.toBrain
import com.care.voice.platform.android.persistence.toEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class RoomSessionManager(
    private val dao: ConversationSessionDao,
    private val inactivityHours: Long = 8,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : SessionManager {

    @Volatile
    private var cachedSessionId: String? = null

    override suspend fun currentSessionId(): String {
        cachedSessionId?.let { return it }
        val now = Instant.now()
        val latest = dao.latest()?.toBrain()
        if (latest != null && !shouldStartNewSession(latest, now)) {
            cachedSessionId = latest.id
            touchActivity(now)
            return latest.id
        }
        val session = ConversationSession(
            id = UUID.randomUUID().toString(),
            startedAt = now,
            lastActivityAt = now
        )
        dao.upsert(session.toEntity())
        cachedSessionId = session.id
        return session.id
    }

    override suspend fun touchActivity(now: Instant) {
        val id = cachedSessionId ?: dao.latest()?.id ?: return
        val existing = dao.get(id)?.toBrain() ?: return
        dao.upsert(existing.copy(lastActivityAt = now).toEntity())
    }

    override suspend fun markExcludeFromExtraction(sessionId: String) {
        dao.markExclude(sessionId)
    }

    override suspend fun isExcludedFromExtraction(sessionId: String): Boolean =
        dao.isExcluded(sessionId) == true

    private fun shouldStartNewSession(session: ConversationSession, now: Instant): Boolean {
        val inactiveMs = now.toEpochMilli() - session.lastActivityAt.toEpochMilli()
        if (inactiveMs > inactivityHours * 3_600_000) return true
        val lastDay = LocalDate.ofInstant(session.lastActivityAt, zoneId)
        val today = LocalDate.ofInstant(now, zoneId)
        return today.isAfter(lastDay)
    }
}
