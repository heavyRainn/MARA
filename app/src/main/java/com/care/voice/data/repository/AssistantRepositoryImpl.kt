package com.care.voice.data.repository

import com.care.voice.data.dto.ReminderExtraction
import com.care.voice.data.history.ChatHistoryRepository
import com.care.voice.data.history.ReminderEntity
import com.care.voice.data.history.UserProfileEntity
import com.care.voice.data.net.ChatRequest
import com.care.voice.data.net.ErrorBody
import com.care.voice.data.net.LlmApi
import com.care.voice.data.net.Message
import com.care.voice.data.reminder.ReminderScheduler
import com.care.voice.domain.repository.AssistantRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.ResponseBody
import retrofit2.HttpException

class AssistantRepositoryImpl(
    private val api: LlmApi,
    private val model: String,
    private val history: ChatHistoryRepository,
    private val userProfileDao: UserProfileDao,
    private val reminderDao: ReminderDao,              // ← добавить
    private val reminderScheduler: ReminderScheduler,  // ← добавить
    private val sessionIdProvider: () -> String = { "default" },
    private val historyTail: Int = 8
) : AssistantRepository {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val errorAdapter = moshi.adapter(ErrorBody::class.java)
    private val profileAdapter = moshi.adapter(ProfileExtraction::class.java)
    private val reminderAdapter = moshi.adapter(ReminderExtraction::class.java)


    private val SYSTEM_PROMPT =
        "Ты доброжелательный голосовой помощник для пожилых людей. " +
                "Отвечай просто и кратко. ОТВЕЧАЙ ТОЛЬКО НА РУССКОМ ЯЗЫКЕ."

    override suspend fun chat(userText: String): Result<String> = runCatching {

        val sessionId = sessionIdProvider()

        // 🔔 0️⃣ Пытаемся извлечь напоминание
        val reminder = extractReminder(userText)
        var reminderCreated = false

        if (reminder != null) {
            val id = reminderDao.insert(reminder)
            reminderScheduler.schedule(reminder.copy(id = id))
            reminderCreated = true
        }

        // 1️⃣ Получаем профиль пользователя
        val profile = userProfileDao.get()
        val profileContext = buildProfileContext(profile)

        // 2️⃣ Берём хвост истории
        val tail = history.tail(sessionId, historyTail).map {
            Message(role = it.role, content = it.content)
        }

        // 3️⃣ Формируем основной запрос к ассистенту
        val body = ChatRequest(
            model = model,
            messages = listOf(
                Message("system", SYSTEM_PROMPT),
                Message("system", profileContext),
                Message(
                    "system",
                    if (reminderCreated)
                        "Пользователь только что создал напоминание. Подтверди это простым и понятным языком."
                    else ""
                )
            ) + tail + Message("user", userText),
            stream = false,
            temperature = 0.3
        )

        // 4️⃣ Запрос к модели
        val resp = api.chat(body)
        val answer = resp.choices.firstOrNull()?.message?.content.orEmpty()

        // 5️⃣ Сохраняем диалог
        history.append(sessionId, "user", userText)
        history.append(sessionId, "assistant", answer)

        // 6️⃣ Обновляем профиль
        extractAndSaveProfile(userText)

        answer

    }.recoverCatching { e ->
        if (e is HttpException) throw RuntimeException(httpErrorToMessage(e))
        else throw e
    }


    // -------------------------
    // PROFILE CONTEXT
    // -------------------------

    private fun buildProfileContext(profile: UserProfileEntity?): String {
        if (profile == null) return ""

        return buildString {
            append("Факты о пользователе:\n")
            profile.name?.let { append("Имя: $it\n") }
            profile.age?.let { append("Возраст: $it\n") }
            profile.conditions?.let { append("Заболевания: $it\n") }
            profile.medications?.let { append("Лекарства: $it\n") }
            profile.notes?.let { append("Дополнительно: $it\n") }
        }
    }

    // -------------------------
    // PROFILE EXTRACTION
    // -------------------------

    private suspend fun extractAndSaveProfile(text: String) {
        val extractionPrompt = """
            Извлеки факты о пользователе из текста.
            Ответ строго в JSON:
            {
              "name": String | null,
              "age": Int | null,
              "conditions": String | null,
              "medications": String | null,
              "notes": String | null
            }
            Если данных нет — null.
            
            Текст:
            $text
        """.trimIndent()

        val body = ChatRequest(
            model = model,
            messages = listOf(Message("system", extractionPrompt)),
            temperature = 0.0,
            stream = false
        )

        runCatching {
            val resp = api.chat(body)
            val json = resp.choices.firstOrNull()?.message?.content.orEmpty()
            val extracted = profileAdapter.fromJson(json) ?: return

            val current = userProfileDao.get()

            val merged = UserProfileEntity(
                id = 1,
                name = extracted.name ?: current?.name,
                age = extracted.age ?: current?.age,
                conditions = extracted.conditions ?: current?.conditions,
                medications = extracted.medications ?: current?.medications,
                notes = extracted.notes ?: current?.notes
            )

            userProfileDao.save(merged)
        }
    }

    private suspend fun extractReminder(userText: String): ReminderEntity? {

        val systemPrompt = """
        Определи, является ли сообщение созданием напоминания.
        
        Если да — верни ТОЛЬКО JSON без пояснений:
        {
          "text": "...",
          "delayMillis": число миллисекунд от текущего момента,
          "isRepeating": false,
          "repeatIntervalMillis": null
        }
        
        Пример:
        "Напомни через 1 минуту выключить плиту"
        →
        {
          "text": "Выключить плиту",
          "delayMillis": 60000,
          "isRepeating": false,
          "repeatIntervalMillis": null
        }
        
        Если это НЕ напоминание — верни строго null.
    """.trimIndent()

        val body = ChatRequest(
            model = model,
            messages = listOf(
                Message("system", systemPrompt),
                Message("user", userText)
            ),
            temperature = 0.0,
            stream = false
        )

        return runCatching {
            val resp = api.chat(body)
            val content = resp.choices.firstOrNull()?.message?.content?.trim()
                ?: return null

            println("Reminder LLM raw: $content")

            if (content == "null") return null

            val parsed = reminderAdapter.fromJson(content) ?: return null

            if (parsed.text == null || parsed.delayMillis == null) return null

            val triggerAt = System.currentTimeMillis() + parsed.delayMillis

            ReminderEntity(
                text = parsed.text,
                triggerAt = triggerAt,
                isRepeating = parsed.isRepeating ?: false,
                repeatIntervalMillis = parsed.repeatIntervalMillis
            )
        }.getOrNull()
    }



    // -------------------------
    // ERROR HANDLING
    // -------------------------

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

// -------------------------
// DTO для извлечения профиля
// -------------------------

data class ProfileExtraction(
    val name: String?,
    val age: Int?,
    val conditions: String?,
    val medications: String?,
    val notes: String?
)

