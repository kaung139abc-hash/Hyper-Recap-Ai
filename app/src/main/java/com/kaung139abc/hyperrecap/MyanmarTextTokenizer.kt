package com.kaung139abc.hyperrecap

import java.text.BreakIterator
import java.util.Locale

/**
 * JVM-compatible word segmentation. Android ICU classes are runtime stubs in
 * local JVM unit tests; java.text.BreakIterator works on both Android and JVM.
 * Myanmar dictionary segmentation quality varies by runtime, so downstream
 * ranking treats these tokens as heuristic signals, not linguistic truth.
 */
object MyanmarTextTokenizer {
    fun tokens(text: String): List<String> {
        val normalized = text.lowercase(Locale.ROOT)
        val iterator = BreakIterator.getWordInstance(Locale.forLanguageTag("my"))
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
