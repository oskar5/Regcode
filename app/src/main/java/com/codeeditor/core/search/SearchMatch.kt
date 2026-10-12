package com.codeeditor.core.search

/** A half-open match range `[start, end)` in the document's flat text. */
data class SearchMatch(val start: Int, val end: Int) {
    val length: Int get() = end - start
}
