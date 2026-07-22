package com.care.voice.brain.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class ReminderTimeParserTest {

    private val locale = Locale("ru", "RU")

    @Test
    fun throughTenMinutes() {
        val now = fixedNow(2026, Calendar.JULY, 19, 10, 0)
        val parser = ReminderTimeParser(locale) { now }
        val parsed = parser.parse("через 10 минут", null)
        requireNotNull(parsed)
        assertEquals(now + TimeUnit.MINUTES.toMillis(10), parsed.triggerAt)
    }

    @Test
    fun throughOneAndHalfHours() {
        val now = fixedNow(2026, Calendar.JULY, 19, 10, 0)
        val parser = ReminderTimeParser(locale) { now }
        val parsed = parser.parse("через полтора часа", null)
        requireNotNull(parsed)
        assertEquals(now + TimeUnit.MINUTES.toMillis(90), parsed.triggerAt)
    }

    @Test
    fun tomorrowAtNine() {
        val now = fixedNow(2026, Calendar.JULY, 19, 10, 0)
        val parser = ReminderTimeParser(locale) { now }
        val parsed = parser.parse("завтра в 9", null)
        requireNotNull(parsed)
        val cal = Calendar.getInstance(locale).apply { timeInMillis = parsed.triggerAt }
        assertEquals(20, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, cal.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun todayAt1830RollsToNextDayIfPast() {
        val now = fixedNow(2026, Calendar.JULY, 19, 20, 0)
        val parser = ReminderTimeParser(locale) { now }
        val parsed = parser.parse("сегодня в 18:30", null)
        requireNotNull(parsed)
        assert(parsed.triggerAt > now)
    }

    @Test
    fun augustFirstAt1400() {
        val now = fixedNow(2026, Calendar.JULY, 19, 10, 0)
        val parser = ReminderTimeParser(locale) { now }
        val parsed = parser.parse("1 августа в 14:00", null)
        requireNotNull(parsed)
        val cal = Calendar.getInstance(locale).apply { timeInMillis = parsed.triggerAt }
        assertEquals(Calendar.AUGUST, cal.get(Calendar.MONTH))
        assertEquals(1, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(14, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
    }

    @Test
    fun throughWeek() {
        val now = fixedNow(2026, Calendar.JULY, 19, 10, 0)
        val parser = ReminderTimeParser(locale) { now }
        val parsed = parser.parse("через неделю", null)
        requireNotNull(parsed)
        assertEquals(now + TimeUnit.DAYS.toMillis(7), parsed.triggerAt)
    }

    @Test
    fun blankTimeReturnsNull() {
        val parser = ReminderTimeParser(locale) { System.currentTimeMillis() }
        assertNull(parser.parse(null, null))
    }

    private fun fixedNow(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return Calendar.getInstance(locale).apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
