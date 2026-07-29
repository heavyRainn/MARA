package com.care.voice.platform.android.vision

import com.care.voice.brain.vision.VisionError
import com.care.voice.brain.vision.VisionResult
import com.care.voice.data.net.ErrorBody
import com.squareup.moshi.JsonEncodingException
import okhttp3.ResponseBody
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object VisionErrorMapper {

    fun mapHttp(code: Int, bodyMessage: String): VisionResult.Failure {
        val error = when (code) {
            400 -> VisionError.HttpBadRequest
            401, 403 -> VisionError.Unauthorized
            413 -> VisionError.PayloadTooLarge
            429 -> VisionError.RateLimited
            in 500..599 -> VisionError.Network
            else -> VisionError.Unknown("HTTP $code")
        }
        return VisionResult.Failure(error = error, userMessage = userMessage(error))
    }

    fun mapThrowable(t: Throwable): VisionResult.Failure {
        val error = when (t) {
            is SocketTimeoutException -> VisionError.Timeout
            is UnknownHostException -> VisionError.Network
            is IOException -> VisionError.Network
            else -> VisionError.Unknown(t.message ?: "unknown")
        }
        return VisionResult.Failure(error = error, userMessage = userMessage(error))
    }

    fun userMessage(error: VisionError): String = when (error) {
        VisionError.UriUnavailable -> "Не удалось прочитать фотографию"
        VisionError.FileTooLarge,
        VisionError.PayloadTooLarge,
        -> "Фотография слишком большая. Попробуйте снять ещё раз"
        VisionError.UnsupportedFormat -> "Не удалось прочитать фотографию"
        VisionError.HttpBadRequest -> "Не удалось прочитать фотографию"
        VisionError.RateLimited -> "Сервис сейчас занят. Попробуйте немного позже"
        VisionError.Timeout -> "Сервис сейчас занят. Попробуйте немного позже"
        VisionError.Network -> "Для анализа фотографии нужен интернет"
        VisionError.Unauthorized -> "Для анализа фотографии нужен интернет"
        VisionError.EmptyResponse -> "Не удалось прочитать фотографию"
        is VisionError.Unknown -> "Не удалось прочитать фотографию"
    }

    fun parseErrorBody(body: ResponseBody?, errorAdapter: com.squareup.moshi.JsonAdapter<ErrorBody>): String =
        runCatching {
            body?.string()?.let { errorAdapter.fromJson(it)?.error?.message }.orEmpty()
        }.getOrDefault("")
}
