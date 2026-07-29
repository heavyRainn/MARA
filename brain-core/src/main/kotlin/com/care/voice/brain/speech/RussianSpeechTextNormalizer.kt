package com.care.voice.brain.speech

object RussianSpeechTextNormalizer {

    internal const val PARAGRAPH_MARKER = "\u0001"

    private val headingRegex = Regex("""^#{1,6}\s+(.+)$""", RegexOption.MULTILINE)
    private val bulletLineRegex = Regex("""(?m)^\s*[-*+]\s+""")
    private val numberedLineRegex = Regex("""(?m)^\s*\d+\.\s+""")
    private val blockquoteRegex = Regex("""^\s*>\s?""", RegexOption.MULTILINE)
    private val markdownLinkRegex = Regex("""\[([^\]]+)]\([^)]+\)""")
    private val rawUrlRegex = Regex("""https?://\S+""")
    private val fencedCodeRegex = Regex("""```[\s\S]*?```""")
    private val inlineCodeRegex = Regex("""`([^`]+)`""")
    private val htmlTagRegex = Regex("""<[^>]+>""")
    private val citationRegex = Regex("""\[\d+]|\(\d{4}\)|\^\d+""")
    private val markdownDecorRegex = Regex("""[*_~`#]+""")
    private val emojiRegex = Regex("""[\uD83C-\uDBFF\uDC00-\uDFFF]+|[\u2600-\u27BF]""")
    private val repeatedPunctRegex = Regex("""([!?.,])\1{2,}""")
    private val whitespaceRegex = Regex("""\s+""")
    private val nameAddressRegex = Regex("""(?<=[.!?…\s]|^)([А-ЯЁ][а-яё]{1,20}),\s""")
    private val colonBeforeListRegex = Regex("""(:)\s+(?=[А-ЯЁA-Z«"0-9])""")

    fun normalize(input: String): String {
        if (input.isBlank()) return ""

        var text = input
        text = fencedCodeRegex.replace(text) { " Я вывела код на экран. " }
        text = headingRegex.replace(text) { "${it.groupValues[1]}. $PARAGRAPH_MARKER " }
        text = blockquoteRegex.replace(text, "")
        text = bulletLineRegex.replace(text, "")
        text = numberedLineRegex.replace(text, "")
        text = markdownLinkRegex.replace(text) { it.groupValues[1] }
        text = rawUrlRegex.replace(text, " Я добавила ссылку на экран. ")
        text = inlineCodeRegex.replace(text) { it.groupValues[1] }
        text = htmlTagRegex.replace(text, "")
        text = citationRegex.replace(text, "")
        text = markdownDecorRegex.replace(text, "")
        text = emojiRegex.replace(text, "")
        text = repeatedPunctRegex.replace(text, "$1")
        text = nameAddressRegex.replace(text) { "${it.groupValues[1]}, $PARAGRAPH_MARKER " }
        text = colonBeforeListRegex.replace(text) { "${it.groupValues[1]} $PARAGRAPH_MARKER " }
        text = text.replace("\r\n", "\n")
        text = text.replace(Regex("""\n\s*[-*+]\s+"""), " $PARAGRAPH_MARKER ")
        text = text.replace(Regex("""\n\s*\d+\.\s+"""), " $PARAGRAPH_MARKER ")
        text = text.replace("\n\n", " $PARAGRAPH_MARKER ")
        text = text.replace("\n", " ")
        text = whitespaceRegex.replace(text, " ").trim()
        text = text.replace(Regex("""\s*\u0001\s*"""), " $PARAGRAPH_MARKER ")
        text = RussianSpeechTtsExpander.expand(text)
        return text.trim()
    }
}
