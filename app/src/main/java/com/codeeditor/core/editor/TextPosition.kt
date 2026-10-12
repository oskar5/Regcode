package com.codeeditor.core.editor

/**
 * A zero-based position inside a [TextDocument]. Column is measured in UTF-16
 * code units, matching [String] indexing and the Compose text layout.
 */
data class TextPosition(val line: Int, val column: Int) : Comparable<TextPosition> {

    override fun compareTo(other: TextPosition): Int =
        if (line != other.line) line - other.line else column - other.column

    companion object {
        val Zero = TextPosition(0, 0)
    }
}

/**
 * A text selection described by a fixed [anchor] and a moving [caret].
 * When [anchor] == [caret] the selection is collapsed (a plain cursor).
 */
data class Selection(val anchor: TextPosition, val caret: TextPosition) {

    val isCollapsed: Boolean get() = anchor == caret

    val start: TextPosition get() = if (anchor <= caret) anchor else caret
    val end: TextPosition get() = if (anchor <= caret) caret else anchor

    fun reversed(): Selection = Selection(caret, anchor)

    companion object {
        fun collapsed(at: TextPosition): Selection = Selection(at, at)
        val Zero: Selection = collapsed(TextPosition.Zero)
    }
}
