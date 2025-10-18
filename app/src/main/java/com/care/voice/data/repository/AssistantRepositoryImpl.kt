package com.care.voice.data.repository

import com.care.voice.data.net.ChatRequest
import com.care.voice.data.net.LlmApi
import com.care.voice.data.net.Message
import com.care.voice.domain.repository.AssistantRepository
import retrofit2.HttpException
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.care.voice.data.net.ErrorBody
import okhttp3.ResponseBody

class AssistantRepositoryImpl(
    private val api: LlmApi
) : AssistantRepository {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val errorAdapter = moshi.adapter(ErrorBody::class.java)

    private val SYSTEM_PROMPT =
        "Ты доброжелательный голосовой помощник для пожилых людей. " +
                "Отвечай просто и кратко. ОТВЕЧАЙ ТОЛЬКО НА РУССКОМ ЯЗЫКЕ."


    override suspend fun chat(userText: String): Result<String> = runCatching {
        val resp = api.chat(
            ChatRequest(
                model = "llama-3.1-8b-instant", // ✅ модель Groq (можно 70b/405b, но начнём с 8b)
                messages = listOf(
                    Message("system", SYSTEM_PROMPT),
                    Message("user", userText)
                ),
                stream = false,
                temperature = 0.3
            )
        )
        resp.choices.firstOrNull()?.message?.content.orEmpty()
    }.recoverCatching { e ->
        if (e is HttpException) throw RuntimeException(httpErrorToMessage(e))
        else throw e
    }

    private fun httpErrorToMessage(e: HttpException): String {
        val code = e.code()
        val body = e.response()?.errorBody()?.let(::parseErrorBody)
        val hint = when (code) {
            401 -> "Ключ API пустой/невалидный или нет доступа."
            429 -> "Лимит запросов исчерпан."
            500, 502, 503 -> "Сервер недоступен, попробуйте позже."
            else -> null
        }
        return buildString {
            append("HTTP $code")
            if (!body.isNullOrBlank()) append(" • ").append(body)
            if (!hint.isNullOrBlank()) append(" • ").append(hint)
        }
    }
    private fun parseErrorBody(rb: ResponseBody): String =
        runCatching { errorAdapter.fromJson(rb.string())?.error?.message ?: "" }.getOrDefault("")
}
