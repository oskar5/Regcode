package com.codeeditor.core.editor.highlight

/**
 * Line-oriented syntax highlighted. Implementations must be pure functions of
 * (line, startState) so callers can cache line states and re-highlight ranges
 * after an edit without rescanning the whole document.
 */
interface SyntaxHighlighter {
    val language: LanguageDefinition
    fun highlight(line: String, startState: Int): HighlightResult
}

/** Shared scanner states carried across lines. */
object HlState {
    const val NORMAL = 0
    const val BLOCK_COMMENT = 1
    const val MULTILINE_STRING = 2
}
