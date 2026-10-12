package com.codeeditor.ui.editor

import com.codeeditor.core.editor.LineEnding
import com.codeeditor.core.editor.Selection
import com.codeeditor.core.editor.TextPosition

/** Immutable snapshot of everything the editor UI needs to render one frame. */
data class EditorUiState(
    val fileName: String = "Untitled",
    val languageId: String = "plaintext",
    val contentVersion: Int = 0,
    val lineCount: Int = 1,
    val cursor: TextPosition = TextPosition.Zero,
    val selection: Selection = Selection.Zero,
    val selections: List<Selection> = emptyList(),
    val isDirty: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val lineEnding: LineEnding = LineEnding.LF
) {
    /** All carets/selections, falling back to the primary one. */
    val effectiveSelections: List<Selection>
        get() = selections.ifEmpty { listOf(selection) }
}
