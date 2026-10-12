package com.codeeditor.core.editor.edit

/**
 * Bounded undo/redo history of [TextEdit] operations.
 *
 * When [record] is called with `coalesce = true`, a new edit is merged into the
 * previous one if they are contiguous (e.g. typing a word becomes a single undo
 * step). Recording always clears the redo stack.
 */
class UndoRedoManager(private val maxHistory: Int = DEFAULT_HISTORY) {

    private val undoStack = ArrayDeque<TextEdit>()
    private val redoStack = ArrayDeque<TextEdit>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun record(edit: TextEdit, coalesce: Boolean = false) {
        if (isEmpty(edit)) return
        if (coalesce) {
            val merged = tryMerge(undoStack.lastOrNull(), edit)
            if (merged != null) {
                undoStack.removeLast()
                undoStack.addLast(merged)
                redoStack.clear()
                return
            }
        }
        undoStack.addLast(edit)
        while (undoStack.size > maxHistory) undoStack.removeFirst()
        redoStack.clear()
    }

    /** Returns the edit that must be inverted, or null. */
    fun undo(): TextEdit? {
        val edit = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(edit)
        return edit
    }

    /** Returns the edit that must be re-applied, or null. */
    fun redo(): TextEdit? {
        val edit = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(edit)
        return edit
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    private fun isEmpty(edit: TextEdit): Boolean = when (edit) {
        is TextEdit.Group -> edit.edits.isEmpty()
        else -> edit.length == 0
    }

    private fun tryMerge(last: TextEdit?, edit: TextEdit): TextEdit? {
        if (last is TextEdit.Insert && edit is TextEdit.Insert &&
            last.offset + last.text.length == edit.offset
        ) {
            return TextEdit.Insert(last.offset, last.text + edit.text)
        }
        if (last is TextEdit.Delete && edit is TextEdit.Delete &&
            edit.offset + edit.text.length == last.offset
        ) {
            return TextEdit.Delete(edit.offset, edit.text + last.text)
        }
        return null
    }

    companion object {
        const val DEFAULT_HISTORY = 500
    }
}
