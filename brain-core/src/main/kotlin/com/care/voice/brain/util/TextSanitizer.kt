package com.care.voice.brain.util

object TextSanitizer {

    fun forUi(raw: String): String =
        raw
            .removeCodeBlocks()
            .removeInlineCode()
            .stripMarkdownDecor()
            .normalizeLists(keepBullets = true)
            .removeBulletDashes()          // 👈 уберём дефис-буллеты в начале строк
            .removeBackticksAndStars()     // 👈 выкинем бэктики/звёздочки всех видов
            .normalizePunctuation()
            .normalizeQuotes()
            .collapseSpaces()
            .trim()

    fun forTts(raw: String): String =
        raw
            .removeCodeBlocks()
            .removeInlineCode()
            .stripMarkdownDecor()
            .normalizeLists(keepBullets = false)
            .removeUrls()
            .removeEmojiAndSymbols()
            .removeBulletDashes()          // 👈 дефисы в начале строк
            .removeBackticksAndStars()     // 👈 бэктики/звёзды
            .removeInlineDashPauses()      // 👈 « - »/« — » в середине предложений
            .normalizePunctuation()
            .normalizeQuotes()
            .collapseSpaces()
            .trim()
            .ensureSentenceEnding()


    // ---------- шаги пайплайна ниже ----------
    private fun String.removeCodeBlocks(): String =
        // ```code``` или ~~~code~~~
        replace(Regex("(?s)```.*?```|~~~.*?~~~"), " ")

    private fun String.removeInlineCode(): String =
        // `inline`
        replace(Regex("`([^`]+)`"), "$1")

    private fun String.stripMarkdownDecor(): String =
        // **bold**, *italic*, __bold__, _italic_, ~~strike~~, заголовки '#'
        replace(Regex("[*_~]{1,2}"), "")
            .replace(Regex("(?m)^#{1,6}\\s*"), "")   // в начале строк
            .replace(Regex("(?m)^>\\s?"), "")        // цитаты >

    /**
     * Списки:
     * - "1. ", "1) ", "(1) " → убираем цифру; если keepBullets=true заменяем на "— "
     * - "- ", "* ", "• " → убираем или заменяем на "— "
     */
    private fun String.normalizeLists(keepBullets: Boolean): String {
        val bullet = if (keepBullets) "— " else ""
        var s = this
        s = s.replace(Regex("(?m)^\\s*(\\d+)[\\.)]\\s+"), bullet)  // 1. 1)
        s = s.replace(Regex("(?m)^\\s*\\(\\d+\\)\\s+"), bullet)    // (1)
        s = s.replace(Regex("(?m)^\\s*[-*•·]\\s+"), bullet)        // - * •
        return s
    }

    private fun String.removeUrls(): String =
        replace(Regex("(https?://|www\\.)\\S+"), " ") // можно заменить на "ссылка"

    /** Удаляем смайлы/пиктограммы/техзнаки — оставляем буквы/цифры и базовую пунктуацию */
    private fun String.removeEmojiAndSymbols(): String =
        map { ch ->
            if (ch.isLetterOrDigit() || " .,!?:;()-«»\"".indexOf(ch) >= 0) ch else ' '
        }.joinToString("")

    private fun Char.isLetterOrDigit(): Boolean =
        this.isLetter() || this.isDigit()

    private fun String.removeBackticksAndApostrophes(): String =
        replace("`", " ")
            .replace("’", "")
            .replace("'", "")

    /** Приводим пунктуацию в порядок: убираем повторы, ставим пробелы где нужно */
    private fun String.normalizePunctuation(): String {
        var s = this

        // троеточие → «…»
        s = s.replace(Regex("\\.\\.{2,}"), "…")

        // Повторы знаков → один знак
        s = s.replace(Regex("([!?.,;:])\\1+"), "$1")

        // Тире к нормальному длинному (по желанию)
        s = s.replace(Regex("\\s*-{2,}\\s*"), " — ")

        // Пробел перед пунктуацией — убрать; после — добавить
        s = s.replace(Regex("\\s+([!?.,;:])"), "$1")
        s = s.replace(Regex("([!?.,;:])(\\S)"), "$1 $2")

        // Лишние пробелы внутри скобок
        s = s.replace(Regex("\\(\\s+"), "(").replace(Regex("\\s+\\)"), ")")

        return s
    }

    /** Приводим кавычки к русским «ёлочкам» */
    private fun String.normalizeQuotes(): String =
        this.replace("“", "«")
            .replace("”", "»")
            .replace("„", "«")
            .replace("\"", "") // убираем прямые, чтобы TTS не бормотал

    private fun String.collapseSpaces(): String =
        replace(Regex("[ \\t\\u00A0]{2,}"), " ")
            .replace(Regex("(?m)^\\s+"), "")
            .replace(Regex("(?m)\\s+$"), "")
            .replace(Regex("\\n{3,}"), "\n\n")

    private fun String.ensureSentenceEnding(): String =
        if (isBlank()) this
        else if (trimEnd().last() !in ".!?…") this.trimEnd() + "."
        else this.trimEnd()

    // 2) ЗАМЕНИ старую функцию removeBackticksAndApostrophes() на ЭТУ:

    /** Удаляем дефис/тире как буллет в начале строк: -, –, —, ‒, − */
    private fun String.removeBulletDashes(): String =
        replace(Regex("(?m)^\\s*[\\-–—‒−]\\s+"), "") // начало строки: дефис + пробел → пусто

    /** Убираем «паузы» вида ' - ' / ' — ' внутри текста (для TTS), не трогаем составные слова */
    private fun String.removeInlineDashPauses(): String =
        replace(Regex("\\s+[\\-–—‒−]\\s+"), " ") // между словами дефис/тире с пробелами → пробел

    /** Жёстко убираем бэктики/апострофы/типографские кавычки и любые "звёздочки" */
    private fun String.removeBackticksAndStars(): String {
        val remove = setOf(
            // backticks
            '\u0060', // `
            '\uFF40', // ｀ fullwidth
            // primes / reversed primes
            '\u2032','\u2033','\u2034','\u2035','\u2036',
            // acute/grave/modifiers/apostrophes
            '\u00B4','\u02CB','\u02CA','\u02BC','\u02B9','\u02BB','\u02BD','\u02BE','\u02BF','\u02C8',
            // typographic quotes + "
            '\u2018','\u2019','\u201A','\u201B','\u201C','\u201D','\u201E','\u201F','\"',
            // stars (ASCII, fullwidth, math/star glyphs)
            '\u002A','\uFF0A','\u204E','\u2217','\u22C6',
            '\u2729','\u272A','\u272D','\u272F','\u2730','\u2731','\u2732','\u2733','\u2734','\u2735',
            '\u2736','\u2737','\u2738','\u2739','\u273A','\u273B','\u273C','\u2605','\u2606'
        )
        val sb = StringBuilder(length)
        for (ch in this) sb.append(if (ch in remove) ' ' else ch)
        return sb.toString().replace(Regex(" {2,}"), " ")
    }


}
