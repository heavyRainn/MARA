package com.care.voice.platform.android.vision

import android.util.Base64
import kotlin.math.max
import kotlin.math.roundToInt

object ImageCompressionLogic {
    const val MAX_SIDE_PX = 1440
    const val JPEG_QUALITY = 80
    const val MAX_JPEG_BYTES = 1_572_864 // 1.5 MB
    const val MIN_SIDE_PX = 480

    fun scaleToMaxSide(width: Int, height: Int, maxSide: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return width to height
        val longest = max(width, height)
        if (longest <= maxSide) return width to height
        val scale = maxSide.toFloat() / longest.toFloat()
        val newW = (width * scale).roundToInt().coerceAtLeast(1)
        val newH = (height * scale).roundToInt().coerceAtLeast(1)
        return newW to newH
    }

    fun calculateInSampleSize(width: Int, height: Int, maxSide: Int): Int {
        var inSampleSize = 1
        var halfW = width / 2
        var halfH = height / 2
        while (halfW / inSampleSize >= maxSide || halfH / inSampleSize >= maxSide) {
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }

    fun nextMaxSide(currentMaxSide: Int): Int =
        (currentMaxSide * 0.75f).roundToInt().coerceAtLeast(MIN_SIDE_PX)

    fun buildDataUrl(jpegBytes: ByteArray): String {
        val base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        return "data:image/jpeg;base64,$base64"
    }
}
