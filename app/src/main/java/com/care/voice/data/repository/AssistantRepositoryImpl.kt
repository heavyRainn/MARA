package com.care.voice.data.repository

import com.care.voice.data.history.ChatHistoryRepository
import com.care.voice.data.net.ChatRequest
import com.care.voice.data.net.ErrorBody
import com.care.voice.data.net.LlmApi
import com.care.voice.data.net.Message
import com.care.voice.domain.repository.AssistantRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.ResponseBody
import retrofit2.HttpException

class AssistantRepositoryImpl(
    private val api: LlmApi,
    private val model: String,
    private val history: ChatHistoryRepository,
    private val sessionIdProvider: () -> String = { "default" },
    private val historyTail: Int = 8
) : AssistantRepository {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val errorAdapter = moshi.adapter(ErrorBody::class.java)

    private val SYSTEM_PROMPT =
        "Ты доброжелательный голосовой помощник для пожилых людей. " +
                "Отвечай просто и кратко. ОТВЕЧАЙ ТОЛЬКО НА РУССКОМ ЯЗЫКЕ."

    /** Основной вызов: берём хвост истории → спрашиваем модель → записываем диалог в Room. */
    override suspend fun chat(userText: String): Result<String> = runCatching {
        val sessionId = sessionIdProvider()

        // 1) берём последние реплики текущей сессии (user/assistant) в хронологическом порядке
        val tail = history.tail(sessionId, historyTail).map {
            Message(role = it.role, content = it.content)
        }

        // 2) собираем запрос
        val body = ChatRequest(
            model = model,
            messages = listOf(Message("system", SYSTEM_PROMPT)) +
                    tail +
                    Message("user", userText),
            stream = false,
            temperature = 0.3
        )

        // 3) спрашиваем LLM
        val resp = api.chat(body)
        val answer = resp.choices.firstOrNull()?.message?.content.orEmpty()

        // 4) записываем новую пару в историю
        history.append(sessionId, "user", userText)
        history.append(sessionId, "assistant", answer)

        answer
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
        runCatching { errorAdapter.fromJson(rb.string())?.error?.message ?: "" }
            .getOrDefault("")
}
