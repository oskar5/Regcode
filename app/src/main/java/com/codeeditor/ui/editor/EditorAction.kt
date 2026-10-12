package com.codeeditor.ui.editor

/** All user intents the editor screen can raise. Keeps the UI free of VM types. */
sealed interface EditorAction {
    data class InsertText(val text: String) : EditorAction
    data object Newline : EditorAction
    data object InsertTab : EditorAction
    data object Backspace : EditorAction
    data object DeleteForward : EditorAction
    data object Undo : EditorAction
    data object Redo : EditorAction
    data object MoveLeft : EditorAction
    data object MoveRight : EditorAction
    data object MoveUp : EditorAction
    data object MoveDown : EditorAction
    data object MoveLineStart : EditorAction
    data object MoveLineEnd : EditorAction
    data class MoveTo(val line: Int, val column: Int) : EditorAction

    data object ToggleComment : EditorAction
    data object DuplicateLine : EditorAction
    data object MoveLineUp : EditorAction
    data object MoveLineDown : EditorAction
    data object AddNextOccurrence : EditorAction
    data object SelectWord : EditorAction

    data object OpenFind : EditorAction
    data object OpenReplace : EditorAction
    data object CloseFind : EditorAction
    data object FindNext : EditorAction
    data object FindPrevious : EditorAction
    data object ReplaceCurrent : EditorAction
    data object ReplaceAll : EditorAction

    data object ShowCompletions : EditorAction
    data object CompletionNext : EditorAction
    data object CompletionPrevious : EditorAction
    data object AcceptCompletion : EditorAction
    data object DismissCompletion : EditorAction

    data object ToggleKeyRow : EditorAction
}
