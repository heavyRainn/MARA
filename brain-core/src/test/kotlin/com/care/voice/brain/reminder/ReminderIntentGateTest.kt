package com.care.voice.brain.reminder

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderIntentGateTest {

    @Test
    fun rejectsRecallQuestions() {
        assertEquals(
            ReminderIntentGate.Decision.REJECT,
            ReminderIntentGate.evaluate("Напомни, как назывался фильм")
        )
        assertEquals(
            ReminderIntentGate.Decision.REJECT,
            ReminderIntentGate.evaluate("Напомни, что мы обсуждали вчера")
        )
    }

    @Test
    fun rejectsMetaInstructions() {
        assertEquals(
            ReminderIntentGate.Decision.REJECT,
            ReminderIntentGate.evaluate("Не создавай напоминание")
        )
        assertEquals(
            ReminderIntentGate.Decision.REJECT,
            ReminderIntentGate.evaluate("Расскажи, как поставить напоминание")
        )
    }

    @Test
    fun allowsCreationRequests() {
        assertEquals(
            ReminderIntentGate.Decision.ALLOW_LLM,
            ReminderIntentGate.evaluate("Напомни завтра в 9 позвонить маме")
        )
        assertEquals(
            ReminderIntentGate.Decision.ALLOW_LLM,
            ReminderIntentGate.evaluate("Поставь напоминание через час")
        )
        assertEquals(
            ReminderIntentGate.Decision.ALLOW_LLM,
            ReminderIntentGate.evaluate("Через 20 минут выключить духовку")
        )
    }
}
