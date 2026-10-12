package com.codeeditor.ui.editor

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.font.FontFamily

/** Visual + typographic configuration of the editor surface. */
@Immutable
data class EditorStyle(
    val fontFamily: FontFamily = FontFamily.Monospace,
    val useLigatures: Boolean = false,
    val fontSizeSp: Float = 14f,
    val lineHeightSp: Float = 21f,
    val tabWidth: Int = 4,
    val showLineNumbers: Boolean = true,
    val wordWrap: Boolean = false
) {
    val indentUnit: String get() = " ".repeat(tabWidth)
}
