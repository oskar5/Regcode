package com.codeeditor.core.autocomplete

/** Everything a completion provider needs to reason about the current state. */
data class CompletionContext(
    val line: String,
    val caretColumn: Int,
    val languageId: String,
    val words: WordIndex
)

/**
 * Pluggable source of completion items keyed by a word prefix. Kept as a plain
 * interface so a language-server backed provider can be dropped in later
 * without touching the editor or UI layers.
 */
fun interface CompletionProvider {
    fun completions(prefix: String, context: CompletionContext): List<CompletionItem>
}

/**
 * Offers keywords/type names of the active language followed by words found in
 * the buffer (deduplicated, frequency-ranked).
 */
class BufferCompletionProvider(
    private val index: WordIndex,
    private val keywords: Set<String> = emptySet(),
    private val types: Set<String> = emptySet()
) : CompletionProvider {

    override fun completions(prefix: String, context: CompletionContext): List<CompletionItem> {
        val merged = LinkedHashMap<String, CompletionItem>()
        keywords.forEach { word ->
            if (word.lowercase().startsWith(prefix.lowercase())) {
                merged[word.lowercase()] = CompletionItem(word, kind = CompletionKind.KEYWORD)
            }
        }
        types.forEach { word ->
            if (word.lowercase().startsWith(prefix.lowercase())) {
                merged[word.lowercase()] = CompletionItem(word, kind = CompletionKind.TYPE)
            }
        }
        index.matches(prefix).forEach { word ->
            merged.putIfAbsent(word.lowercase(), CompletionItem(word, kind = CompletionKind.WORD))
        }
        return merged.values.toList()
    }
}