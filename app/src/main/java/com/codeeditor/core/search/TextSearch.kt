package com.codeeditor.core.search

/**
 * Pure, dependency-free search over a flat string. Supports literal and regex
 * queries with optional case sensitivity and whole-word matching. Results are
 * returned as offset ranges so callers can map them onto lines/columns.
 */
object TextSearch {

    fun findAll(text: String, query: String, options: SearchOptions): List<SearchMatch> {
        if (query.isEmpty()) return emptyList()
        return if (options.regex) regexFindAll(text, query, options)
        else literalFindAll(text, query, options)
    }

    private fun literalFindAll(text: String, query: String, options: SearchOptions): List<SearchMatch> {
        val ignoreCase = !options.caseSensitive
        val result = ArrayList<SearchMatch>()
        var index = text.indexOf(query, 0, ignoreCase = ignoreCase)
        val step = maxOf(1, query.length)
        while (index >= 0) {
            val end = index + query.length
            if (isWordBoundary(text, index, end, options.wholeWord)) {
                result.add(SearchMatch(index, end))
            }
            index = text.indexOf(query, index + step, ignoreCase = ignoreCase)
        }
        return result
    }

    private fun regexFindAll(text: String, query: String, options: SearchOptions): List<SearchMatch> {
        val regex = runCatching {
            if (options.caseSensitive) Regex(query) else Regex(query, RegexOption.IGNORE_CASE)
        }.getOrNull() ?: return emptyList()
        return regex.findAll(text)
            .filter { it.value.isNotEmpty() }
            .map { SearchMatch(it.range.first, it.range.last + 1) }
            .filter { isWordBoundary(text, it.start, it.end, options.wholeWord) }
            .toList()
    }

    private fun isWordBoundary(text: String, start: Int, end: Int, wholeWord: Boolean): Boolean {
        if (!wholeWord) return true
        val before = if (start > 0) text[start - 1] else null
        val after = if (end < text.length) text[end] else null
        return (before == null || !isWordChar(before)) && (after == null || !isWordChar(after))
    }

    private fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_'
}
