package com.kaung139abc.hyperrecap

import java.util.Locale
import kotlin.math.ln
import kotlin.math.sqrt

/** Parses SRT cues and selects a diverse, chronological, source-timed recap. */
object SrtTranscript {
    data class Cue(val startMs: Long, val endMs: Long, val text: String)

    private val timing = Regex(
        """(?m)^\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{3}).*$"""
    )
    private val stop = setOf(
        "the", "and", "that", "this", "with", "from", "then", "when", "what",
        "where", "which", "there", "their", "have", "were", "been", "will",
        "would", "could", "should", "about", "into", "your", "you", "they",
        "them", "she", "her", "his", "him", "for", "are", "was", "but",
        "ဖြစ်", "သည်", "တွေ", "တော့", "များ", "ကို", "မှာ", "နှင့်", "ပြီး",
        "လည်း", "သော", "တယ်", "က", "နဲ့", "မ", "၏", "၍", "ထို့"
    )
    private val turningPointTerms = listOf(
        "but", "however", "because", "suddenly", "finally", "discover", "reveals",
        "secret", "betray", "kill", "dead", "escape", "กลับ", "သို့သော်", "ဒါပေမဲ့",
        "အမှန်တရား", "လျှို့ဝှက်", "သတ်", "သေဆုံး", "ထွက်ပြေး", "နောက်ဆုံး", "ရုတ်တရက်"
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
                .lineSequence().map { it.trim() }
                .filter { it.isNotBlank() && !it.matches(Regex("\\d+")) }
                .joinToString(" ").replace(Regex("<[^>]*>"), "").trim()
            if (body.isBlank()) null else Cue(start, end, body)
        }
    }

    /**
     * Uses inverse document frequency rather than raw word frequency, so repeated
     * filler/dialogue is less likely to dominate. Selection is spread over the
     * whole runtime, then redundancy is reduced while preserving chronology.
     * This remains an extractive heuristic, not semantic plot comprehension.
     */
    fun summarize(cues: List<Cue>, maxCues: Int = 8): List<Cue> {
        if (cues.isEmpty()) return emptyList()
        val ordered = cues.filter { it.endMs > it.startMs && it.text.isNotBlank() }
            .sortedBy { it.startMs }
        if (ordered.isEmpty()) return emptyList()
        val limit = maxCues.coerceIn(1, ordered.size)
        val tokenLists = ordered.map { cue ->
            MyanmarTextTokenizer.tokens(cue.text).filter { it !in stop }
        }
        val docFreq = mutableMapOf<String, Int>()
        tokenLists.forEach { tokens -> tokens.toSet().forEach { term ->
            docFreq[term] = (docFreq[term] ?: 0) + 1
        } }
        val total = ordered.size.toDouble()
        val start = ordered.first().startMs
        val end = ordered.last().endMs.coerceAtLeast(start + 1L)
        val span = (end - start).toDouble()

        data class Candidate(val index: Int, val cue: Cue, val terms: Set<String>, val score: Double)
        val candidates = ordered.mapIndexed { index, cue ->
            val tokens = tokenLists[index]
            val unique = tokens.toSet()
            val idf = tokens.sumOf { term ->
                ln(1.0 + (total + 1.0) / ((docFreq[term] ?: 0) + 0.5))
            } / sqrt(tokens.size.coerceAtLeast(1).toDouble())
            val duration = (cue.endMs - cue.startMs).coerceIn(500L, 12_000L)
            val lengthSignal = (tokens.size.coerceAtMost(18) / 18.0)
            val midpoint = (cue.startMs + cue.endMs) / 2.0
            val progress = ((midpoint - start) / span).coerceIn(0.0, 1.0)
            val arc = when {
                progress < 0.12 -> 1.18
                progress > 0.88 -> 1.22
                progress in 0.38..0.68 -> 1.08
                else -> 1.0
            }
            val lower = cue.text.lowercase(Locale.ROOT)
            val turning = if (turningPointTerms.any { lower.contains(it) }) 1.16 else 1.0
            val score = (idf * (0.75 + lengthSignal * 0.25) +
                ln(1.0 + duration / 1000.0) * 0.12) * arc * turning
            Candidate(index, cue, unique, score)
        }

        // Divide the timeline into acts and reserve slots in each act.
        val chosen = mutableListOf<Candidate>()
        val remaining = candidates.toMutableList()
        val acts = minOf(4, limit)
        for (act in 0 until acts) {
            val lower = act.toDouble() / acts
            val upper = (act + 1).toDouble() / acts
            val pool = remaining.filter { candidate ->
                val midpoint = (candidate.cue.startMs + candidate.cue.endMs) / 2.0
                val p = (midpoint - start) / span
                p >= lower && (p < upper || act == acts - 1)
            }
            val best = pool.maxByOrNull { it.score } ?: continue
            chosen += best
            remaining.remove(best)
        }
        while (chosen.size < limit && remaining.isNotEmpty()) {
            val best = remaining.maxByOrNull { candidate ->
                val redundancy = chosen.maxOfOrNull { prior ->
                    val union = (candidate.terms + prior.terms).size.coerceAtLeast(1)
                    candidate.terms.intersect(prior.terms).size.toDouble() / union
                } ?: 0.0
                candidate.score * (1.0 - redundancy * 0.65)
            } ?: break
            chosen += best
            remaining.remove(best)
        }
        return chosen.sortedBy { it.index }.map { it.cue }
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
