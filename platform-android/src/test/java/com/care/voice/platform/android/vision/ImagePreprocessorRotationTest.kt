package com.care.voice.platform.android.vision

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class ImagePreprocessorRotationTest {

    private val preprocessor = ImagePreprocessor(RuntimeEnvironment.getApplication())

    @Test
    fun rotateBitmapSwapsDimensionsFor90Degrees() {
        val source = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
        source.eraseColor(Color.RED)
        val rotated = preprocessor.rotateBitmap(source, 90)
        assertEquals(80, rotated.width)
        assertEquals(120, rotated.height)
        assertNotEquals(source.width, rotated.width)
        rotated.recycle()
    }

    @Test
    fun readExifRotationReturnsZeroForInvalidBytes() {
        assertEquals(0, preprocessor.readExifRotationDegrees(byteArrayOf(0, 1, 2)))
    }
}
