package com.codeeditor.ui.settings

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.codeeditor.domain.model.EditorSettings
import com.codeeditor.domain.model.ThemeMode
import com.codeeditor.ui.theme.DefaultDarkEditorColors
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun noopCallbacks() = SettingsCallbacks(
        onThemeMode = {},
        onScheme = {},
        onFontFamily = {},
        onFontSize = {},
        onToggleLineNumbers = {},
        onToggleWordWrap = {}
    )

    @Test
    fun scheme_options_are_listed_for_the_active_theme() {
        composeRule.setContent {
            SettingsContent(
                settings = EditorSettings(themeMode = ThemeMode.DARK),
                colors = DefaultDarkEditorColors,
                callbacks = noopCallbacks()
            )
        }
        composeRule.onNodeWithText("Dracula").assertExists()
        composeRule.onNodeWithText("One Dark").assertExists()
        composeRule.onNodeWithText("Solarized").assertExists()
        // GitHub is light-only, so it is hidden while a dark theme is active.
        composeRule.onNode(hasText("GitHub")).assertDoesNotExist()
    }

    @Test
    fun choosing_a_scheme_invokes_callback_with_its_id() {
        var selectedScheme: String? = null
        val callbacks = SettingsCallbacks(
            onThemeMode = {},
            onScheme = { selectedScheme = it },
            onFontFamily = {},
            onFontSize = {},
            onToggleLineNumbers = {},
            onToggleWordWrap = {}
        )
        composeRule.setContent {
            SettingsContent(
                settings = EditorSettings(),
                colors = DefaultDarkEditorColors,
                callbacks = callbacks
            )
        }
        composeRule.onNodeWithText("Dracula").performClick()
        composeRule.waitForIdle()
        assertEquals("dracula", selectedScheme)
    }

    @Test
    fun font_family_options_are_present() {
        composeRule.setContent {
            SettingsContent(
                settings = EditorSettings(),
                colors = DefaultDarkEditorColors,
                callbacks = noopCallbacks()
            )
        }
        composeRule.onNodeWithText("Monospace").assertExists()
        composeRule.onNodeWithText("Sans").assertExists()
        composeRule.onNodeWithText("Serif").assertExists()
    }
}