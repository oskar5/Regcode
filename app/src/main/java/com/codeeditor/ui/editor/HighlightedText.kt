package com.codeeditor.ui.editor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.codeeditor.core.editor.highlight.SyntaxToken
import com.codeeditor.core.editor.highlight.TokenType
import com.codeeditor.ui.theme.EditorColors

/** Converts a line + its syntax tokens into a coloured [AnnotatedString]. */
fun buildHighlightedText(
    line: String,
    tokens: List<SyntaxToken>,
    colors: EditorColors
): AnnotatedString {
    if (tokens.isEmpty()) return AnnotatedString(line)
    return buildAnnotatedString {
        var cursor = 0
        for (token in tokens) {
            val start = token.start.coerceIn(0, line.length)
            val end = token.end.coerceIn(start, line.length)
            if (start > cursor) append(line.substring(cursor, start))
            if (end > start) {
                withStyle(SpanStyle(color = colorFor(token.type, colors))) {
                    append(line.substring(start, end))
                }
            }
            cursor = end
        }
        if (cursor < line.length) append(line.substring(cursor))
    }
}

private fun colorFor(type: TokenType, c: EditorColors): androidx.compose.ui.graphics.Color = when (type) {
    TokenType.KEYWORD -> c.keyword
    TokenType.TYPE -> c.type
    TokenType.FUNCTION -> c.function
    TokenType.CONSTANT -> c.constant
    TokenType.NUMBER -> c.number
    TokenType.STRING -> c.string
    TokenType.COMMENT -> c.comment
    else -> c.foreground
}
