package com.care.voice.core

import android.app.Application
import com.care.voice.BuildConfig
import com.care.voice.brain.AssistantOrchestrator
import com.care.voice.brain.memory.extract.GroqMemoryExtractor
import com.care.voice.brain.memory.pipeline.MemoryPipeline
import com.care.voice.brain.reminder.ReminderIntentResolver
import com.care.voice.brain.summary.ConversationSummarizer
import com.care.voice.data.history.ChatHistoryRepository
import com.care.voice.data.net.LlmApi
import com.care.voice.data.reminder.ReminderScheduler
import com.care.voice.platform.android.llm.GroqLanguageModel
import com.care.voice.platform.android.persistence.RoomConversationRepository
import com.care.voice.platform.android.persistence.RoomMemoryRepository
import com.care.voice.platform.android.persistence.RoomMemoryStore
import com.care.voice.platform.android.persistence.RoomPendingActionRepository
import com.care.voice.platform.android.persistence.RoomSummaryRepository
import com.care.voice.platform.android.persistence.YasnaDatabase
import com.care.voice.platform.android.reminder.AndroidReminderScheduler
import com.care.voice.platform.android.reminder.ReminderRuntime
import com.care.voice.platform.android.memory.MemoryConsolidationScheduler
import com.care.voice.platform.android.session.RoomSessionManager
import com.care.voice.platform.tts.TtsManager
import com.care.voice.platform.voice.RecognitionManager
import java.util.Locale

object ServiceLocator {
    lateinit var app: Application
        private set

    private val appContext get() = app.applicationContext

    private const val MODEL = "llama-3.1-8b-instant"

    val isGroqConfigured: Boolean
        get() = BuildConfig.GROQ_API_KEY.isNotBlank()

    fun init(application: Application) {
        app = application
        require(isGroqConfigured) {
            "Groq API key is missing. Add groq.api.key=... to local.properties"
        }
    }

    val recognition by lazy { RecognitionManager(appContext) }
    val tts by lazy { TtsManager(appContext, Locale.forLanguageTag("ru-RU")) }

    val assistantOrchestrator by lazy {
        val db = YasnaDatabase.create(appContext)
        val historyRepo = ChatHistoryRepository(db.messages())
        val userProfileDao = db.userProfile()
        val summaryDao = db.chatSummaryDao()
        val reminderDao = db.reminders()
        val alarmScheduler = ReminderScheduler(appContext)

        ReminderRuntime.initialize(reminderDao, alarmScheduler)

        val languageModel = GroqLanguageModel(
            api = LlmApi.groq(BuildConfig.GROQ_API_KEY),
            model = MODEL
        )
        val memoryStore = RoomMemoryStore(historyRepo, userProfileDao, summaryDao, historyTail = 8)
        val conversationRepository = RoomConversationRepository(historyRepo)
        val memoryRepository = RoomMemoryRepository(db.memoryFacts())
        val summaryRepository = RoomSummaryRepository(summaryDao)
        val pendingActionRepository = RoomPendingActionRepository(db.pendingActions())
        val sessionManager = RoomSessionManager(db.conversationSessions())
        val reminderScheduler = AndroidReminderScheduler(reminderDao, alarmScheduler)

        val memoryPipeline = MemoryPipeline(
            extractor = GroqMemoryExtractor(languageModel),
            memoryRepository = memoryRepository,
            conversationRepository = conversationRepository,
            summaryRepository = summaryRepository,
            memoryStore = memoryStore,
            pendingActionRepository = pendingActionRepository
        )

        AssistantOrchestrator(
            languageModel = languageModel,
            memoryStore = memoryStore,
            conversationRepository = conversationRepository,
            reminderScheduler = reminderScheduler,
            sessionManager = sessionManager,
            pendingActionRepository = pendingActionRepository,
            memoryPipeline = memoryPipeline,
            reminderIntentResolver = ReminderIntentResolver(languageModel),
            conversationSummarizer = ConversationSummarizer(languageModel, memoryStore),
            historyTail = 8,
            onMemoryChanged = { MemoryConsolidationScheduler.scheduleDeferred(appContext) }
        )
    }

    fun wirePlatformRuntime() {
        assistantOrchestrator
    }
}
