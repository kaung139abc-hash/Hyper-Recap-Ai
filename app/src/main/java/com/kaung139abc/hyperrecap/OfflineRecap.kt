package com.kaung139abc.hyperrecap

import java.util.Locale
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Offline, extractive recap engine.
 *
 * It selects evidence already present in the transcript; it does not invent plot
 * details or claim to be a generative language model. Large transcripts are
 * summarized hierarchically in bounded windows to keep memory use predictable.
 */
object OfflineRecap {
    private const val WINDOW_CHARS = 7_000
    private const val MIN_SENTENCE_CHARS = 12
    // Burmese full stops are often written without a following space.
    // Split after punctuation whether or not whitespace follows it.
    private val sentenceBoundary = Regex("(?<=[။.!?])\\s*|\\n+")

    private val turningPointTerms = listOf(
        "but", "however", "because", "suddenly", "finally", "discover", "reveals", "secret", "betray", "escape",
        "သို့သော်", "ဒါပေမဲ့", "အမှန်တရား", "လျှို့ဝှက်", "သတ်", "သေဆုံး", "ထွက်ပြေး", "နောက်ဆုံး", "ရုတ်တရက်", "ဖော်ထုတ်"
    )

    private val stopWords = setOf(
        "the", "and", "that", "this", "with", "from", "then", "when", "what",
        "where", "which", "there", "their", "have", "were", "been", "will",
        "would", "could", "should", "about", "into", "your", "you", "they",
        "them", "she", "her", "his", "him", "for", "are", "was", "but",
        "ဖြစ်", "သည်", "တွေ", "တော့", "များ", "ကို", "မှာ", "နှင့်", "ပြီး",
        "လည်း", "သော", "တယ်", "က", "နဲ့", "မ", "၏", "၍", "ထို့"
    )

    fun summarize(text: String, maxSentences: Int = 8): String {
        val clean = text.replace("\r", " ").trim()
        if (clean.isBlank()) return "အကျဉ်းချုပ်ရန် စာသား မတွေ့ပါ။"
        val limit = maxSentences.coerceIn(1, 30)
        val windows = splitIntoWindows(clean)
        if (windows.size == 1) return summarizeWindow(windows.first(), limit)
        val intermediate = windows.mapNotNull { summarizeWindow(it, 5).takeIf(String::isNotBlank) }
        if (intermediate.isEmpty()) return "အကျဉ်းချုပ်ရန် စာကြောင်း မတွေ့ပါ။"
        // The second pass condenses the chronological window summaries.
        return summarizeWindow(intermediate.joinToString("\n"), limit)
    }

    private fun splitIntoWindows(text: String): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = (start + WINDOW_CHARS).coerceAtMost(text.length)
            if (end < text.length) {
                val boundary = text.lastIndexOfAny(charArrayOf('\n', '။', '.', '!', '?'), end - 1)
                if (boundary > start + WINDOW_CHARS / 2) end = boundary + 1
            }
            val part = text.substring(start, end).trim()
            if (part.isNotBlank()) result += part
            start = end.coerceAtLeast(start + 1)
        }
        return result
    }

    private data class Candidate(
        val index: Int,
        val text: String,
        val terms: Set<String>,
        val score: Double
    )

    private fun summarizeWindow(text: String, maxSentences: Int): String {
        val sentences = text.replace("\r", " ")
            .split(sentenceBoundary)
            .map { it.trim().trimStart('-', '•', ' ', '\t') }
            .filter { it.length >= MIN_SENTENCE_CHARS }
            .distinctBy { normalize(it) }
        if (sentences.isEmpty()) return ""

        val termLists = sentences.map { sentence ->
            MyanmarTextTokenizer.tokens(sentence).filter { it !in stopWords }
        }
        val documentFrequency = mutableMapOf<String, Int>()
        termLists.forEach { terms -> terms.toSet().forEach { term ->
            documentFrequency[term] = (documentFrequency[term] ?: 0) + 1
        } }
        val total = sentences.size.toDouble()
        val candidates = sentences.mapIndexed { index, sentence ->
            val terms = termLists[index]
            val unique = terms.toSet()
            val weighted = terms.sumOf { term ->
                val idf = ln(1.0 + (total + 1.0) / ((documentFrequency[term] ?: 0) + 0.5))
                idf
            }
            val density = weighted / sqrt(terms.size.coerceAtLeast(1).toDouble())
            val positionBoost = when {
                index == 0 -> 1.12
                index == sentences.lastIndex -> 1.04
                else -> 1.0
            }
            val progress = index.toDouble() / (sentences.size - 1).coerceAtLeast(1)
            val storyArcBoost = when {
                progress < 0.10 -> 1.16
                progress > 0.90 -> 1.20
                progress in 0.38..0.68 -> 1.08
                else -> 1.0
            }
            val turningPointBoost = if (turningPointTerms.any { sentence.contains(it, ignoreCase = true) }) 1.18 else 1.0
            Candidate(index, sentence, unique, density * positionBoost * storyArcBoost * turningPointBoost)
        }

        val wanted = maxSentences.coerceIn(1, sentences.size)
        val selected = mutableListOf<Candidate>()
        val remaining = candidates.toMutableList()
        while (selected.size < wanted && remaining.isNotEmpty()) {
            val best = remaining.maxByOrNull { candidate ->
                val overlap = if (selected.isEmpty()) 0.0 else {
                    selected.maxOf { prior ->
                        val union = (candidate.terms + prior.terms).size.coerceAtLeast(1)
                        candidate.terms.intersect(prior.terms).size.toDouble() / union
                    }
                }
                candidate.score * (1.0 - overlap * 0.72)
            } ?: break
            selected += best
            remaining.remove(best)
        }
        return selected.sortedBy { it.index }
            .mapIndexed { index, candidate -> "${index + 1}. ${candidate.text}" }
            .joinToString("\n\n")
    }

    private fun normalize(value: String): String =
        value.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]"), "")
}
