package com.codeeditor.core.editor.edit

/** An atomic, reversible mutation of a text buffer. */
sealed interface TextEdit {
    val offset: Int
    val length: Int

    data class Insert(override val offset: Int, val text: String) : TextEdit {
        override val length: Int get() = text.length
    }

    data class Delete(override val offset: Int, val text: String) : TextEdit {
        override val length: Int get() = text.length
    }

    /**
     * Several edits treated as a single undo unit. [edits] are stored in the
     * order they were applied; undoing applies their inverses in reverse order.
     */
    data class Group(val edits: List<TextEdit>) : TextEdit {
        override val offset: Int get() = edits.minOfOrNull { it.offset } ?: 0
        override val length: Int get() = edits.sumOf { it.length }
    }
}
