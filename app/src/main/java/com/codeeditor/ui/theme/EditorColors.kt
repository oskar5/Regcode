package com.codeeditor.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colours used by the code editor surface. Token colours are keyed by the
 * semantic categories emitted by the syntax highlighter.
 */
@Immutable
data class EditorColors(
    val background: Color,
    val foreground: Color,
    val gutterBackground: Color,
    val gutterForeground: Color,
    val gutterActiveForeground: Color,
    val currentLineBackground: Color,
    val caret: Color,
    val selection: Color,
    val keyword: Color,
    val type: Color,
    val function: Color,
    val constant: Color,
    val number: Color,
    val string: Color,
    val comment: Color
)

val DefaultDarkEditorColors = EditorColors(
    background = Color(0xFF1E1F22),
    foreground = Color(0xFFD4D4D8),
    gutterBackground = Color(0xFF1E1F22),
    gutterForeground = Color(0xFF6B6F76),
    gutterActiveForeground = Color(0xFFD4D4D8),
    currentLineBackground = Color(0x14FFFFFF),
    caret = Color(0xFF8AB4FF),
    selection = Color(0x554F8CFF),
    keyword = Color(0xFFCF8EF4),
    type = Color(0xFF4EC9B0),
    function = Color(0xFFDCDCAA),
    constant = Color(0xFF569CD6),
    number = Color(0xFFB5CEA8),
    string = Color(0xFFCE9178),
    comment = Color(0xFF6A9955)
)

val DefaultLightEditorColors = EditorColors(
    background = Color(0xFFFFFFFF),
    foreground = Color(0xFF1F2328),
    gutterBackground = Color(0xFFF6F8FA),
    gutterForeground = Color(0xFF8C959F),
    gutterActiveForeground = Color(0xFF1F2328),
    currentLineBackground = Color(0x0F000000),
    caret = Color(0xFF0969DA),
    selection = Color(0x3D0969DA),
    keyword = Color(0xFFCF222E),
    type = Color(0xFF953800),
    function = Color(0xFF8250DF),
    constant = Color(0xFF0550AE),
    number = Color(0xFF0550AE),
    string = Color(0xFF0A3069),
    comment = Color(0xFF6E7781)
)

val LocalEditorColors = staticCompositionLocalOf { DefaultDarkEditorColors }
