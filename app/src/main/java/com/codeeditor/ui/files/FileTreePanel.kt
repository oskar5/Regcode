package com.codeeditor.ui.files

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codeeditor.domain.model.FileNode
import com.codeeditor.ui.theme.EditorColors
import com.codeeditor.ui.theme.LocalEditorColors
import com.codeeditor.ui.workspace.TreeRow
import com.codeeditor.ui.workspace.WorkspaceUiState

/** Side panel: the opened project tree, or the recent-files list when no folder is open. */
@Composable
fun FileTreePanel(
    state: WorkspaceUiState,
    onOpenFolderPicker: () -> Unit,
    onOpenFile: (String) -> Unit,
    onTreeRowClick: (TreeRow) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorColors.current
    Column(modifier.background(colors.gutterBackground)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = (state.rootFolder?.name ?: "RECENT").uppercase(),
                color = colors.gutterForeground,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onOpenFolderPicker, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = if (state.rootFolder == null) Icons.Filled.FolderOpen else Icons.Filled.Refresh,
                    contentDescription = "Open folder",
                    tint = colors.gutterActiveForeground,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        val root = state.rootFolder
        if (root == null) {
            RecentList(state, colors, onOpenFile)
        } else {
            val rows = remember(root, state.folderChildren, state.expandedDirs) {
                flattenTree(root, state.folderChildren, state.expandedDirs)
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(rows, key = { it.node.uri }) { row ->
                    TreeRowItem(row, state.expandedDirs, colors, onTreeRowClick)
                }
            }
        }
    }
}

@Composable
private fun RecentList(
    state: WorkspaceUiState,
    colors: EditorColors,
    onOpenFile: (String) -> Unit
) {
    if (state.recentFiles.isEmpty()) {
        Text(
            text = "Відкрийте файл або теку,\nщоб почати.",
            color = colors.gutterForeground,
            fontSize = 12.sp,
            modifier = Modifier.padding(12.dp)
        )
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(state.recentFiles, key = { it.uri }) { recent ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenFile(recent.uri) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Description,
                    contentDescription = null,
                    tint = colors.gutterForeground,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = recent.title,
                    color = colors.foreground,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TreeRowItem(
    row: TreeRow,
    expandedDirs: Set<String>,
    colors: EditorColors,
    onClick: (TreeRow) -> Unit
) {
    val node = row.node
    val expanded = node.uri in expandedDirs
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(row) }
            .padding(start = (8 + row.depth * 14).dp, top = 7.dp, bottom = 7.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = iconFor(node, expanded),
            contentDescription = null,
            tint = if (node.isDirectory) colors.type else colors.gutterForeground,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = node.name,
            color = colors.foreground,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun iconFor(node: FileNode, expanded: Boolean): ImageVector = when {
    node.isDirectory && expanded -> Icons.Filled.FolderOpen
    node.isDirectory -> Icons.Filled.Folder
    else -> Icons.Filled.InsertDriveFile
}

/** Depth-first flattening of the currently expanded part of the tree. */
fun flattenTree(
    root: FileNode,
    children: Map<String, List<FileNode>>,
    expanded: Set<String>
): List<TreeRow> {
    val out = ArrayList<TreeRow>()
    fun visit(node: FileNode, depth: Int) {
        out.add(TreeRow(node, depth))
        if (node.isDirectory && node.uri in expanded) {
            children[node.uri]?.forEach { visit(it, depth + 1) }
        }
    }
    children[root.uri]?.forEach { visit(it, 0) }
    return out
}
