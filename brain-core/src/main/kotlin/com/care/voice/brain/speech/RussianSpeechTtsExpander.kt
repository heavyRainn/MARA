package com.care.voice.brain.speech

/**
 * Expands digits, dates and clock times into TTS-friendly Russian phrases.
 */
object RussianSpeechTtsExpander {

    private val clockRegex = Regex("""(?<!\d)(\d{1,2})\s*:\s*(\d{2})(?!\d)""")
    private val dateRegex = Regex(
        """(?<!\d)(\d{1,2})\s+(января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря)(?!\w)""",
        RegexOption.IGNORE_CASE,
    )

    fun expand(input: String): String {
        if (input.isBlank()) return input
        var text = input
        text = clockRegex.replace(text) { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return@replace match.value
            val minute = match.groupValues[2].toIntOrNull() ?: return@replace match.value
            expandClock(hour, minute)
        }
        text = dateRegex.replace(text) { match ->
            val day = match.groupValues[1].toIntOrNull() ?: return@replace match.value
            val month = match.groupValues[2].lowercase()
            "${dayOrdinalGenitive(day)} $month"
        }
        return text
    }

    internal fun expandClock(hour: Int, minute: Int): String {
        if (hour !in 0..23 || minute !in 0..59) {
            return "$hour:${minute.toString().padStart(2, '0')}"
        }
        val hourPart = hourToWords(hour)
        if (minute == 0) return "$hourPart ровно"
        val minutePart = minuteToWords(minute)
        return if (minute in 1..9) {
            "$hourPart ноль $minutePart"
        } else {
            "$hourPart $minutePart"
        }
    }

    private fun hourToWords(hour: Int): String {
        val word = numberToWords(hour)
        val form = pluralForm(
            value = hour,
            one = "час",
            few = "часа",
            many = "часов",
        )
        return "$word $form"
    }

    private fun minuteToWords(minute: Int): String {
        val word = numberToWordsFeminine(minute)
        val form = pluralForm(
            value = minute,
            one = "минута",
            few = "минуты",
            many = "минут",
        )
        return "$word $form"
    }

    private fun pluralForm(value: Int, one: String, few: String, many: String): String {
        val mod100 = value % 100
        val mod10 = value % 10
        return when {
            mod100 in 11..14 -> many
            mod10 == 1 -> one
            mod10 in 2..4 -> few
            else -> many
        }
    }

    private fun dayOrdinalGenitive(day: Int): String = when (day) {
        1 -> "первого"
        2 -> "второго"
        3 -> "третьего"
        4 -> "четвёртого"
        5 -> "пятого"
        6 -> "шестого"
        7 -> "седьмого"
        8 -> "восьмого"
        9 -> "девятого"
        10 -> "десятого"
        11 -> "одиннадцатого"
        12 -> "двенадцатого"
        13 -> "тринадцатого"
        14 -> "четырнадцатого"
        15 -> "пятнадцатого"
        16 -> "шестнадцатого"
        17 -> "семнадцатого"
        18 -> "восемнадцатого"
        19 -> "девятнадцатого"
        20 -> "двадцатого"
        21 -> "двадцать первого"
        22 -> "двадцать второго"
        23 -> "двадцать третьего"
        24 -> "двадцать четвёртого"
        25 -> "двадцать пятого"
        26 -> "двадцать шестого"
        27 -> "двадцать седьмого"
        28 -> "двадцать восьмого"
        29 -> "двадцать девятого"
        30 -> "тридцатого"
        31 -> "тридцать первого"
        else -> "$day-го"
    }

    private fun numberToWords(value: Int): String = when (value) {
        0 -> "ноль"
        1 -> "один"
        2 -> "два"
        3 -> "три"
        4 -> "четыре"
        5 -> "пять"
        6 -> "шесть"
        7 -> "семь"
        8 -> "восемь"
        9 -> "девять"
        10 -> "десять"
        11 -> "одиннадцать"
        12 -> "двенадцать"
        13 -> "тринадцать"
        14 -> "четырнадцать"
        15 -> "пятнадцать"
        16 -> "шестнадцать"
        17 -> "семнадцать"
        18 -> "восемнадцать"
        19 -> "девятнадцать"
        20 -> "двадцать"
        21 -> "двадцать один"
        22 -> "двадцать два"
        23 -> "двадцать три"
        24 -> "двадцать четыре"
        25 -> "двадцать пять"
        26 -> "двадцать шесть"
        27 -> "двадцать семь"
        28 -> "двадцать восемь"
        29 -> "двадцать девять"
        30 -> "тридцать"
        31 -> "тридцать один"
        40 -> "сорок"
        50 -> "пятьдесят"
        59 -> "пятьдесят девять"
        else -> value.toString()
    }

    private fun numberToWordsFeminine(value: Int): String = when (value) {
        1 -> "одна"
        2 -> "две"
        3 -> "три"
        4 -> "четыре"
        in 5..19 -> numberToWords(value)
        20 -> "двадцать"
        21 -> "двадцать одна"
        22 -> "двадцать две"
        23 -> "двадцать три"
        24 -> "двадцать четыре"
        25 -> "двадцать пять"
        26 -> "двадцать шесть"
        27 -> "двадцать семь"
        28 -> "двадцать восемь"
        29 -> "двадцать девять"
        30 -> "тридцать"
        31 -> "тридцать одна"
        32 -> "тридцать две"
        33 -> "тридцать три"
        34 -> "тридцать четыре"
        35 -> "тридцать пять"
        36 -> "тридцать шесть"
        37 -> "тридцать семь"
        38 -> "тридцать восемь"
        39 -> "тридцать девять"
        40 -> "сорок"
        41 -> "сорок одна"
        42 -> "сорок две"
        43 -> "сорок три"
        44 -> "сорок четыре"
        45 -> "сорок пять"
        46 -> "сорок шесть"
        47 -> "сорок семь"
        48 -> "сорок восемь"
        49 -> "сорок девять"
        50 -> "пятьдесят"
        51 -> "пятьдесят одна"
        52 -> "пятьдесят две"
        53 -> "пятьдесят три"
        54 -> "пятьдесят четыре"
        55 -> "пятьдесят пять"
        56 -> "пятьдесят шесть"
        57 -> "пятьдесят семь"
        58 -> "пятьдесят восемь"
        59 -> "пятьдесят девять"
        else -> value.toString()
    }
}
