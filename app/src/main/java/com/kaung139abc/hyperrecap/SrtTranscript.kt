package com.kaung139abc.hyperrecap

import java.util.Locale

/** Parses SRT cues and creates a source-timed extractive recap without inventing timestamps. */
object SrtTranscript {
    data class Cue(val startMs: Long, val endMs: Long, val text: String)

    private val timing = Regex(
        """(?m)^\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{3}).*$"""
    )

    fun parse(source: String): List<Cue> {
        val normalized = source.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val matches = timing.findAll(normalized).toList()
        return matches.mapIndexedNotNull { index, match ->
            val start = timestamp(match, 1)
            val end = timestamp(match, 5)
            if (end <= start) return@mapIndexedNotNull null
            val bodyStart = match.range.last + 1
            val bodyEnd = if (index + 1 < matches.size) matches[index + 1].range.first else normalized.length
            val body = normalized.substring(bodyStart, bodyEnd)
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.matches(Regex("\\d+")) }
                .joinToString(" ")
                .replace(Regex("<[^>]*>"), "")
                .trim()
            if (body.isBlank()) null else Cue(start, end, body)
        }
    }

    fun summarize(cues: List<Cue>, maxCues: Int = 8): List<Cue> {
        if (cues.isEmpty()) return emptyList()
        val limit = maxCues.coerceIn(1, cues.size)
        val tokenizer = Regex("[\\p{L}\\p{N}]{2,}")
        val stop = setOf("the", "and", "that", "this", "with", "from", "then", "when",
            "ဖြစ်", "သည်", "တွေ", "တော့", "များ", "ကို", "မှာ", "နှင့်", "ပြီး")
        val frequency = mutableMapOf<String, Int>()
        cues.forEach { cue ->
            tokenizer.findAll(cue.text.lowercase(Locale.ROOT)).forEach {
                if (it.value !in stop) frequency[it.value] = (frequency[it.value] ?: 0) + 1
            }
        }
        val start = cues.minOf { it.startMs }
        val end = cues.maxOf { it.endMs }.coerceAtLeast(start + 1L)
        val span = (end - start).toDouble()
        val scored = cues.mapIndexed { index, cue ->
            val words = tokenizer.findAll(cue.text.lowercase(Locale.ROOT)).map { it.value }.toList()
            val score = words.sumOf { (frequency[it] ?: 0).toDouble() } /
                kotlin.math.sqrt(words.size.coerceAtLeast(1).toDouble())
            val midpoint = (cue.startMs + cue.endMs) / 2.0
            val act = (((midpoint - start) / span) * 4.0).toInt().coerceIn(0, 3)
            Triple(index, cue, score to act)
        }
        // Keep the recap's opening, middle turns and ending instead of selecting
        // every high-frequency line from one repetitive scene.
        val selected = linkedSetOf<Int>()
        val base = limit / 4
        val extra = limit % 4
        for (act in 0..3) {
            val quota = base + if (act < extra) 1 else 0
            scored.asSequence()
                .filter { it.third.second == act }
                .sortedByDescending { it.third.first }
                .take(quota)
                .forEach { selected += it.first }
        }
        if (selected.size < limit) {
            scored.asSequence()
                .filter { it.first !in selected }
                .sortedByDescending { it.third.first }
                .take(limit - selected.size)
                .forEach { selected += it.first }
        }
        return selected.sorted().map { cues[it] }
    }

    fun toSrt(cues: List<Cue>): String = cues.mapIndexed { index, cue ->
        "${index + 1}\n${format(cue.startMs)} --> ${format(cue.endMs)}\n${cue.text}"
    }.joinToString("\n\n", postfix = if (cues.isEmpty()) "" else "\n")

    private fun timestamp(match: MatchResult, offset: Int): Long {
        val h = match.groupValues[offset].toLong()
        val m = match.groupValues[offset + 1].toLong()
        val s = match.groupValues[offset + 2].toLong()
        val ms = match.groupValues[offset + 3].toLong()
        return (((h * 60 + m) * 60 + s) * 1000) + ms
    }

    private fun format(ms: Long): String {
        val h = ms / 3_600_000
        val m = (ms % 3_600_000) / 60_000
        val s = (ms % 60_000) / 1_000
        val milli = ms % 1_000
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", h, m, s, milli)
    }
}
