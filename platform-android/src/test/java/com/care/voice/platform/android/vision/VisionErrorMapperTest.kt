package com.care.voice.platform.android.vision

import com.care.voice.brain.vision.VisionError
import org.junit.Assert.assertEquals
import org.junit.Test

class VisionErrorMapperTest {

    @Test
    fun http413MapsToUserFriendlyMessage() {
        val failure = VisionErrorMapper.mapHttp(413, "payload too large")
        assertEquals(VisionError.PayloadTooLarge, failure.error)
        assertEquals(
            "Фотография слишком большая. Попробуйте снять ещё раз",
            failure.userMessage,
        )
    }

    @Test
    fun http429MapsToBusyMessage() {
        val failure = VisionErrorMapper.mapHttp(429, "rate limit")
        assertEquals(
            "Сервис сейчас занят. Попробуйте немного позже",
            failure.userMessage,
        )
    }
}
