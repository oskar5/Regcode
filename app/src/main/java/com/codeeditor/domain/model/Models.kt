package com.codeeditor.domain.model

import com.codeeditor.core.editor.LineEnding
import com.codeeditor.core.encoding.TextEncoding

/** A node in the opened project tree. */
data class FileNode(
    val uri: String,
    val name: String,
    val isDirectory: Boolean,
    val mimeType: String?
)

/** A file that was read from disk together with its detected metadata. */
data class LoadedDocument(
    val name: String,
    val text: String,
    val encoding: TextEncoding,
    val hasBom: Boolean,
    val lineEnding: LineEnding
)

/** An entry in the "recent files" list. */
data class RecentFile(
    val uri: String,
    val title: String,
    val lastOpenedAt: Long
)

/** Persisted state of one open tab, used to restore the session. */
data class SessionDocument(
    val uri: String,
    val title: String,
    val languageId: String,
    val content: String,
    val cursorLine: Int,
    val cursorColumn: Int,
    val isDirty: Boolean,
    val orderIndex: Int
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** User-configurable editor preferences. */
data class EditorSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val editorSchemeId: String = "default",
    val fontFamilyId: String = "monospace",
    val fontSizeSp: Float = 14f,
    val showLineNumbers: Boolean = true,
    val wordWrap: Boolean = false,
    val tabWidth: Int = 4,
    val autosave: Boolean = true,
    val autoCloseBrackets: Boolean = true,
    val showKeyRow: Boolean = true,
    val lastFolderUri: String? = null,
    val lastActiveUri: String? = null
)
