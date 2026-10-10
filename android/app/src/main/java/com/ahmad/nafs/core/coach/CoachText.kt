package com.ahmad.nafs.core.coach

/** Coach replies are light Markdown; copies and shares go out as plain text any app can show. */
object CoachText {
    private val heading = Regex("^#{1,6}\\s+(.+)$")
    private val bullet = Regex("^[-+*•]\\s+(.+)$")
    private val bold = Regex("\\*\\*([^*]+)\\*\\*")
    private val blankRun = Regex("\\n{3,}")

    fun plain(reply: String): String {
        val lines = reply.replace("\r\n", "\n").split('\n')
            .filterNot { it.trim().startsWith("```") }
            .map { line ->
                val trimmed = line.trim()
                val text = when {
                    heading.matches(trimmed) -> heading.matchEntire(trimmed)!!.groupValues[1]
                    bullet.matches(trimmed) -> "• " + bullet.matchEntire(trimmed)!!.groupValues[1]
                    trimmed.startsWith("> ") -> trimmed.removePrefix("> ")
                    else -> line.trimEnd()
                }
                bold.replace(text) { it.groupValues[1] }
            }
        return lines.joinToString("\n").replace(blankRun, "\n\n").trim()
    }
}
