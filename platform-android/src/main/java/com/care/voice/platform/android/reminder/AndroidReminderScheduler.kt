package com.care.voice.platform.android.reminder

import android.content.Context
import com.care.voice.brain.reminder.ScheduleReminderResult
import com.care.voice.brain.reminder.ReminderRequest
import com.care.voice.brain.reminder.ReminderScheduler
import com.care.voice.brain.reminder.ReminderLogEvent
import com.care.voice.brain.reminder.ReminderFailureCode
import com.care.voice.brain.reminder.ReminderStatus
import com.care.voice.data.history.ReminderEntity
import com.care.voice.data.reminder.AlarmReminderScheduler
import com.care.voice.data.reminder.ReminderAlarmScheduler
import com.care.voice.data.reminder.AlarmScheduleOutcome
import com.care.voice.data.repository.ReminderDao
import com.care.voice.brain.reminder.ReminderDeliveryCoordinator
import com.care.voice.brain.reminder.ReminderVoiceStateStore
import com.care.voice.brain.reminder.VoiceReminderPolicy
import com.care.voice.platform.android.reminder.voice.AndroidCallStateProvider
import com.care.voice.platform.android.speech.YasnaSpeechHolder
import com.care.voice.platform.android.reminder.voice.DefaultVoiceReminderSettings
import com.care.voice.platform.android.reminder.voice.SystemClockAdapter
import com.care.voice.platform.android.persistence.YasnaDatabase

class AndroidReminderScheduler(
    private val reminderDao: ReminderDao,
    private val alarmScheduler: ReminderAlarmScheduler,
    private val capabilityChecker: ReminderCapabilityChecker,
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) : ReminderScheduler {

    override suspend fun schedule(request: ReminderRequest): ScheduleReminderResult {
        val now = nowMillis()

        if (request.triggerAtMillis <= now) {
            return ScheduleReminderResult.InvalidTime("triggerAt in past")
        }

        if (!capabilityChecker.areNotificationsAllowed()) {
            ReminderLog.capability(
                notifications = false,
                exactAlarms = capabilityChecker.canScheduleExactAlarms()
            )
            return ScheduleReminderResult.NotificationPermissionRequired
        }

        val entity = ReminderEntity(
            text = request.text,
            triggerAt = request.triggerAtMillis,
            isRepeating = request.isRepeating,
            repeatIntervalMillis = request.repeatIntervalMillis,
            status = ReminderStatus.SCHEDULING,
            createdAt = now,
            updatedAt = now,
            precision = request.precision,
            deliveryMode = request.deliveryMode
        )

        val id = try {
            reminderDao.insert(entity)
        } catch (t: Throwable) {
            ReminderLog.roomError(0, "insert", t.message)
            return ScheduleReminderResult.Failure(t.message ?: "room insert failed")
        }

        ReminderLog.event(id, ReminderLogEvent.CREATED, null, ReminderStatus.SCHEDULING.name)
        ReminderLog.event(id, ReminderLogEvent.SCHEDULING, null, ReminderStatus.SCHEDULING.name)

        val saved = entity.copy(id = id)
        val alarmOutcome = alarmScheduler.schedule(id, request.triggerAtMillis, request.precision)

        return when (alarmOutcome) {
            AlarmScheduleOutcome.Success -> {
                val scheduledAt = nowMillis()
                ReminderStateUpdater.applyTransition(
                    dao = reminderDao,
                    id = id,
                    to = ReminderStatus.SCHEDULED,
                    now = scheduledAt
                ) {
                    it.copy(
                        scheduledAt = scheduledAt,
                        failureReason = null,
                        failureCode = null,
                        failureMessage = null,
                        failedAt = null
                    )
                }
                ReminderLog.scheduler(
                    reminderId = id,
                    triggerAt = request.triggerAtMillis,
                    precision = request.precision.name,
                    statusFrom = ReminderStatus.SCHEDULING.name,
                    statusTo = ReminderStatus.SCHEDULED.name,
                    scheduleMethod = request.precision.name,
                    exactPermission = capabilityChecker.canScheduleExactAlarms(),
                    notificationPermission = true
                )
                ScheduleReminderResult.Success(
                    reminderId = id,
                    scheduledAtEpochMillis = scheduledAt,
                    precision = request.precision
                )
            }
            AlarmScheduleOutcome.ExactPermissionRequired -> {
                ReminderStateUpdater.markFailed(
                    dao = reminderDao,
                    id = id,
                    code = ReminderFailureCode.EXACT_ALARM_PERMISSION_REQUIRED,
                    message = "exact_alarm_permission_required",
                    now = nowMillis()
                )
                alarmScheduler.cancel(id)
                ScheduleReminderResult.ExactAlarmPermissionRequired
            }
            is AlarmScheduleOutcome.Failure -> {
                ReminderStateUpdater.markFailed(
                    dao = reminderDao,
                    id = id,
                    code = ReminderFailureCode.ALARM_SCHEDULE_FAILED,
                    message = alarmOutcome.reason,
                    now = nowMillis()
                )
                alarmScheduler.cancel(id)
                ScheduleReminderResult.Failure(alarmOutcome.reason)
            }
        }
    }

    suspend fun cancel(reminderId: Long) {
        val now = nowMillis()
        alarmScheduler.cancel(reminderId)
        reminderDao.markCancelled(reminderId, now)
    }
}

object ReminderDependencies {
    data class Deps(
        val reminderDao: ReminderDao,
        val alarmScheduler: ReminderAlarmScheduler,
        val deliveryService: NotificationReminderDeliveryService,
        val capabilityChecker: ReminderCapabilityChecker,
        val rescheduler: ReminderRescheduler,
        val reconciler: ReminderReconciler,
        val deliveryCoordinator: ReminderDeliveryCoordinator,
        val voiceStateStore: ReminderVoiceStateStore
    )

    @Volatile
    private var cached: Deps? = null

    fun get(context: Context): Deps {
        val appContext = context.applicationContext
        return cached ?: synchronized(this) {
            cached ?: build(appContext).also { cached = it }
        }
    }

    private fun build(context: Context): Deps {
        val db = YasnaDatabase.get(context)
        val dao = db.reminders()
        val capabilityChecker = AndroidReminderCapabilityChecker(context)
        val alarmScheduler = AlarmReminderScheduler(context) { capabilityChecker.canScheduleExactAlarms() }
        val deliveryService = NotificationReminderDeliveryService(context, capabilityChecker)
        val rescheduler = ReminderRescheduler(dao, alarmScheduler, { System.currentTimeMillis() })
        val voiceStateStore = RoomReminderVoiceStateStore(dao)
        val deliveryPersistence = RoomReminderDeliveryPersistence(dao)
        val speechProvider = YasnaSpeechHolder.get(context).reminderCoordinator
        val policy = VoiceReminderPolicy(
            settings = DefaultVoiceReminderSettings(),
            clock = SystemClockAdapter(),
            callState = AndroidCallStateProvider(context)
        )
        val deliveryCoordinator = ReminderDeliveryCoordinator(
            notificationDelivery = deliveryService,
            speechProvider = speechProvider,
            voiceStateStore = voiceStateStore,
            persistence = deliveryPersistence,
            policy = policy,
            logger = ReminderLog
        )
        val reconciler = ReminderReconciler(
            reminderDao = dao,
            alarmScheduler = alarmScheduler,
            deliveryCoordinator = deliveryCoordinator,
            nowMillis = { System.currentTimeMillis() }
        )
        return Deps(
            dao,
            alarmScheduler,
            deliveryService,
            capabilityChecker,
            rescheduler,
            reconciler,
            deliveryCoordinator,
            voiceStateStore
        )
    }
}
