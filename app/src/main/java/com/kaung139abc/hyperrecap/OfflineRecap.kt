package com.kaung139abc.hyperrecap

import java.util.Locale

/** Small extractive summarizer that runs entirely on-device; it makes no network calls. */
object OfflineRecap {
    fun summarize(text: String, maxSentences: Int = 8): String {
        val sentences = text.replace("\r", " ")
            .split(Regex("(?<=[.!?။])\\s+|\\n+"))
            .map { it.trim() }
            .filter { it.length >= 18 }
        if (sentences.isEmpty()) return "အကျဉ်းချုပ်ရန် စာကြောင်း မတွေ့ပါ။"
        val stopWords = setOf("the", "and", "that", "this", "with", "from", "then", "when", "ဖြစ်", "သည်", "တွေ", "တော့", "များ", "ကို", "မှာ", "နှင့်", "ပြီး")
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
            val score = words.sumOf { (frequency[it] ?: 0).toDouble() } / kotlin.math.sqrt(words.size.coerceAtLeast(1).toDouble())
            Triple(index, sentence, score)
        }
        val count = maxSentences.coerceAtLeast(1).coerceAtMost(sentences.size)
        return ranked.sortedByDescending { it.third }.take(count).sortedBy { it.first }
            .mapIndexed { index, item -> (index + 1).toString() + ". " + item.second }
            .joinToString("\n\n")
    }
}
