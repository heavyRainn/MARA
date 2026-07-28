package com.care.voice.brain.reminder

data class ReminderDeliverySnapshot(
    val id: Long,
    val text: String,
    val status: ReminderStatus,
    val voiceDeliveryStatus: VoiceDeliveryStatus
)

interface ReminderDeliveryPersistence {
    suspend fun getSnapshot(reminderId: Long): ReminderDeliverySnapshot?
    suspend fun markNotificationDelivered(reminderId: Long, nowEpochMillis: Long): Boolean
}

enum class DeliveryEntryPoint {
    ALARM_TRIGGER,
    RECONCILE_REDELIVERY
}

sealed interface CoordinatorDeliveryResult {
    data object NotificationAndVoiceComplete : CoordinatorDeliveryResult
    data object NotificationDeliveredVoiceSkipped : CoordinatorDeliveryResult
    data object NotificationDeliveredVoiceFailed : CoordinatorDeliveryResult
    data class NotificationFailed(val reason: String) : CoordinatorDeliveryResult
    data object SkippedIdempotent : CoordinatorDeliveryResult
    data class Rejected(val reason: String) : CoordinatorDeliveryResult
}

interface ReminderDeliveryLogger {
    fun event(reminderId: Long, event: ReminderLogEvent, detail: String? = null)
    fun notificationError(reminderId: Long, reason: String?)
    fun notificationSuccess(reminderId: Long)
}

object NoOpReminderDeliveryLogger : ReminderDeliveryLogger {
    override fun event(reminderId: Long, event: ReminderLogEvent, detail: String?) = Unit
    override fun notificationError(reminderId: Long, reason: String?) = Unit
    override fun notificationSuccess(reminderId: Long) = Unit
}

class ReminderDeliveryCoordinator(
    private val notificationDelivery: ReminderDeliveryService,
    private val speechProvider: ReminderSpeechProvider,
    private val voiceStateStore: ReminderVoiceStateStore,
    private val persistence: ReminderDeliveryPersistence,
    private val policy: VoiceReminderPolicy,
    private val formatter: ReminderSpeechTextFormatter = ReminderSpeechTextFormatter,
    private val logger: ReminderDeliveryLogger = NoOpReminderDeliveryLogger
) {
    /** After status is TRIGGERED — notification then voice. No alarm-idempotency check. */
    suspend fun deliverAfterTriggered(
        snapshot: ReminderDeliverySnapshot,
        nowEpochMillis: Long
    ): CoordinatorDeliveryResult {
        if (snapshot.status != ReminderStatus.TRIGGERED) {
            return CoordinatorDeliveryResult.Rejected("expected_triggered:${snapshot.status}")
        }
        return deliverNotificationThenVoice(snapshot, nowEpochMillis)
    }

    /** Reconcile recovery path for TRIGGERED without DELIVERED. Separate from alarm duplicate guard. */
    suspend fun redeliverFromReconcile(
        snapshot: ReminderDeliverySnapshot,
        nowEpochMillis: Long
    ): CoordinatorDeliveryResult {
        if (snapshot.status != ReminderStatus.TRIGGERED) {
            return CoordinatorDeliveryResult.SkippedIdempotent
        }
        return deliverNotificationThenVoice(snapshot, nowEpochMillis)
    }

    private suspend fun deliverNotificationThenVoice(
        snapshot: ReminderDeliverySnapshot,
        nowEpochMillis: Long
    ): CoordinatorDeliveryResult {
        val reminder = Reminder(
            id = snapshot.id,
            text = snapshot.text,
            triggerAtEpochMillis = 0L,
            status = snapshot.status,
            precision = ReminderPrecision.EXACT
        )

        return when (val notificationResult = notificationDelivery.deliver(reminder)) {
            ReminderDeliveryResult.Delivered -> {
                if (!persistence.markNotificationDelivered(snapshot.id, nowEpochMillis)) {
                    return CoordinatorDeliveryResult.Rejected("delivered_transition_failed")
                }
                logger.notificationSuccess(snapshot.id)
                performVoiceBestEffort(snapshot.id, snapshot.text, nowEpochMillis)
            }
            is ReminderDeliveryResult.Failed -> {
                logger.notificationError(snapshot.id, notificationResult.reason)
                CoordinatorDeliveryResult.NotificationFailed(notificationResult.reason)
            }
        }
    }

    internal suspend fun performVoiceBestEffort(
        reminderId: Long,
        rawText: String,
        nowEpochMillis: Long
    ): CoordinatorDeliveryResult {
        val voiceStatus = voiceStateStore.getStatus(reminderId)
        when (val decision = policy.evaluate(rawText, voiceStatus)) {
            is VoicePolicyDecision.Skip -> {
                voiceStateStore.markSkipped(reminderId, decision.reason, nowEpochMillis)
                logger.event(reminderId, ReminderLogEvent.VOICE_SKIPPED, decision.reason.name)
                return CoordinatorDeliveryResult.NotificationDeliveredVoiceSkipped
            }
            VoicePolicyDecision.Proceed -> Unit
        }

        if (!voiceStateStore.tryMarkPending(reminderId, nowEpochMillis)) {
            logger.event(reminderId, ReminderLogEvent.VOICE_SKIPPED, VoiceSkipReason.ALREADY_DELIVERED.name)
            return CoordinatorDeliveryResult.NotificationDeliveredVoiceSkipped
        }

        logger.event(reminderId, ReminderLogEvent.VOICE_REQUESTED)

        val speechText = formatter.format(rawText)
        return when (val speechResult = speechProvider.speak(reminderId, speechText)) {
            ReminderSpeechResult.Spoken -> {
                voiceStateStore.markSpoken(reminderId, nowEpochMillis)
                logger.event(reminderId, ReminderLogEvent.VOICE_SPOKEN)
                CoordinatorDeliveryResult.NotificationAndVoiceComplete
            }
            is ReminderSpeechResult.Skipped -> {
                voiceStateStore.markSkipped(reminderId, speechResult.reason, nowEpochMillis)
                logger.event(reminderId, ReminderLogEvent.VOICE_SKIPPED, speechResult.reason.name)
                CoordinatorDeliveryResult.NotificationDeliveredVoiceSkipped
            }
            is ReminderSpeechResult.Failed -> {
                voiceStateStore.markFailed(reminderId, speechResult.code, nowEpochMillis)
                logger.event(reminderId, ReminderLogEvent.VOICE_FAILED, speechResult.code)
                CoordinatorDeliveryResult.NotificationDeliveredVoiceFailed
            }
        }
    }
}
