package com.kaung139abc.hyperrecap

import java.text.BreakIterator
import java.util.Locale

/**
 * Splits long Myanmar text without cutting a character cluster.
 *
 * maxChars is measured in UTF-16 code units to match Kotlin String.length.
 * A single cluster may exceed the limit; it is kept intact rather than split.
 */
object MyanmarTextChunker {
    fun splitAtCharacterBoundaries(text: String, maxChars: Int): List<String> {
        require(maxChars > 0) { "maxChars must be positive" }
        if (text.isEmpty()) return emptyList()

        val iterator = BreakIterator.getCharacterInstance(Locale("my"))
        iterator.setText(text)
        val chunks = ArrayList<String>()
        var chunkStart = iterator.first()
        var chunkEnd = iterator.next()
        var currentLength = 0
        var currentStart = chunkStart

        while (chunkEnd != BreakIterator.DONE) {
            val clusterLength = chunkEnd - chunkStart
            if (currentLength > 0 && currentLength + clusterLength > maxChars) {
                chunks += text.substring(currentStart, chunkStart)
                currentStart = chunkStart
                currentLength = 0
            }
            currentLength += clusterLength
            chunkStart = chunkEnd
            chunkEnd = iterator.next()
        }

        if (currentLength > 0) chunks += text.substring(currentStart, text.length)
        return chunks
    }
}
