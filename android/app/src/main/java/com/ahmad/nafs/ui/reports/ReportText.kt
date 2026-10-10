package com.ahmad.nafs.ui.reports

enum class ReportTextKind { PARAGRAPH, HEADING, BULLET, QUOTE, CODE, SPACE }
data class ReportTextLine(val text: String, val kind: ReportTextKind, val headingLevel: Int = 0, val marker: String? = null)
data class ReportTextRun(val text: String, val bold: Boolean = false)

/** The saved coach format is prose with headings and lists. HTML stays literal text. */
object ReportText {
    private val heading = Regex("^(#{1,6})\\s+(.+)$")
    private val bullet = Regex("^[-+*•]\\s+(.+)$")
    private val numbered = Regex("^(\\d+[.)])\\s+(.+)$")
    private val bold = Regex("\\*\\*([^*]+)\\*\\*")

    fun lines(text: String): List<ReportTextLine> {
        var inCode = false
        return text.replace("\r\n", "\n").split('\n').map { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("```") -> {
                    inCode = !inCode
                    ReportTextLine(line, ReportTextKind.CODE)
                }
                inCode -> ReportTextLine(line, ReportTextKind.CODE)
                trimmed.isEmpty() -> ReportTextLine("", ReportTextKind.SPACE)
                heading.matches(trimmed) -> heading.matchEntire(trimmed)!!.let {
                    ReportTextLine(it.groupValues[2], ReportTextKind.HEADING, headingLevel = it.groupValues[1].length)
                }
                bullet.matches(trimmed) -> ReportTextLine(bullet.matchEntire(trimmed)!!.groupValues[1], ReportTextKind.BULLET, marker = "•")
                numbered.matches(trimmed) -> numbered.matchEntire(trimmed)!!.let {
                    ReportTextLine(it.groupValues[2], ReportTextKind.BULLET, marker = it.groupValues[1])
                }
                trimmed.startsWith("> ") -> ReportTextLine(trimmed.removePrefix("> "), ReportTextKind.QUOTE)
                else -> ReportTextLine(line, ReportTextKind.PARAGRAPH)
            }
        }
    }

    fun runs(text: String): List<ReportTextRun> = buildList {
        var offset = 0
        bold.findAll(text).forEach { match ->
            if (match.range.first > offset) add(ReportTextRun(text.substring(offset, match.range.first)))
            add(ReportTextRun(match.groupValues[1], bold = true))
            offset = match.range.last + 1
        }
        if (offset < text.length) add(ReportTextRun(text.substring(offset)))
    }
}
