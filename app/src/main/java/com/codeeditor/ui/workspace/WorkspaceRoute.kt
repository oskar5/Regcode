package com.codeeditor.ui.workspace

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeeditor.R
import com.codeeditor.domain.model.EditorSettings
import com.codeeditor.domain.model.ThemeMode
import com.codeeditor.ui.editor.EditorStyle
import com.codeeditor.ui.editor.FindCallbacks
import com.codeeditor.ui.settings.SettingsCallbacks
import com.codeeditor.ui.theme.CodeEditorTheme

@Composable
fun WorkspaceRoute(viewModel: WorkspaceViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val openFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.openFile(it.toString()) } }

    val openFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { viewModel.openFolder(it.toString()) } }

    val findCallbacks = remember(viewModel) {
        FindCallbacks(
            onQueryChange = viewModel::setFindQuery,
            onReplacementChange = viewModel::setReplacement,
            onToggleCaseSensitive = viewModel::toggleCaseSensitive,
            onToggleWholeWord = viewModel::toggleWholeWord,
            onToggleRegex = viewModel::toggleRegex,
            onNext = viewModel::findNext,
            onPrevious = viewModel::findPrevious,
            onReplace = viewModel::replaceCurrent,
            onReplaceAll = viewModel::replaceAllMatches,
            onClose = viewModel::closeFind
        )
    }

    val settingsCallbacks = remember(viewModel) {
        SettingsCallbacks(
            onThemeMode = viewModel::setThemeMode,
            onScheme = viewModel::setEditorScheme,
            onFontFamily = viewModel::setFontFamily,
            onFontSize = viewModel::updateFontSize,
            onToggleLineNumbers = viewModel::toggleLineNumbers,
            onToggleWordWrap = viewModel::toggleWordWrap
        )
    }

    CodeEditorTheme(
        darkTheme = resolveDark(state.settings.themeMode),
        editorSchemeId = state.settings.editorSchemeId
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            WorkspaceScreen(
                state = state,
                style = styleFrom(state.settings),
                lineText = viewModel::activeLineText,
                tokensForLine = viewModel::activeTokens,
                onAction = viewModel::onAction,
                findCallbacks = findCallbacks,
                onCompletionSelect = viewModel::acceptCompletionAt,
                onFontSizeChange = viewModel::updateFontSize,
                onSelectTab = viewModel::selectTab,
                onCloseTab = viewModel::closeTab,
                onNewFile = viewModel::newFile,
                onSave = viewModel::saveActive,
                onOpenFilePicker = { openFileLauncher.launch(arrayOf("*/*")) },
                onOpenFolderPicker = { openFolderLauncher.launch(null) },
                onOpenFile = viewModel::openFile,
                onTreeRowClick = viewModel::onTreeRowClick,
                settingsCallbacks = settingsCallbacks,
                onMessageShown = viewModel::consumeMessage
            )
        }
    }
}

@Composable
private fun resolveDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

private fun styleFrom(settings: EditorSettings): EditorStyle = EditorStyle(
    fontFamily = when (settings.fontFamilyId) {
        "jetbrains" -> FontFamily(res = R.font.jetbrains_mono)
        "sans" -> FontFamily.SansSerif
        "serif" -> FontFamily.Serif
        else -> FontFamily.Monospace
    },
    useLigatures = settings.fontFamilyId == "jetbrains",
    fontSizeSp = settings.fontSizeSp,
    lineHeightSp = settings.fontSizeSp * 1.5f,
    tabWidth = settings.tabWidth,
    showLineNumbers = settings.showLineNumbers,
    wordWrap = settings.wordWrap
)
