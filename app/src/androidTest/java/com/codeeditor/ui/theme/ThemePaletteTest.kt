package com.codeeditor.ui.theme

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemePaletteTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun palettes_are_registered_with_expected_families() {
        val schemes = EditorSchemes.schemes
        assertTrue(schemes.any { it.id == "default" && it.hasDark && it.hasLight })
        assertTrue(schemes.any { it.id == "dracula" && it.hasDark && !it.hasLight })
        assertTrue(schemes.any { it.id == "one-dark" && it.hasDark && !it.hasLight })
        assertTrue(schemes.any { it.id == "solarized" && it.hasDark && it.hasLight })
        assertTrue(schemes.any { it.id == "github" && !it.hasDark && it.hasLight })
    }

    @Test
    fun dracula_activates_in_dark_theme_only() {
        val dark = EditorSchemes.resolve("dracula", darkTheme = true)
        assertEquals(Color(0xFF282A36), dark.background)

        val light = EditorSchemes.resolve("dracula", darkTheme = false)
        assertEquals(DefaultLightEditorColors, light)
    }

    @Test
    fun github_activates_in_light_theme_only() {
        val light = EditorSchemes.resolve("github", darkTheme = false)
        assertEquals(Color(0xFFFFFFFF), light.background)

        val dark = EditorSchemes.resolve("github", darkTheme = true)
        assertEquals(DefaultDarkEditorColors, dark)
    }

    @Test
    fun unknown_scheme_id_falls_back_to_default() {
        val dark = EditorSchemes.resolve("does-not-exist", darkTheme = true)
        assertEquals(DefaultDarkEditorColors.background, dark.background)
    }

    @Test
    fun theme_provides_editor_colors_through_composition_local() {
        composeRule.setContent {
            CodeEditorTheme(darkTheme = true, editorSchemeId = "dracula") {
                SchemeProbe()
            }
        }
        composeRule.onNodeWithText("282A36").assertExists()
    }
}

@Composable
private fun SchemeProbe() {
    val colors = LocalEditorColors.current
    Text(text = argb(colors.background))
}

private fun argb(color: Color): String = "%06X".format(color.toArgb() and 0x00FFFFFF)