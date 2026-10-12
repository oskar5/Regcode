package com.codeeditor.core.editor

import com.codeeditor.core.editor.edit.TextEdit
import com.codeeditor.core.editor.edit.UndoRedoManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoRedoManagerTest {

    @Test
    fun `records and undoes an insert`() {
        val mgr = UndoRedoManager()
        assertFalse(mgr.canUndo)
        mgr.record(TextEdit.Insert(0, "hello"))
        assertTrue(mgr.canUndo)

        val edit = mgr.undo()
        assertEquals(TextEdit.Insert(0, "hello"), edit)
        assertFalse(mgr.canUndo)
        assertTrue(mgr.canRedo)
    }

    @Test
    fun `redo returns the same edit`() {
        val mgr = UndoRedoManager()
        mgr.record(TextEdit.Insert(3, "x"))
        mgr.undo()
        assertEquals(TextEdit.Insert(3, "x"), mgr.redo())
        assertFalse(mgr.canRedo)
    }

    @Test
    fun `recording clears redo stack`() {
        val mgr = UndoRedoManager()
        mgr.record(TextEdit.Insert(0, "a"))
        mgr.undo()
        mgr.record(TextEdit.Insert(0, "b"))
        assertFalse(mgr.canRedo)
    }

    @Test
    fun `empty edits are ignored`() {
        val mgr = UndoRedoManager()
        mgr.record(TextEdit.Insert(0, ""))
        assertFalse(mgr.canUndo)
        assertNull(mgr.undo())
    }

    @Test
    fun `history is bounded`() {
        val mgr = UndoRedoManager(maxHistory = 2)
        mgr.record(TextEdit.Insert(0, "a"))
        mgr.record(TextEdit.Insert(0, "b"))
        mgr.record(TextEdit.Insert(0, "c"))
        mgr.undo()
        mgr.undo()
        assertFalse(mgr.canUndo)
    }
}
