package com.care.voice.core

import android.app.Application
import com.care.voice.data.net.LlmApi
import com.care.voice.data.repository.AssistantRepositoryImpl
import com.care.voice.domain.repository.AssistantRepository
import com.care.voice.platform.voice.RecognitionManager
import com.care.voice.platform.tts.TtsManager
import java.util.Locale

object ServiceLocator {
    lateinit var app: Application

    // ⚠️ для dev. Подставь свой gsk-ключ Groq (формат обычно gsk_xxx)
    private const val GROQ_KEY = "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"

    val recognition by lazy { RecognitionManager(app) }
    val tts by lazy { TtsManager(app, Locale("ru","RU")) }

    // Используем Groq вместо DeepSeek
    val llmApi by lazy { LlmApi.groq(GROQ_KEY) }
    val assistantRepo: AssistantRepository by lazy { AssistantRepositoryImpl(llmApi) }
}
