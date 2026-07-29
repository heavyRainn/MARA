package com.care.voice.platform.android.vision

import com.care.voice.brain.vision.VisionError
import com.care.voice.brain.vision.VisionProvider
import com.care.voice.brain.vision.VisionRequest
import com.care.voice.brain.vision.VisionResponseSanitizer
import com.care.voice.brain.vision.VisionResult
import com.care.voice.data.net.ErrorBody
import com.care.voice.data.net.VisionApi
import com.care.voice.data.net.VisionChatResponseDto
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

class GroqVisionProvider(
    private val api: VisionApi,
    private val model: String,
) : VisionProvider {

    private val errorAdapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(ErrorBody::class.java)

    override suspend fun analyze(request: VisionRequest): VisionResult {
        if (request.question.isBlank()) {
            return VisionResult.Failure(
                error = VisionError.HttpBadRequest,
                userMessage = VisionErrorMapper.userMessage(VisionError.HttpBadRequest),
            )
        }
        if (request.image.dataUrl.isBlank() || request.image.jpegBytes.isEmpty()) {
            return VisionResult.Failure(
                error = VisionError.UriUnavailable,
                userMessage = VisionErrorMapper.userMessage(VisionError.UriUnavailable),
            )
        }

        return try {
            val first = executeSanitized(request, allowReasoningFormatHidden = true)
            if (first != null) return first

            // One retry when sanitize produced empty text (still with reasoning_effort=none).
            val second = executeSanitized(request, allowReasoningFormatHidden = true)
            if (second != null) return second

            VisionResult.Failure(
                error = VisionError.EmptyResponse,
                userMessage = VisionPrompt.EMPTY_AFTER_SANITIZE_MESSAGE,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            val bodyMessage = VisionErrorMapper.parseErrorBody(e.response()?.errorBody(), errorAdapter)
            VisionErrorMapper.mapHttp(e.code(), bodyMessage)
        } catch (e: JsonEncodingException) {
            VisionResult.Failure(
                error = VisionError.Unknown("invalid_json"),
                userMessage = VisionErrorMapper.userMessage(VisionError.EmptyResponse),
            )
        } catch (e: Exception) {
            VisionErrorMapper.mapThrowable(e)
        }
    }

    /**
     * @return Success with sanitized text, or null when sanitized text is empty (caller may retry).
     */
    private suspend fun executeSanitized(
        request: VisionRequest,
        allowReasoningFormatHidden: Boolean,
    ): VisionResult.Success? {
        val response = callApi(request, includeReasoningFormatHidden = allowReasoningFormatHidden)
        val message = response.choices.firstOrNull()?.message
        val sanitized = VisionResponseSanitizer.sanitize(
            content = message?.content,
            reasoningField = message?.reasoning,
        )
        return if (sanitized.isBlank()) null else VisionResult.Success(sanitized)
    }

    private suspend fun callApi(
        request: VisionRequest,
        includeReasoningFormatHidden: Boolean,
    ): VisionChatResponseDto {
        val networkRequest = request.toVisionChatRequest(
            model = model,
            includeReasoningFormatHidden = includeReasoningFormatHidden,
        )
        return try {
            api.chat(networkRequest)
        } catch (e: HttpException) {
            // Combination with reasoning_format=hidden may be rejected — fall back without it.
            if (e.code() == 400 && includeReasoningFormatHidden) {
                api.chat(
                    request.toVisionChatRequest(
                        model = model,
                        includeReasoningFormatHidden = false,
                    ),
                )
            } else {
                throw e
            }
        }
    }

    companion object {
        const val GROQ_VISION_MODEL = "qwen/qwen3.6-27b"
    }
}
