package com.care.voice.brain.reminder

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class ReminderTimeParser(
    private val locale: Locale = Locale("ru", "RU"),
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    data class Parsed(
        val triggerAt: Long,
        val isRepeating: Boolean,
        val repeatIntervalMillis: Long?,
        val humanReadableTime: String
    )

    fun parse(timeExpression: String?, repeatExpression: String?): Parsed? {
        val time = timeExpression.normalizeRu()
        val repeat = repeatExpression.normalizeRu()
        if (time.isBlank() && repeat.isBlank()) return null

        val repeatInterval = parseRepeatInterval(repeat)
        val triggerAt = parseTriggerAt(time, repeat) ?: return null
        return Parsed(
            triggerAt = triggerAt,
            isRepeating = repeatInterval != null,
            repeatIntervalMillis = repeatInterval,
            humanReadableTime = formatHuman(triggerAt, repeatInterval)
        )
    }

    private fun parseTriggerAt(time: String, repeat: String): Long? {
        val now = nowProvider()
        parseRelativeDelay(time)?.let { return now + it }
        parseCalendarDate(time)?.let { return it }

        val cal = Calendar.getInstance(locale).apply { timeInMillis = now }
        val dayShift = when {
            time.contains("послезавтра") -> 2
            time.contains("завтра") -> 1
            time.contains("сегодня") -> 0
            else -> null
        }
        val weekShift = parseDayOfWeekShift(time, cal)

        if (dayShift != null) cal.add(Calendar.DAY_OF_YEAR, dayShift)
        if (weekShift != null) cal.add(Calendar.DAY_OF_YEAR, weekShift)

        val hm = parseExplicitTime(time)
            ?: parseApproximatePartOfDay(time)
            ?: when {
                repeat.contains("кажд") -> 9 to 0
                dayShift != null || weekShift != null -> 9 to 0
                else -> null
            }
            ?: return null

        cal.set(Calendar.HOUR_OF_DAY, hm.first)
        cal.set(Calendar.MINUTE, hm.second)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    private fun parseRelativeDelay(text: String): Long? {
        if (text.contains("полтора") && text.contains("час")) {
            return TimeUnit.MINUTES.toMillis(90)
        }
        if (text.contains("через неделю") || text.contains("через 1 недел")) {
            return TimeUnit.DAYS.toMillis(7)
        }

        Regex("через\\s+(\\d+[,.]\\d+)\\s*([а-яе]+)").find(text)?.let { match ->
            val amount = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return@let
            val unit = match.groupValues[2]
            val millis = when {
                unit.startsWith("мин") -> (amount * TimeUnit.MINUTES.toMillis(1)).toLong()
                unit.startsWith("час") -> (amount * TimeUnit.HOURS.toMillis(1)).toLong()
                unit.startsWith("дн") || unit.startsWith("ден") -> TimeUnit.DAYS.toMillis(amount.toLong())
                unit.startsWith("нед") -> TimeUnit.DAYS.toMillis((amount * 7).toLong())
                else -> null
            }
            if (millis != null) return millis
        }

        val match = Regex("через\\s+(\\d+)\\s*([а-яе]+)").find(text) ?: return null
        val amount = match.groupValues[1].toLongOrNull() ?: return null
        val unit = match.groupValues[2]
        return when {
            unit.startsWith("сек") -> TimeUnit.SECONDS.toMillis(amount)
            unit.startsWith("мин") -> TimeUnit.MINUTES.toMillis(amount)
            unit.startsWith("час") -> TimeUnit.HOURS.toMillis(amount)
            unit.startsWith("дн") || unit.startsWith("ден") -> TimeUnit.DAYS.toMillis(amount)
            unit.startsWith("нед") -> TimeUnit.DAYS.toMillis(amount * 7)
            else -> null
        }
    }

    private fun parseCalendarDate(text: String): Long? {
        val monthMatch = Regex("(\\d{1,2})\\s+(январ|феврал|март|апрел|ма[йя]|июн|июл|август|сентябр|октябр|ноябр|декабр)\\w*").find(text)
            ?: return null
        val day = monthMatch.groupValues[1].toIntOrNull() ?: return null
        val month = monthToCalendar(monthMatch.groupValues[2]) ?: return null

        val now = nowProvider()
        val cal = Calendar.getInstance(locale).apply { timeInMillis = now }
        cal.set(Calendar.MONTH, month)
        cal.set(Calendar.DAY_OF_MONTH, day)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val hm = parseExplicitTime(text) ?: parseApproximatePartOfDay(text) ?: (14 to 0)
        cal.set(Calendar.HOUR_OF_DAY, hm.first)
        cal.set(Calendar.MINUTE, hm.second)

        if (cal.timeInMillis <= now) {
            cal.add(Calendar.YEAR, 1)
        }
        return cal.timeInMillis
    }

    private fun monthToCalendar(prefix: String): Int? = when {
        prefix.startsWith("январ") -> Calendar.JANUARY
        prefix.startsWith("феврал") -> Calendar.FEBRUARY
        prefix.startsWith("март") -> Calendar.MARCH
        prefix.startsWith("апрел") -> Calendar.APRIL
        prefix.startsWith("ма") -> Calendar.MAY
        prefix.startsWith("июн") -> Calendar.JUNE
        prefix.startsWith("июл") -> Calendar.JULY
        prefix.startsWith("август") -> Calendar.AUGUST
        prefix.startsWith("сентябр") -> Calendar.SEPTEMBER
        prefix.startsWith("октябр") -> Calendar.OCTOBER
        prefix.startsWith("ноябр") -> Calendar.NOVEMBER
        prefix.startsWith("декабр") -> Calendar.DECEMBER
        else -> null
    }

    private fun parseRepeatInterval(text: String): Long? = when {
        text.isBlank() -> null
        text.contains("каждый день") || text.contains("ежеднев") || text.contains("каждое утро") || text.contains("каждый вечер") -> TimeUnit.DAYS.toMillis(1)
        text.contains("каждую неделю") || text.contains("еженед") -> TimeUnit.DAYS.toMillis(7)
        text.contains("каждый месяц") || text.contains("ежемесяч") -> TimeUnit.DAYS.toMillis(30)
        text.contains("каждый час") || text.contains("ежечас") -> TimeUnit.HOURS.toMillis(1)
        else -> null
    }

    private fun parseExplicitTime(text: String): Pair<Int, Int>? {
        Regex("(?:в\\s*)?(\\d{1,2})[:.](\\d{2})").find(text)?.let { m ->
            val h = m.groupValues[1].toIntOrNull() ?: return null
            val min = m.groupValues[2].toIntOrNull() ?: return null
            return normalizeHourMinute(h, min, text)
        }
        Regex("(?:в|на)?\\s*(\\d{1,2})\\s*(?:час(?:а|ов)?)?\\s*(утра|дня|вечера|ночи)?").find(text)?.let { m ->
            val h = m.groupValues[1].toIntOrNull() ?: return null
            val part = m.groupValues.getOrNull(2).orEmpty()
            return normalizeHourMinute(h, 0, part.ifBlank { text })
        }
        return null
    }

    private fun normalizeHourMinute(rawHour: Int, minute: Int, context: String): Pair<Int, Int>? {
        if (rawHour !in 0..23 || minute !in 0..59) return null
        var hour = rawHour
        if (hour in 1..11 && (context.contains("вечера") || context.contains("дня"))) hour += 12
        if (hour == 12 && context.contains("ночи")) hour = 0
        return hour to minute
    }

    private fun parseApproximatePartOfDay(text: String): Pair<Int, Int>? = when {
        text.contains("утром") || text.contains("утра") -> 9 to 0
        text.contains("днем") || text.contains("дня") || text.contains("после обеда") -> 13 to 0
        text.contains("вечером") || text.contains("вечера") -> 18 to 0
        text.contains("ночью") || text.contains("ночи") -> 21 to 0
        else -> null
    }

    private fun parseDayOfWeekShift(text: String, now: Calendar): Int? {
        val target = when {
            text.contains("понедельник") -> Calendar.MONDAY
            text.contains("вторник") -> Calendar.TUESDAY
            text.contains("сред") -> Calendar.WEDNESDAY
            text.contains("четверг") -> Calendar.THURSDAY
            text.contains("пятниц") -> Calendar.FRIDAY
            text.contains("суббот") -> Calendar.SATURDAY
            text.contains("воскрес") -> Calendar.SUNDAY
            else -> null
        } ?: return null
        var diff = target - now.get(Calendar.DAY_OF_WEEK)
        if (diff <= 0) diff += 7
        return diff
    }

    private fun formatHuman(triggerAt: Long, repeatIntervalMillis: Long?): String {
        val base = SimpleDateFormat("d MMMM в HH:mm", locale).format(Date(triggerAt))
        return when (repeatIntervalMillis) {
            TimeUnit.DAYS.toMillis(1) -> "$base, каждый день"
            TimeUnit.DAYS.toMillis(7) -> "$base, каждую неделю"
            TimeUnit.HOURS.toMillis(1) -> "$base, каждый час"
            TimeUnit.DAYS.toMillis(30) -> "$base, каждый месяц"
            else -> base
        }
    }

    private fun String?.normalizeRu(): String = this?.trim()?.lowercase(Locale("ru", "RU"))?.replace('ё', 'е').orEmpty()
}
