package com.codeeditor.core.autocomplete

/** Semantic category of a [CompletionItem], lowercased to a sort hint. */
enum class CompletionKind { KEYWORD, TYPE, WORD, SNIPPET }

/** A single suggestion offered by the completion engine. */
data class CompletionItem(
    val label: String,
    val detail: String? = null,
    val kind: CompletionKind = CompletionKind.WORD
)