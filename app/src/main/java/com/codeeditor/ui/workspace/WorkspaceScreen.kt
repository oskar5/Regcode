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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
    onSaveAs: () -> Unit,
    onGoToLine: (Int) -> Unit,
    onOpenFilePicker: () -> Unit,
    onOpenFolderPicker: () -> Unit,
    onOpenFile: (String) -> Unit,
    onTreeRowClick: (TreeRow) -> Unit,
    settingsCallbacks: SettingsCallbacks,
    onMessageShown: () -> Unit
) {
    val colors = LocalEditorColors.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    var showFiles by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showGoToLine by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var sidePanelOpen by rememberSaveable { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState()

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onMessageShown()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val windowWidth = maxWidth
        val isLandscape = maxWidth > maxHeight
        // Portrait keeps the original no-inline-panel threshold (720dp) so the
        // phone design is untouched; landscape follows the Material 600dp
        // compact breakpoint so more windows get an adaptive side panel.
        val compactWidth = if (isLandscape) windowWidth < 600.dp else windowWidth < 720.dp
        // The side panel never takes more than ~a third of the width and is
        // narrower on medium windows (phones / small tablets in landscape) so
        // the editor always keeps the majority of the screen.
        val panelWidth = (if (isLandscape && windowWidth < 840.dp) 200.dp else 280.dp)
            .coerceAtMost(windowWidth * 0.38f)

        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        if (!compactWidth) {
                            IconButton(onClick = { sidePanelOpen = !sidePanelOpen }) {
                                Icon(
                                    imageVector = Icons.Filled.Menu,
                                    contentDescription = stringResource(R.string.menu_files)
                                )
                            }
                        }
                    },
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
                        IconButton(onClick = {
                            val text = state.editor.selectedText
                            if (text.isNotEmpty()) clipboard.setText(AnnotatedString(text))
                        }, enabled = state.editor.selectedText.isNotEmpty()) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.menu_copy))
                        }
                        IconButton(onClick = {
                            val text = state.editor.selectedText
                            if (text.isNotEmpty()) {
                                clipboard.setText(AnnotatedString(text))
                                onAction(EditorAction.DeleteSelection)
                            }
                        }, enabled = state.editor.selectedText.isNotEmpty()) {
                            Icon(Icons.Filled.ContentCut, contentDescription = stringResource(R.string.menu_cut))
                        }
                        IconButton(onClick = {
                            val text = clipboard.getText()?.text
                            if (!text.isNullOrEmpty()) onAction(EditorAction.Paste(text))
                        }) {
                            Icon(Icons.Filled.ContentPaste, contentDescription = stringResource(R.string.menu_paste))
                        }
                        IconButton(onClick = { onAction(EditorAction.SelectAll) }) {
                            Icon(Icons.Filled.SelectAll, contentDescription = stringResource(R.string.menu_select_all))
                        }
                        IconButton(onClick = { onAction(EditorAction.SelectAll) }) {
                            Icon(Icons.Filled.SelectAll, contentDescription = stringResource(R.string.menu_select_all))
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
                                    text = { Text(stringResource(R.string.menu_save_as)) },
                                    onClick = {
                                        showMenu = false
                                        onSaveAs()
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Save, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_goto_line)) },
                                    onClick = {
                                        showMenu = false
                                        showGoToLine = true
                                    }
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
            bottomBar = { EditorStatusBar(state.editor, colors, compact = isLandscape) },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            val contentModifier = Modifier
                .padding(padding)
                .fillMaxSize()
            if (compactWidth) {
                // Phone portrait / narrow window: the editor fills the screen and
                // the file tree is reached via the floating trigger + bottom sheet.
                Box(contentModifier) {
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
                        onSaveAs = onSaveAs,
                        onGoToLine = { showGoToLine = true },
                        onOpenFilePicker = onOpenFilePicker,
                        compactHeight = isLandscape,
                        modifier = Modifier.fillMaxSize()
                    )
                    FileTreeTrigger(
                        onClick = { showFiles = true },
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
            } else {
                // Wide windows (tablets, phones in landscape): a collapsible side
                // panel beside an editor that always keeps the remaining width.
                Row(contentModifier) {
                    if (sidePanelOpen) {
                        FileTreePanel(
                            state = state,
                            onOpenFolderPicker = onOpenFolderPicker,
                            onOpenFile = onOpenFile,
                            onTreeRowClick = onTreeRowClick,
                            modifier = Modifier
                                .width(panelWidth)
                                .fillMaxHeight()
                        )
                        VerticalDivider(color = colors.currentLineBackground)
                    }
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
                        onSaveAs = onSaveAs,
                        onGoToLine = { showGoToLine = true },
                        onOpenFilePicker = onOpenFilePicker,
                        compactHeight = isLandscape,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
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

    if (showGoToLine) {
        var lineText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showGoToLine = false },
            title = { Text(stringResource(R.string.menu_goto_line)) },
            text = {
                TextField(
                    value = lineText,
                    onValueChange = { lineText = it.filter { c -> c.isDigit() } },
                    singleLine = true,
                    label = { Text(stringResource(R.string.menu_goto_line)) }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        lineText.toIntOrNull()?.let { onGoToLine(it) }
                        showGoToLine = false
                    },
                    enabled = lineText.isNotEmpty()
                ) { Text(stringResource(R.string.menu_goto_line)) }
            },
            dismissButton = {
                TextButton(onClick = { showGoToLine = false }) { Text(stringResource(R.string.dialog_cancel)) }
            }
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
    onSaveAs: () -> Unit,
    onGoToLine: () -> Unit,
    onOpenFilePicker: () -> Unit,
    compactHeight: Boolean,
    modifier: Modifier
) {
    Column(modifier.background(colors.background)) {
        EditorTabs(
            tabs = state.tabs,
            activeTabId = state.activeTabId,
            colors = colors,
            onSelect = onSelectTab,
            onClose = onCloseTab,
            onNewFile = onNewFile,
            compact = compactHeight
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
                onFontSizeChange = onFontSizeChange,
                onRequestOpen = onOpenFilePicker,
                onRequestSaveAs = onSaveAs,
                onRequestGoToLine = onGoToLine
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
                onKey = { onAction(EditorAction.InsertText(it)) },
                compact = compactHeight
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
