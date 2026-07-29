package com.care.voice.platform.android.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.care.voice.brain.vision.VisionError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max

data class PreprocessedImage(
    val jpegBytes: ByteArray,
    val dataUrl: String,
    val originalWidth: Int,
    val originalHeight: Int,
    val outputWidth: Int,
    val outputHeight: Int,
    val originalSizeBytes: Long,
    val outputSizeBytes: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PreprocessedImage) return false
        return jpegBytes.contentEquals(other.jpegBytes) &&
            dataUrl == other.dataUrl &&
            originalWidth == other.originalWidth &&
            originalHeight == other.originalHeight &&
            outputWidth == other.outputWidth &&
            outputHeight == other.outputHeight &&
            originalSizeBytes == other.originalSizeBytes &&
            outputSizeBytes == other.outputSizeBytes
    }

    override fun hashCode(): Int = jpegBytes.contentHashCode()
}

sealed interface PreprocessResult {
    data class Success(val image: PreprocessedImage) : PreprocessResult

    data class Failure(
        val error: VisionError,
        val userMessage: String,
    ) : PreprocessResult
}

class ImagePreprocessor(
    private val context: Context,
) {
    suspend fun preprocess(uri: Uri): PreprocessResult = withContext(Dispatchers.IO) {
        try {
            val sourceBytes = readUriBytes(uri)
                ?: return@withContext failure(VisionError.UriUnavailable)

            if (sourceBytes.isEmpty()) {
                return@withContext failure(VisionError.UriUnavailable)
            }

            val originalSizeBytes = context.contentResolver.openFileDescriptor(uri, "r")
                ?.use { it.statSize }
                ?.coerceAtLeast(0L)
                ?: sourceBytes.size.toLong()

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return@withContext failure(VisionError.UnsupportedFormat)
            }

            var maxSide = ImageCompressionLogic.MAX_SIDE_PX
            var jpegBytes: ByteArray? = null
            var outputWidth = 0
            var outputHeight = 0

            repeat(MAX_COMPRESSION_ATTEMPTS) {
                val bitmap = decodeRotateAndScale(sourceBytes, maxSide)
                    ?: return@withContext failure(VisionError.UnsupportedFormat)

                outputWidth = bitmap.width
                outputHeight = bitmap.height
                jpegBytes = compressToJpeg(bitmap)
                bitmap.recycle()

                if (jpegBytes!!.size <= ImageCompressionLogic.MAX_JPEG_BYTES) {
                    return@withContext success(
                        jpegBytes = jpegBytes!!,
                        originalWidth = bounds.outWidth,
                        originalHeight = bounds.outHeight,
                        outputWidth = outputWidth,
                        outputHeight = outputHeight,
                        originalSizeBytes = originalSizeBytes,
                    )
                }

                maxSide = ImageCompressionLogic.nextMaxSide(maxSide)
                if (maxSide < ImageCompressionLogic.MIN_SIDE_PX) {
                    return@withContext failure(VisionError.FileTooLarge)
                }
            }

            failure(VisionError.FileTooLarge)
        } catch (_: SecurityException) {
            failure(VisionError.UriUnavailable)
        } catch (_: Exception) {
            failure(VisionError.UnsupportedFormat)
        }
    }

    private fun readUriBytes(uri: Uri): ByteArray? =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }

    private fun decodeRotateAndScale(sourceBytes: ByteArray, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size, bounds)

        val sampleSize = ImageCompressionLogic.calculateInSampleSize(
            width = bounds.outWidth,
            height = bounds.outHeight,
            maxSide = maxSide,
        )
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bitmap = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size, decodeOptions)
            ?: return null

        val rotation = readExifRotationDegrees(sourceBytes)
        if (rotation != 0) {
            bitmap = rotateBitmap(bitmap, rotation)
        }

        val (targetW, targetH) = ImageCompressionLogic.scaleToMaxSide(
            width = bitmap.width,
            height = bitmap.height,
            maxSide = maxSide,
        )
        if (targetW == bitmap.width && targetH == bitmap.height) {
            return bitmap
        }

        val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        if (scaled !== bitmap) {
            bitmap.recycle()
        }
        return scaled
    }

    internal fun readExifRotationDegrees(sourceBytes: ByteArray): Int {
        return runCatching {
            ExifInterface(ByteArrayInputStream(sourceBytes)).rotationDegrees
        }.getOrDefault(0)
    }

    internal fun rotateBitmap(source: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return source
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (rotated !== source) {
            source.recycle()
        }
        return rotated
    }

    private fun compressToJpeg(bitmap: Bitmap): ByteArray {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, ImageCompressionLogic.JPEG_QUALITY, output)
        return output.toByteArray()
    }

    private fun success(
        jpegBytes: ByteArray,
        originalWidth: Int,
        originalHeight: Int,
        outputWidth: Int,
        outputHeight: Int,
        originalSizeBytes: Long,
    ): PreprocessResult.Success {
        val dataUrl = ImageCompressionLogic.buildDataUrl(jpegBytes)
        return PreprocessResult.Success(
            PreprocessedImage(
                jpegBytes = jpegBytes,
                dataUrl = dataUrl,
                originalWidth = originalWidth,
                originalHeight = originalHeight,
                outputWidth = outputWidth,
                outputHeight = outputHeight,
                originalSizeBytes = originalSizeBytes,
                outputSizeBytes = jpegBytes.size,
            ),
        )
    }

    private fun failure(error: VisionError): PreprocessResult.Failure =
        PreprocessResult.Failure(
            error = error,
            userMessage = VisionErrorMapper.userMessage(error),
        )

    companion object {
        private const val MAX_COMPRESSION_ATTEMPTS = 5
    }
}
