package com.codeeditor.core.editor.highlight

/** Semantic categories produced by a [SyntaxHighlighter]. */
enum class TokenType {
    DEFAULT,
    KEYWORD,
    TYPE,
    FUNCTION,
    CONSTANT,
    NUMBER,
    STRING,
    COMMENT,
    ANNOTATION,
    OPERATOR
}

/** A highlighted span [start, end) within a single line. */
data class SyntaxToken(val start: Int, val end: Int, val type: TokenType)

/** Result of highlighting one line: the tokens and the state to feed the next line. */
data class HighlightResult(val tokens: List<SyntaxToken>, val endState: Int)
