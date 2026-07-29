package com.care.voice.brain.vision

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionSubmitGuardTest {

    @Test
    fun onlyOneActiveRequestAllowed() {
        val guard = VisionSubmitGuard()
        assertTrue(guard.tryBegin("req-1"))
        assertFalse(guard.tryBegin("req-2"))
        guard.finish("req-1")
        assertTrue(guard.tryBegin("req-2"))
    }

    @Test
    fun staleCallbackIgnoredAfterFinish() {
        val guard = VisionSubmitGuard()
        assertTrue(guard.tryBegin("req-1"))
        guard.finish("req-1")
        assertFalse(guard.isActive("req-1"))
    }

    @Test
    fun samePhotoCanBeSubmittedAgainAfterPreviousFinished() {
        val guard = VisionSubmitGuard()
        assertTrue(guard.tryBegin("req-1"))
        guard.finish("req-1")
        assertTrue(guard.tryBegin("req-2"))
    }
}
