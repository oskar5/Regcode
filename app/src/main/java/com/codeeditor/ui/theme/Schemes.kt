package com.codeeditor.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Registry of named editor colour schemes. Each scheme may define a dark
 * and/or a light variant; `resolve` picks the one matching the app theme and
 * falls back gracefully when a scheme does not support the active theme.
 */
object EditorSchemes {

    data class SchemeInfo(
        val id: String,
        val displayName: String,
        val hasDark: Boolean,
        val hasLight: Boolean
    )

    private val registry = listOf(
        Scheme("default", "Default", DefaultDarkEditorColors, DefaultLightEditorColors),
        Scheme("dracula", "Dracula", DraculaEditorColors, null),
        Scheme("one-dark", "One Dark", OneDarkEditorColors, null),
        Scheme("solarized", "Solarized", SolarizedDarkEditorColors, SolarizedLightEditorColors),
        Scheme("github", "GitHub", null, GitHubEditorColors)
    )

    val schemes: List<SchemeInfo>
        get() = registry.map {
            SchemeInfo(it.id, it.displayName, it.dark != null, it.light != null)
        }

    fun available(forDark: Boolean): List<SchemeInfo> =
        schemes.filter { if (forDark) it.hasDark else it.hasLight }

    fun resolve(id: String, darkTheme: Boolean): EditorColors {
        val scheme = registry.firstOrNull { it.id == id } ?: registry.first()
        return when {
            darkTheme -> scheme.dark ?: scheme.light ?: DefaultDarkEditorColors
            else -> scheme.light ?: scheme.dark ?: DefaultLightEditorColors
        }
    }

    private data class Scheme(
        val id: String,
        val displayName: String,
        val dark: EditorColors?,
        val light: EditorColors?
    )
}

// ------------------------------------------------------------------ palettes

val DraculaEditorColors = EditorColors(
    background = Color(0xFF282A36),
    foreground = Color(0xFFF8F8F2),
    gutterBackground = Color(0xFF21222C),
    gutterForeground = Color(0xFF6272A4),
    gutterActiveForeground = Color(0xFFF8F8F2),
    currentLineBackground = Color(0x14FFFFFF),
    caret = Color(0xFFF8F8F2),
    selection = Color(0xFF44475A),
    keyword = Color(0xFFFF79C6),
    type = Color(0xFF8BE9FD),
    function = Color(0xFF50FA7B),
    constant = Color(0xFFFFB86C),
    number = Color(0xFFBD93F9),
    string = Color(0xFFF1FA8C),
    comment = Color(0xFF6272A4)
)

val OneDarkEditorColors = EditorColors(
    background = Color(0xFF282C34),
    foreground = Color(0xFFABB2BF),
    gutterBackground = Color(0xFF21252B),
    gutterForeground = Color(0xFF495162),
    gutterActiveForeground = Color(0xFFABB2BF),
    currentLineBackground = Color(0x14222C3A),
    caret = Color(0xFF528BFF),
    selection = Color(0xFF3E4451),
    keyword = Color(0xFFC678DD),
    type = Color(0xFFE5C07B),
    function = Color(0xFF61AFEF),
    constant = Color(0xFF56B6C2),
    number = Color(0xFFD19A66),
    string = Color(0xFF98C379),
    comment = Color(0xFF5C6370)
)

val SolarizedDarkEditorColors = EditorColors(
    background = Color(0xFF002B36),
    foreground = Color(0xFF839496),
    gutterBackground = Color(0xFF002731),
    gutterForeground = Color(0xFF586E75),
    gutterActiveForeground = Color(0xFF93A1A1),
    currentLineBackground = Color(0xFF073642),
    caret = Color(0xFF93A1A1),
    selection = Color(0x33586575),
    keyword = Color(0xFF859900),
    type = Color(0xFFB58900),
    function = Color(0xFF268BD2),
    constant = Color(0xFFCB4B16),
    number = Color(0xFF2AA198),
    string = Color(0xFF2AA198),
    comment = Color(0xFF586E75)
)

val SolarizedLightEditorColors = EditorColors(
    background = Color(0xFFFDF6E3),
    foreground = Color(0xFF657B83),
    gutterBackground = Color(0xFFF5EEDC),
    gutterForeground = Color(0xFF93A1A1),
    gutterActiveForeground = Color(0xFF586E75),
    currentLineBackground = Color(0xFFEEE8D5),
    caret = Color(0xFF586E75),
    selection = Color(0x33586575),
    keyword = Color(0xFF859900),
    type = Color(0xFFB58900),
    function = Color(0xFF268BD2),
    constant = Color(0xFFCB4B16),
    number = Color(0xFF2AA198),
    string = Color(0xFF2AA198),
    comment = Color(0xFF93A1A1)
)

val GitHubEditorColors = EditorColors(
    background = Color(0xFFFFFFFF),
    foreground = Color(0xFF24292F),
    gutterBackground = Color(0xFFF6F8FA),
    gutterForeground = Color(0xFF8C959F),
    gutterActiveForeground = Color(0xFF1F2328),
    currentLineBackground = Color(0xFFF6F8FA),
    caret = Color(0xFF0969DA),
    selection = Color(0x30C6E3FF),
    keyword = Color(0xFFD73A49),
    type = Color(0xFF6F42C1),
    function = Color(0xFF6F42C1),
    constant = Color(0xFFE36209),
    number = Color(0xFF005CC5),
    string = Color(0xFF032F62),
    comment = Color(0xFF6A737D)
)