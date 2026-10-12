package com.codeeditor.core.search

/** User-configurable matching behaviour for a search. */
data class SearchOptions(
    val caseSensitive: Boolean = false,
    val wholeWord: Boolean = false,
    val regex: Boolean = false
)
