package com.kaung139abc.hyperrecap

import java.util.Locale

/**
 * Extractive on-device summarizer. It does not call a network service or claim to be
 * generative AI. Long transcripts are summarized in bounded text windows first so
 * a multi-hour transcript does not create one enormous scoring pass.
 */
object OfflineRecap {
    private const val WINDOW_CHARS = 8_000

    fun summarize(text: String, maxSentences: Int = 8): String {
        val clean = text.replace("\r", " ").trim()
        if (clean.isBlank()) return "အကျဉ်းချုပ်ရန် စာသား မတွေ့ပါ။"
        if (clean.length <= WINDOW_CHARS) return summarizeWindow(clean, maxSentences)

        val windows = splitIntoWindows(clean)
        val intermediate = windows.mapNotNull { window ->
            summarizeWindow(window, 4).takeIf { it.isNotBlank() }
        }
        if (intermediate.isEmpty()) return "အကျဉ်းချုပ်ရန် စာကြောင်း မတွေ့ပါ။"
        val combined = intermediate.joinToString("\n")
        return summarizeWindow(combined, maxSentences)
    }

    private fun splitIntoWindows(text: String): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = (start + WINDOW_CHARS).coerceAtMost(text.length)
            if (end < text.length) {
                // Prefer a paragraph or sentence boundary; never move so far that
                // a single unusually long line prevents forward progress.
                val boundary = text.lastIndexOfAny(charArrayOf('\n', '။', '.', '!', '?'), end - 1)
                if (boundary > start + WINDOW_CHARS / 2) end = boundary + 1
            }
            val part = text.substring(start, end).trim()
            if (part.isNotBlank()) result += part
            start = end
        }
        return result
    }

    private fun summarizeWindow(text: String, maxSentences: Int): String {
        val sentences = text.replace("\r", " ")
            .split(Regex("(?<=[.!?။])\\s+|\\n+"))
            .map { it.trim() }
            .filter { it.length >= 18 }
        if (sentences.isEmpty()) return ""

        val stopWords = setOf(
            "the", "and", "that", "this", "with", "from", "then", "when",
            "ဖြစ်", "သည်", "တွေ", "တော့", "များ", "ကို", "မှာ", "နှင့်", "ပြီး"
        )
        val tokenizer = Regex("[\\p{L}\\p{N}]{2,}")
        val frequency = mutableMapOf<String, Int>()
        sentences.forEach { sentence ->
            tokenizer.findAll(sentence.lowercase(Locale.ROOT)).forEach { match ->
                val word = match.value
                if (word !in stopWords) frequency[word] = (frequency[word] ?: 0) + 1
            }
        }
        val ranked = sentences.mapIndexed { index, sentence ->
            val words = tokenizer.findAll(sentence.lowercase(Locale.ROOT)).map { it.value }.toList()
            val score = words.sumOf { (frequency[it] ?: 0).toDouble() } /
                kotlin.math.sqrt(words.size.coerceAtLeast(1).toDouble())
            Triple(index, sentence, score)
        }
        val count = maxSentences.coerceAtLeast(1).coerceAtMost(sentences.size)
        return ranked.sortedByDescending { it.third }.take(count).sortedBy { it.first }
            .mapIndexed { index, item -> "${index + 1}. ${item.second}" }
            .joinToString("\n\n")
    }
}
