package com.codeeditor.core.autocomplete

/** Coordinates prefix detection and provider results. Dependency-free core. */
object CompletionEngine {

    /** The identifier fragment immediately before [caretColumn] on [line]. */
    fun prefixBeforeCaret(line: String, caretColumn: Int): String {
        val end = caretColumn.coerceIn(0, line.length)
        var start = end - 1
        while (start >= 0 && isWordChar(line[start])) start--
        return line.substring(start + 1, end)
    }

    /** Merges all providers into a deduplicated, ordered suggestion list. */
    fun complete(
        prefix: String,
        context: CompletionContext,
        providers: List<CompletionProvider>
    ): List<CompletionItem> {
        if (prefix.isEmpty() || providers.isEmpty()) return emptyList()
        val merged = LinkedHashMap<String, CompletionItem>()
        for (provider in providers) {
            for (item in provider.completions(prefix, context)) {
                merged.putIfAbsent(item.label.lowercase(), item)
            }
        }
        return merged.values.toList()
    }

    private fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_'
}