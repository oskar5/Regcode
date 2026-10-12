package com.codeeditor.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary = SeedDark,
    secondary = AccentTeal
)

private val LightColors = lightColorScheme(
    primary = SeedLight,
    secondary = AccentTeal
)

@Composable
fun CodeEditorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    editorSchemeId: String = "default",
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    val editorColors = EditorSchemes.resolve(editorSchemeId, darkTheme)

    CompositionLocalProvider(LocalEditorColors provides editorColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
