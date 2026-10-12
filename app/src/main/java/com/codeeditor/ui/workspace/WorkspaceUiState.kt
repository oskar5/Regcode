package com.codeeditor.ui.workspace

import com.codeeditor.domain.model.EditorSettings
import com.codeeditor.domain.model.FileNode
import com.codeeditor.domain.model.RecentFile
import com.codeeditor.ui.editor.CompletionState
import com.codeeditor.ui.editor.EditorUiState
import com.codeeditor.ui.editor.FindState

/** One open editor tab. */
data class TabInfo(
    val id: String,
    val title: String,
    val isDirty: Boolean,
    val languageId: String
)

/** A flattened row of the project tree ready for rendering. */
data class TreeRow(val node: FileNode, val depth: Int)

/** Complete state of the workspace (tabs + files + editor + settings). */
data class WorkspaceUiState(
    val tabs: List<TabInfo> = emptyList(),
    val activeTabId: String? = null,
    val editor: EditorUiState = EditorUiState(),
    val find: FindState = FindState(),
    val completion: CompletionState = CompletionState(),
    val rootFolder: FileNode? = null,
    val folderChildren: Map<String, List<FileNode>> = emptyMap(),
    val expandedDirs: Set<String> = emptySet(),
    val recentFiles: List<RecentFile> = emptyList(),
    val settings: EditorSettings = EditorSettings(),
    val isLoading: Boolean = false,
    val message: String? = null
) {
    val activeTab: TabInfo? get() = tabs.firstOrNull { it.id == activeTabId }
}
