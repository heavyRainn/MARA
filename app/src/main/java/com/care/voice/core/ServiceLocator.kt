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
import com.care.voice.data.net.VisionApi
import com.care.voice.data.reminder.AlarmReminderScheduler
import com.care.voice.platform.android.llm.GroqLanguageModel
import com.care.voice.platform.android.vision.GroqVisionProvider
import com.care.voice.platform.android.vision.ImagePreprocessor
import com.care.voice.platform.android.vision.VisionContextLoader
import com.care.voice.platform.android.persistence.RoomConversationRepository
import com.care.voice.platform.android.persistence.RoomMemoryRepository
import com.care.voice.platform.android.persistence.RoomMemoryStore
import com.care.voice.platform.android.persistence.RoomPendingActionRepository
import com.care.voice.platform.android.persistence.RoomSummaryRepository
import com.care.voice.platform.android.persistence.YasnaDatabase
import com.care.voice.platform.android.reminder.AndroidReminderCapabilityChecker
import com.care.voice.platform.android.reminder.AndroidReminderScheduler
import com.care.voice.platform.android.reminder.ReminderDependencies
import com.care.voice.platform.android.memory.MemoryConsolidationScheduler
import com.care.voice.platform.android.session.RoomSessionManager
import com.care.voice.platform.android.speech.YasnaSpeechHolder
import com.care.voice.platform.tts.TtsManager
import com.care.voice.platform.voice.RecognitionManager

object ServiceLocator {
    lateinit var app: Application
        private set

    private val appContext get() = app.applicationContext

    const val TEXT_MODEL = "llama-3.1-8b-instant"

    private const val MODEL = TEXT_MODEL

    val isGroqConfigured: Boolean
        get() = BuildConfig.GROQ_API_KEY.isNotBlank()

    fun init(application: Application) {
        app = application
        require(isGroqConfigured) {
            "Groq API key is missing. Add groq.api.key=... to local.properties"
        }
    }

    val recognition by lazy { RecognitionManager(appContext) }

    val speech by lazy { YasnaSpeechHolder.get(appContext) }

    val tts by lazy { TtsManager(speech.assistantCoordinator) }

    val reminderCapabilityChecker by lazy { AndroidReminderCapabilityChecker(appContext) }

    private val visionDatabase by lazy { YasnaDatabase.get(appContext) }

    val imagePreprocessor by lazy { ImagePreprocessor(appContext) }

    val visionProvider by lazy {
        GroqVisionProvider(
            api = VisionApi.groq(BuildConfig.GROQ_API_KEY),
            model = GroqVisionProvider.GROQ_VISION_MODEL,
        )
    }

    val visionContextLoader by lazy {
        VisionContextLoader(
            sessionManager = RoomSessionManager(visionDatabase.conversationSessions()),
            conversationRepository = RoomConversationRepository(
                ChatHistoryRepository(visionDatabase.messages()),
            ),
        )
    }

    val assistantOrchestrator by lazy {
        val db = YasnaDatabase.get(appContext)
        val historyRepo = ChatHistoryRepository(db.messages())
        val userProfileDao = db.userProfile()
        val summaryDao = db.chatSummaryDao()
        val reminderDao = db.reminders()
        val alarmScheduler = AlarmReminderScheduler(appContext) {
            reminderCapabilityChecker.canScheduleExactAlarms()
        }

        ReminderDependencies.get(appContext)

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
        val reminderScheduler = AndroidReminderScheduler(
            reminderDao = reminderDao,
            alarmScheduler = alarmScheduler,
            capabilityChecker = reminderCapabilityChecker
        )

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
        ReminderDependencies.get(appContext)
        YasnaSpeechHolder.preloadVoiceModel(appContext)
    }

    suspend fun reconcileReminders() {
        ReminderDependencies.get(appContext).reconciler.reconcile()
    }

    fun rescheduleRemindersAfterPermissionGrant() {
        kotlinx.coroutines.runBlocking {
            ReminderDependencies.get(appContext).reconciler.reconcile()
        }
    }
}
