package com.care.voice.brain.vision

/**
 * Ensures only one in-flight vision request at a time.
 */
class VisionSubmitGuard {
    @Volatile
    private var activeRequestId: String? = null

    fun tryBegin(requestId: String): Boolean {
        synchronized(this) {
            if (activeRequestId != null) return false
            activeRequestId = requestId
            return true
        }
    }

    fun isActive(requestId: String): Boolean = activeRequestId == requestId

    fun finish(requestId: String) {
        synchronized(this) {
            if (activeRequestId == requestId) {
                activeRequestId = null
            }
        }
    }

    fun reset() {
        synchronized(this) {
            activeRequestId = null
        }
    }
}
