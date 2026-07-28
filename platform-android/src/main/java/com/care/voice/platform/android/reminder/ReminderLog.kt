package com.care.voice.platform.android.reminder

import android.util.Log
import com.care.voice.brain.reminder.ReminderLogEvent

object ReminderLog : com.care.voice.brain.reminder.ReminderDeliveryLogger {
    private const val DEBUG = true

    fun event(
        reminderId: Long,
        event: ReminderLogEvent,
        statusFrom: String? = null,
        statusTo: String? = null,
        detail: String? = null
    ) {
        Log.i(
            TAG,
            "reminderId=$reminderId event=${event.name} statusFrom=$statusFrom statusTo=$statusTo detail=$detail"
        )
    }

    override fun event(reminderId: Long, event: ReminderLogEvent, detail: String?) {
        this.event(reminderId, event, statusFrom = null, statusTo = null, detail = detail)
    }

    override fun notificationError(reminderId: Long, reason: String?) {
        Log.e(TAG_DELIVERY, "reminderId=$reminderId notificationError failureReason=$reason")
    }

    override fun notificationSuccess(reminderId: Long) {
        delivery(reminderId, success = true)
    }

    fun scheduler(
        reminderId: Long,
        triggerAt: Long,
        precision: String,
        statusFrom: String?,
        statusTo: String,
        scheduleMethod: String,
        exactPermission: Boolean,
        notificationPermission: Boolean,
        failureReason: String? = null
    ) {
        Log.i(
            TAG_SCHEDULER,
            "reminderId=$reminderId triggerAt=$triggerAt precision=$precision " +
                "statusFrom=$statusFrom statusTo=$statusTo scheduleMethod=$scheduleMethod " +
                "exactPermission=$exactPermission notificationPermission=$notificationPermission " +
                "failureReason=$failureReason"
        )
        val event = runCatching { ReminderLogEvent.valueOf(statusTo) }.getOrNull()
        if (event != null) {
            event(reminderId, event, statusFrom, statusTo, failureReason)
        }
    }

    fun receiver(
        reminderId: Long,
        receiverAction: String,
        statusFrom: String?,
        statusTo: String?,
        failureReason: String? = null
    ) {
        Log.i(
            TAG_RECEIVER,
            "reminderId=$reminderId receiverAction=$receiverAction statusFrom=$statusFrom " +
                "statusTo=$statusTo failureReason=$failureReason"
        )
    }

    fun delivery(reminderId: Long, success: Boolean, failureReason: String? = null) {
        val event = if (success) ReminderLogEvent.DELIVERED else ReminderLogEvent.FAILED
        Log.i(TAG_DELIVERY, "reminderId=$reminderId success=$success failureReason=$failureReason")
        event(reminderId, event, null, if (success) ReminderLogEvent.DELIVERED.name else null, failureReason)
    }

    fun roomError(reminderId: Long, operation: String, failureReason: String?) {
        Log.e(TAG_ROOM, "reminderId=$reminderId operation=$operation failureReason=$failureReason")
    }

    fun rescheduler(count: Int, skippedPast: Int) {
        Log.i(TAG_RESCHEDULER, "rescheduled=$count skippedPast=$skippedPast")
    }

    fun reconciler(report: String) {
        Log.i(TAG_RECONCILER, report)
    }

    fun capability(notifications: Boolean, exactAlarms: Boolean) {
        Log.i(TAG_CAPABILITY, "notificationPermission=$notifications exactPermission=$exactAlarms")
    }

    fun debugText(reminderId: Long, textLength: Int, textHash: Int) {
        if (DEBUG) {
            Log.d(TAG_DELIVERY, "reminderId=$reminderId textLength=$textLength textHash=$textHash")
        }
    }

    private const val TAG = "ReminderLifecycle"
    private const val TAG_SCHEDULER = "ReminderScheduler"
    private const val TAG_RECEIVER = "ReminderReceiver"
    private const val TAG_DELIVERY = "ReminderDelivery"
    private const val TAG_ROOM = "ReminderRoom"
    private const val TAG_RESCHEDULER = "ReminderRescheduler"
    private const val TAG_RECONCILER = "ReminderReconciler"
    private const val TAG_CAPABILITY = "ReminderCapability"
}
