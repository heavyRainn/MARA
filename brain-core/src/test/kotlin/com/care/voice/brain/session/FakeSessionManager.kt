package com.care.voice.brain.session

import java.time.Instant

class FakeSessionManager(
    private var sessionId: String = "test-session"
) : SessionManager {
    var excluded = false

    override suspend fun currentSessionId(): String = sessionId

    override suspend fun touchActivity(now: Instant) = Unit

    override suspend fun markExcludeFromExtraction(sessionId: String) {
        excluded = true
    }

    override suspend fun isExcludedFromExtraction(sessionId: String): Boolean = excluded
}
