package com.kaung139abc.hyperrecap

import android.icu.text.BreakIterator
import java.util.Locale

/**
 * ICU dictionary-based word segmentation. A Unicode-letter regex sees an entire
 * Burmese clause as one token because Burmese normally has no spaces between words.
 * ICU's Myanmar dictionary gives the extractive ranker useful word-level signals.
 */
object MyanmarTextTokenizer {
    fun tokens(text: String): List<String> {
        val normalized = text.lowercase(Locale.ROOT)
        val iterator = BreakIterator.getWordInstance(Locale("my"))
        iterator.setText(normalized)
        val output = ArrayList<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            val token = normalized.substring(start, end).trim()
            if (token.length >= 2 && token.any { it.isLetterOrDigit() }) output.add(token)
            start = end
            end = iterator.next()
        }
        return output
    }
}
