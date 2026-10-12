package com.codeeditor.core.editor.highlight

/**
 * A small hand-written lexical scanner driven by a [LanguageDefinition].
 *
 * It recognises line/block comments, string literals, numbers, identifiers
 * (classified as keyword / type / constant / function) and reports the state
 * that must be carried into the next line (currently only block comments are
 * multi-line aware).
 */
class ScannerSyntaxHighlighter(
    override val language: LanguageDefinition
) : SyntaxHighlighter {

    override fun highlight(line: String, startState: Int): HighlightResult {
        val tokens = ArrayList<SyntaxToken>()
        val n = line.length
        var i = 0
        var state = startState

        if (state == HlState.BLOCK_COMMENT) {
            val end = language.blockCommentEnd
            val closeAt = if (end != null) line.indexOf(end) else -1
            if (closeAt < 0) {
                if (n > 0) tokens.add(SyntaxToken(0, n, TokenType.COMMENT))
                return HighlightResult(tokens, HlState.BLOCK_COMMENT)
            }
            val stop = closeAt + end!!.length
            tokens.add(SyntaxToken(0, stop, TokenType.COMMENT))
            i = stop
            state = HlState.NORMAL
        }

        while (i < n) {
            val c = line[i]

            val lineComment = language.lineComment
            if (lineComment != null && line.startsWith(lineComment, i)) {
                tokens.add(SyntaxToken(i, n, TokenType.COMMENT))
                i = n
                continue
            }

            val blockStart = language.blockCommentStart
            if (blockStart != null && line.startsWith(blockStart, i)) {
                val endToken = language.blockCommentEnd ?: blockStart
                val closeAt = line.indexOf(endToken, i + blockStart.length)
                if (closeAt < 0) {
                    tokens.add(SyntaxToken(i, n, TokenType.COMMENT))
                    i = n
                    state = HlState.BLOCK_COMMENT
                } else {
                    val stop = closeAt + endToken.length
                    tokens.add(SyntaxToken(i, stop, TokenType.COMMENT))
                    i = stop
                }
                continue
            }

            val stringRule = language.strings.firstOrNull { line.startsWith(it.open, i) }
            if (stringRule != null) {
                val start = i
                i += stringRule.open.length
                var closed = false
                while (i < n) {
                    val ch = line[i]
                    if (stringRule.escape != null && ch == stringRule.escape && i + 1 < n) {
                        i += 2
                        continue
                    }
                    if (line.startsWith(stringRule.close, i)) {
                        i += stringRule.close.length
                        closed = true
                        break
                    }
                    i++
                }
                if (!closed && stringRule.open.length == 1 && stringRule.open == "`") {
                    state = HlState.MULTILINE_STRING
                }
                tokens.add(SyntaxToken(start, i.coerceAtMost(n), TokenType.STRING))
                continue
            }

            if (c.isDigit()) {
                val start = i
                i++
                while (i < n && (line[i].isLetterOrDigit() || line[i] == '.' || line[i] == '_')) i++
                tokens.add(SyntaxToken(start, i, TokenType.NUMBER))
                continue
            }

            if (c.isLetter() || c == '_' || c == '@') {
                val start = i
                i++
                while (i < n && (line[i].isLetterOrDigit() || line[i] == '_')) i++
                val word = line.substring(start, i)
                val type = classify(word, line, i)
                if (type != TokenType.DEFAULT) tokens.add(SyntaxToken(start, i, type))
                continue
            }

            i++
        }

        return HighlightResult(tokens, state)
    }

    private fun classify(word: String, line: String, nextIndex: Int): TokenType = when {
        language.keywords.contains(word) -> TokenType.KEYWORD
        language.constants.contains(word) -> TokenType.CONSTANT
        language.types.contains(word) -> TokenType.TYPE
        nextNonSpace(line, nextIndex) == '(' -> TokenType.FUNCTION
        else -> TokenType.DEFAULT
    }

    private fun nextNonSpace(line: String, from: Int): Char? {
        var i = from
        while (i < line.length) {
            if (!line[i].isWhitespace()) return line[i]
            i++
        }
        return null
    }
}
