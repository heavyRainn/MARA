package com.care.voice.platform.android.llm

import com.care.voice.brain.llm.LanguageModel
import com.care.voice.brain.llm.LlmError
import com.care.voice.brain.llm.LlmRequest
import com.care.voice.brain.llm.LlmResponse
import com.care.voice.brain.llm.LlmResult
import com.care.voice.data.net.ErrorBody
import com.care.voice.data.net.LlmApi
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import okhttp3.ResponseBody
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Android-адаптер Groq API к переносимому контракту [LanguageModel].
 */
class GroqLanguageModel(
    private val api: LlmApi,
    private val model: String
) : LanguageModel {

    private val errorAdapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(ErrorBody::class.java)

    override suspend fun generate(request: LlmRequest): LlmResult<LlmResponse> =
        try {
            val response = api.chat(request.toNetwork(model))
            val content = response.choices.firstOrNull()?.message?.content?.trim().orEmpty()
            if (content.isBlank()) {
                LlmResult.Failure(LlmError.Unknown("Пустой ответ модели"))
            } else {
                LlmResult.Success(LlmResponse(content))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            LlmResult.Failure(mapHttpError(e))
        } catch (e: SocketTimeoutException) {
            LlmResult.Failure(LlmError.Network("Таймаут сети"))
        } catch (e: UnknownHostException) {
            LlmResult.Failure(LlmError.Network("Нет подключения к интернету"))
        } catch (e: IOException) {
            LlmResult.Failure(LlmError.Network(e.message ?: "Сетевая ошибка"))
        } catch (e: JsonEncodingException) {
            LlmResult.Failure(LlmError.Unknown("Некорректный JSON в ответе"))
        } catch (e: Exception) {
            LlmResult.Failure(LlmError.Unknown(e.message ?: "Неизвестная ошибка"))
        }

    private fun mapHttpError(e: HttpException): LlmError {
        val code = e.code()
        val bodyMessage = e.response()?.errorBody()?.let(::parseErrorBody).orEmpty()
        val message = buildString {
            append("HTTP $code")
            if (bodyMessage.isNotBlank()) append(" • ").append(bodyMessage)
            when (code) {
                401, 403 -> append(" • Ключ API пустой/невалидный или нет доступа.")
                429 -> append(" • Лимит запросов исчерпан.")
                500, 502, 503 -> append(" • Сервер недоступен, попробуйте позже.")
            }
        }
        return when (code) {
            401, 403 -> LlmError.Unauthorized(message)
            429 -> LlmError.RateLimited(message)
            500, 502, 503 -> LlmError.Server(message)
            in 400..499 -> LlmError.Network(message)
            else -> LlmError.Unknown(message)
        }
    }

    private fun parseErrorBody(body: ResponseBody): String =
        runCatching { errorAdapter.fromJson(body.string())?.error?.message.orEmpty() }.getOrDefault("")
}
