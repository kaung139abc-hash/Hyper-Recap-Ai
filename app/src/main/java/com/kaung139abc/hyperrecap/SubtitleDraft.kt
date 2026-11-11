package com.kaung139abc.hyperrecap

/**
 * Creates an editable narration subtitle draft from recap text.
 * Timing is estimated from text length; it is not source-video speech alignment.
 */
object SubtitleDraft {
    fun fromNarration(text: String): String {
        val lines = text
            .replace(Regex("(?m)^\\s*\\d+[.)]\\s*"), "")
            .split(Regex("(?<=[.!?။])\\s+|\\n+"))
            .flatMap { splitLongLine(it.trim(), 42) }
            .filter { it.isNotBlank() }
        if (lines.isEmpty()) return ""

        var cursorMs = 0L
        return lines.mapIndexed { index, line ->
            val durationMs = (line.codePointCount(0, line.length) * 115L).coerceIn(2200L, 6500L)
            val start = cursorMs
            val end = start + durationMs
            cursorMs = end + 250L
            (index + 1).toString() + "\n" + timestamp(start) + " --> " + timestamp(end) + "\n" + line
        }.joinToString("\n\n") + "\n"
    }

    private fun splitLongLine(line: String, maxChars: Int): List<String> {
        if (line.length <= maxChars) return listOf(line)
        val words = line.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size > 1) {
            val result = mutableListOf<String>()
            var current = StringBuilder()
            for (word in words) {
                if (current.isNotEmpty() && current.length + 1 + word.length > maxChars) {
                    result += current.toString()
                    current = StringBuilder()
                }
                if (current.isNotEmpty()) current.append(' ')
                current.append(word)
            }
            if (current.isNotEmpty()) result += current.toString()
            return result.flatMap { if (it.length > maxChars) splitLongLine(it, maxChars) else listOf(it) }
        }
        return line.chunked(maxChars)
    }

    private fun timestamp(ms: Long): String {
        val hours = ms / 3_600_000
        val minutes = (ms % 3_600_000) / 60_000
        val seconds = (ms % 60_000) / 1_000
        val millis = ms % 1_000
        return String.format(java.util.Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }
}
