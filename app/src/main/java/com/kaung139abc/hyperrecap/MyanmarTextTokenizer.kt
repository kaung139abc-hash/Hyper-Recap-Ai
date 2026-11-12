package com.kaung139abc.hyperrecap

import android.icu.text.BreakIterator
import java.util.Locale

/**
 * Dictionary-based Unicode word segmentation for Burmese and other scripts
 * that do not put spaces between every word. Regex word matching treats an
 * entire Burmese clause as one token and breaks TF-IDF relevance scoring.
 */
object MyanmarTextTokenizer {
    fun tokens(text: String): List<String> {
        val normalized = text.lowercase(Locale.ROOT)
        val iterator = BreakIterator.getWordInstance(Locale("my"))
        iterator.setText(normalized)
        val result = mutableListOf<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            val token = normalized.substring(start, end).trim()
            if (token.length >= 2 && token.any { it.isLetterOrDigit() }) {
                result += token
            }
            start = end
            end = iterator.next()
        }
        return result
    }
}
