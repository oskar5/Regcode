package com.codeeditor.core.autocomplete

/**
 * A frequency-ranked index of identifiers found in a document. Used to offer
 * words already present in the file ahead of anything else, mimicking the
 * behaviour of mature editors. Case-insensitive on lookup but keeps the
 * original spelling of each word.
 */
class WordIndex {

    private val counts = HashMap<String, Int>()

    fun add(word: String) {
        counts[word] = (counts[word] ?: 0) + 1
    }

    /** Words starting with [prefix], most frequent first, then alphabetically. */
    fun matches(prefix: String, limit: Int = 100): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val needle = prefix.lowercase()
        return counts.entries
            .filter { it.key.lowercase().startsWith(needle) }
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenBy { it.key.lowercase() }
            )
            .map { it.key }
            .take(limit)
    }

    companion object {
        /** Builds an index from free text, skipping single-character tokens. */
        fun fromText(text: String): WordIndex {
            val index = WordIndex()
            val current = StringBuilder()
            fun flush() {
                if (current.length >= 2) index.add(current.toString())
                current.setLength(0)
            }
            for (ch in text) {
                if (ch.isLetterOrDigit() || ch == '_') current.append(ch) else flush()
            }
            flush()
            return index
        }
    }
}