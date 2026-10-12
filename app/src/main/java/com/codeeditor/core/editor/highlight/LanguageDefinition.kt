package com.codeeditor.core.editor.highlight

/** A delimited string literal rule, e.g. `"`..`"` or `` ` ``..`` ` ``. */
data class StringRule(
    val open: String,
    val close: String = open,
    val escape: Char? = '\\'
)

/**
 * Declarative description of a language's lexical grammar. Kept intentionally
 * simple so it can be swapped later for a TextMate / Tree-sitter backed engine
 * without touching the editor or UI layers.
 */
data class LanguageDefinition(
    val id: String,
    val displayName: String,
    val extensions: Set<String>,
    val keywords: Set<String> = emptySet(),
    val types: Set<String> = emptySet(),
    val constants: Set<String> = emptySet(),
    val lineComment: String? = null,
    val blockCommentStart: String? = null,
    val blockCommentEnd: String? = null,
    val strings: List<StringRule> = listOf(StringRule("\""), StringRule("'")),
    val indentsAfter: Set<Char> = setOf('{', '(', '['),
    val unindentsAfter: Set<Char> = setOf('}', ')', ']')
)
