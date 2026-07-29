package com.care.voice.platform.android.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageCompressionLogicTest {

    @Test
    fun buildDataUrlUsesJpegPrefixAndNoWrapBase64() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00)
        val dataUrl = ImageCompressionLogic.buildDataUrl(bytes)

        assertTrue(dataUrl.startsWith("data:image/jpeg;base64,"))
        assertFalse(dataUrl.contains("\n"))
        assertFalse(dataUrl.contains("\r"))
    }

    @Test
    fun scaleToMaxSideShrinksLargeImage() {
        val (width, height) = ImageCompressionLogic.scaleToMaxSide(4000, 3000, 1440)
        assertEquals(1440, width)
        assertEquals(1080, height)
    }

    @Test
    fun nextMaxSideReducesResolutionForRetry() {
        assertTrue(ImageCompressionLogic.nextMaxSide(1440) < 1440)
        assertEquals(480, ImageCompressionLogic.nextMaxSide(640))
    }

    @Test
    fun calculateInSampleSizePicksPowerOfTwo() {
        assertTrue(ImageCompressionLogic.calculateInSampleSize(4000, 3000, 1440) >= 2)
    }
}
