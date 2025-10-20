// ServiceLocator.kt
package com.care.voice.core

import android.app.Application
import androidx.room.Room
import com.care.voice.data.history.AppDb
import com.care.voice.data.history.ChatHistoryRepository
import com.care.voice.data.net.LlmApi
import com.care.voice.data.repository.AssistantRepositoryImpl
import com.care.voice.domain.repository.AssistantRepository
import com.care.voice.platform.tts.TtsManager
import com.care.voice.platform.voice.RecognitionManager
import java.util.Locale
import java.util.UUID

object ServiceLocator {
    lateinit var app: Application

    private const val GROQ_KEY = "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"

    @Volatile var currentSessionId: String = "default"
    fun startNewSession(): String {
        currentSessionId = UUID.randomUUID().toString()
        return currentSessionId
    }

    // Speech
    val recognition by lazy { RecognitionManager(app) }
    val tts by lazy { TtsManager(app, Locale("ru", "RU")) }

    // LLM (Groq)
    private const val MODEL = "llama-3.1-8b-instant"
    val llmApi by lazy { LlmApi.groq(GROQ_KEY) }

    // Room DB + история
    private val db by lazy {
        Room.databaseBuilder(app, AppDb::class.java, "yasna.db")
            .fallbackToDestructiveMigration()
            .build()
    }
    val historyRepo by lazy { ChatHistoryRepository(db.messages()) }

    val assistantRepo: AssistantRepository by lazy {
        AssistantRepositoryImpl(
            api = llmApi,
            model = MODEL,
            history = historyRepo,
            sessionIdProvider = { currentSessionId },
            historyTail = 8
        )
    }
}
