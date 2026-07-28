package com.care.voice.brain.reminder

import com.care.voice.brain.AssistantError
import com.care.voice.brain.AssistantResult
import com.care.voice.brain.ReminderPendingCommand
import com.care.voice.brain.ReminderSetupKind
import java.util.Locale

/**
 * Reminder candidate validation, confirmation prompts and execution.
 */
class ReminderCoordinator(
    private val reminderTimeParser: ReminderTimeParser = ReminderTimeParser(),
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) {
    data class CandidateResult(
        val answer: String,
        val pending: ReminderPendingCommand.ScheduleReminder?,
        val requiresConfirmation: Boolean
    )

    fun buildCandidate(intent: ReminderIntent): CandidateResult {
        val cleanText = intent.text.trim()
        if (cleanText.length < 3) {
            return CandidateResult(
                answer = "Я понял, что нужно напомнить, но не понял, о чём именно. Скажите, например: напомни завтра в 9 выпить таблетку.",
                pending = null,
                requiresConfirmation = false
            )
        }

        val parsedTime = reminderTimeParser.parse(intent.timeExpression, intent.repeatExpression)
        if (parsedTime == null || parsedTime.triggerAt <= nowMillis()) {
            return CandidateResult(
                answer = "Я понял, что нужно напомнить: $cleanText. Но не понял время. Скажите, например: сегодня в 18:00 или завтра утром.",
                pending = null,
                requiresConfirmation = false
            )
        }

        val maxDelayMillis = 366L * 24 * 60 * 60 * 1000
        if (parsedTime.triggerAt - nowMillis() > maxDelayMillis) {
            return CandidateResult(
                answer = "Я понял напоминание, но дата получилась слишком далёкой. Назовите дату в пределах ближайшего года.",
                pending = null,
                requiresConfirmation = false
            )
        }

        val title = cleanText.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale("ru", "RU")) else it.toString()
        }
        val pending = ReminderPendingCommand.ScheduleReminder(
            title = title,
            triggerAtEpochMillis = parsedTime.triggerAt,
            isRepeating = parsedTime.isRepeating,
            repeatIntervalMillis = parsedTime.repeatIntervalMillis,
            humanReadableTime = parsedTime.humanReadableTime,
            precision = parsedTime.precision
        )
        val precisionHint = if (parsedTime.precision == ReminderPrecision.FLEXIBLE) {
            " (примерное время)"
        } else {
            ""
        }
        return CandidateResult(
            answer = "Поставить напоминание: «$title» на ${parsedTime.humanReadableTime}$precisionHint? Скажите: да или нет.",
            pending = pending,
            requiresConfirmation = true
        )
    }

    suspend fun executeConfirmed(
        schedule: ReminderPendingCommand.ScheduleReminder,
        scheduler: ReminderScheduler
    ): AssistantResult {
        val request = ReminderRequest(
            text = schedule.title,
            triggerAtMillis = schedule.triggerAtEpochMillis,
            precision = schedule.precision,
            isRepeating = schedule.isRepeating,
            repeatIntervalMillis = schedule.repeatIntervalMillis
        )

        return when (val result = scheduler.schedule(request)) {
            is ScheduleReminderResult.Success -> AssistantResult.ActionCompleted(
                buildSuccessMessage(schedule, result.precision)
            )
            ScheduleReminderResult.NotificationPermissionRequired -> AssistantResult.ReminderSetupRequired(
                kind = ReminderSetupKind.NOTIFICATION_PERMISSION,
                userMessage = "Чтобы я могла показывать напоминания, разрешите уведомления.",
                pendingActionId = ""
            )
            ScheduleReminderResult.ExactAlarmPermissionRequired -> AssistantResult.ReminderSetupRequired(
                kind = ReminderSetupKind.EXACT_ALARM_PERMISSION,
                userMessage = "Для точного напоминания нужно разрешение на будильники и напоминания.",
                pendingActionId = ""
            )
            is ScheduleReminderResult.InvalidTime -> AssistantResult.Failure(
                error = AssistantError.InvalidReminder,
                userMessage = "Не удалось определить корректное время напоминания."
            )
            is ScheduleReminderResult.Failure -> AssistantResult.Failure(
                error = AssistantError.ActionExecutionFailed,
                userMessage = "Не удалось поставить напоминание. Я ничего не запланировала."
            )
        }
    }

    fun buildSuccessMessage(
        schedule: ReminderPendingCommand.ScheduleReminder,
        precision: ReminderPrecision
    ): String {
        val precisionSuffix = if (precision == ReminderPrecision.FLEXIBLE) {
            " (примерное время)"
        } else {
            ""
        }
        return "Готово. Напомню: «${schedule.title}» — ${schedule.humanReadableTime}$precisionSuffix."
    }

    fun buildCancelled(schedule: ReminderPendingCommand.ScheduleReminder?): String {
        val title = schedule?.title.orEmpty()
        return "Хорошо, не буду ставить напоминание: «$title»."
    }
}
