package com.codeeditor.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.codeeditor.R
import com.codeeditor.core.editor.highlight.SyntaxToken
import com.codeeditor.ui.editor.EditorAction
import com.codeeditor.ui.editor.EditorStatusBar
import com.codeeditor.ui.editor.EditorStyle
import com.codeeditor.ui.editor.CompletionPopup
import com.codeeditor.ui.editor.EditorSurface
import com.codeeditor.ui.editor.FindBar
import com.codeeditor.ui.editor.FindCallbacks
import com.codeeditor.ui.editor.KeyRow
import com.codeeditor.ui.settings.SettingsCallbacks
import com.codeeditor.ui.settings.SettingsSheet
import com.codeeditor.ui.files.FileTreePanel
import com.codeeditor.ui.theme.EditorColors
import com.codeeditor.ui.theme.LocalEditorColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    state: WorkspaceUiState,
    style: EditorStyle,
    lineText: (Int) -> String,
    tokensForLine: (Int) -> List<SyntaxToken>,
    onAction: (EditorAction) -> Unit,
    findCallbacks: FindCallbacks,
    onCompletionSelect: (Int) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewFile: () -> Unit,
    onSave: () -> Unit,
    onOpenFilePicker: () -> Unit,
    onOpenFolderPicker: () -> Unit,
    onOpenFile: (String) -> Unit,
    onTreeRowClick: (TreeRow) -> Unit,
    settingsCallbacks: SettingsCallbacks,
    onMessageShown: () -> Unit
) {
    val colors = LocalEditorColors.current
    var showFiles by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState()

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onMessageShown()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.activeTab?.title ?: stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                actions = {
                    IconButton(onClick = { onAction(EditorAction.Undo) }, enabled = state.editor.canUndo) {
                        Icon(Icons.Filled.Undo, contentDescription = stringResource(R.string.menu_undo))
                    }
                    IconButton(onClick = { onAction(EditorAction.Redo) }, enabled = state.editor.canRedo) {
                        Icon(Icons.Filled.Redo, contentDescription = stringResource(R.string.menu_redo))
                    }
                    IconButton(onClick = { onAction(EditorAction.OpenFind) }) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.find_hint))
                    }
                    IconButton(onClick = onSave, enabled = state.editor.isDirty) {
                        Icon(Icons.Filled.Save, contentDescription = stringResource(R.string.menu_save))
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.menu_more))
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_new)) },
                                onClick = {
                                    showMenu = false
                                    onNewFile()
                                },
                                leadingIcon = { Icon(Icons.Filled.NoteAdd, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_open)) },
                                onClick = {
                                    showMenu = false
                                    onOpenFilePicker()
                                },
                                leadingIcon = { Icon(Icons.Filled.FolderOpen, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_key_row)) },
                                onClick = {
                                    showMenu = false
                                    onAction(EditorAction.ToggleKeyRow)
                                },
                                leadingIcon = { Icon(Icons.Filled.Keyboard, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_settings)) },
                                onClick = {
                                    showMenu = false
                                    showSettings = true
                                },
                                leadingIcon = { Icon(Icons.Filled.Settings, null) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.gutterBackground,
                    titleContentColor = colors.foreground,
                    actionIconContentColor = colors.foreground
                )
            )
        },
        bottomBar = { EditorStatusBar(state.editor, colors) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            val expanded = maxWidth >= 720.dp
            if (expanded) {
                Row(Modifier.fillMaxSize()) {
                    FileTreePanel(
                        state = state,
                        onOpenFolderPicker = onOpenFolderPicker,
                        onOpenFile = onOpenFile,
                        onTreeRowClick = onTreeRowClick,
                        modifier = Modifier
                            .width(280.dp)
                            .fillMaxHeight()
                    )
                    VerticalDivider(color = colors.currentLineBackground)
                    EditorPane(
                        state = state,
                        style = style,
                        lineText = lineText,
                        tokensForLine = tokensForLine,
                        colors = colors,
                        onAction = onAction,
                        findCallbacks = findCallbacks,
                        onCompletionSelect = onCompletionSelect,
                        onFontSizeChange = onFontSizeChange,
                        onSelectTab = onSelectTab,
                        onCloseTab = onCloseTab,
                        onNewFile = onNewFile,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            } else {
                Box(Modifier.fillMaxSize()) {
                    EditorPane(
                        state = state,
                        style = style,
                        lineText = lineText,
                        tokensForLine = tokensForLine,
                        colors = colors,
                        onAction = onAction,
                        findCallbacks = findCallbacks,
                        onCompletionSelect = onCompletionSelect,
                        onFontSizeChange = onFontSizeChange,
                        onSelectTab = onSelectTab,
                        onCloseTab = onCloseTab,
                        onNewFile = onNewFile,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                // Compact: expose the file tree through a persistent trigger in the tab bar.
                FileTreeTrigger(
                    onClick = { showFiles = true },
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }
        }
    }

    if (showFiles) {
        ModalBottomSheet(
            onDismissRequest = { showFiles = false },
            sheetState = sheetState,
            containerColor = colors.gutterBackground
        ) {
            FileTreePanel(
                state = state,
                onOpenFolderPicker = {
                    showFiles = false
                    onOpenFolderPicker()
                },
                onOpenFile = {
                    showFiles = false
                    onOpenFile(it)
                },
                onTreeRowClick = onTreeRowClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.7f)
            )
        }
    }

    if (showSettings) {
        SettingsSheet(
            settings = state.settings,
            colors = colors,
            onDismiss = { showSettings = false },
            callbacks = settingsCallbacks
        )
    }
}

@Composable
private fun EditorPane(
    state: WorkspaceUiState,
    style: EditorStyle,
    lineText: (Int) -> String,
    tokensForLine: (Int) -> List<SyntaxToken>,
    colors: EditorColors,
    onAction: (EditorAction) -> Unit,
    findCallbacks: FindCallbacks,
    onCompletionSelect: (Int) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewFile: () -> Unit,
    modifier: Modifier
) {
    Column(modifier.background(colors.background)) {
        EditorTabs(
            tabs = state.tabs,
            activeTabId = state.activeTabId,
            colors = colors,
            onSelect = onSelectTab,
            onClose = onCloseTab,
            onNewFile = onNewFile
        )
        FindBar(
            state = state.find,
            colors = colors,
            callbacks = findCallbacks
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            EditorSurface(
                state = state.editor,
                style = style,
                lineText = lineText,
                tokensForLine = tokensForLine,
                onAction = onAction,
                modifier = Modifier.fillMaxSize(),
                completionVisible = state.completion.visible,
                onFontSizeChange = onFontSizeChange
            )
            CompletionPopup(
                state = state.completion,
                colors = colors,
                onSelect = onCompletionSelect,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            )
        }
        if (state.settings.showKeyRow) {
            KeyRow(
                colors = colors,
                onKey = { onAction(EditorAction.InsertText(it)) }
            )
        }
    }
}

@Composable
private fun FileTreeTrigger(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalEditorColors.current
    IconButton(
        onClick = onClick,
        modifier = modifier.padding(16.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.FolderOpen,
            contentDescription = "Files",
            tint = colors.caret
        )
    }
}
