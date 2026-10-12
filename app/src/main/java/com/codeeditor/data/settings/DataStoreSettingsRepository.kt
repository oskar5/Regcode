package com.codeeditor.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.codeeditor.domain.model.EditorSettings
import com.codeeditor.domain.model.ThemeMode
import com.codeeditor.domain.repository.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    override val settings: Flow<EditorSettings> = dataStore.data.map { it.toSettings() }

    override suspend fun update(transform: (EditorSettings) -> EditorSettings) {
        dataStore.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[KEY_THEME] = next.themeMode.name
            prefs[KEY_SCHEME] = next.editorSchemeId
            prefs[KEY_FONT_FAMILY] = next.fontFamilyId
            prefs[KEY_FONT_SIZE] = next.fontSizeSp
            prefs[KEY_LINE_NUMBERS] = next.showLineNumbers
            prefs[KEY_WORD_WRAP] = next.wordWrap
            prefs[KEY_TAB_WIDTH] = next.tabWidth
            prefs[KEY_AUTOSAVE] = next.autosave
            prefs[KEY_AUTO_CLOSE] = next.autoCloseBrackets
            prefs[KEY_KEY_ROW] = next.showKeyRow
            next.lastFolderUri?.let { prefs[KEY_LAST_FOLDER] = it } ?: prefs.remove(KEY_LAST_FOLDER)
            next.lastActiveUri?.let { prefs[KEY_LAST_ACTIVE] = it } ?: prefs.remove(KEY_LAST_ACTIVE)
        }
    }

    private fun Preferences.toSettings(): EditorSettings = EditorSettings(
        themeMode = this[KEY_THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM,
        editorSchemeId = this[KEY_SCHEME] ?: "default",
        fontFamilyId = this[KEY_FONT_FAMILY] ?: "monospace",
        fontSizeSp = this[KEY_FONT_SIZE] ?: 14f,
        showLineNumbers = this[KEY_LINE_NUMBERS] ?: true,
        wordWrap = this[KEY_WORD_WRAP] ?: false,
        tabWidth = this[KEY_TAB_WIDTH] ?: 4,
        autosave = this[KEY_AUTOSAVE] ?: true,
        autoCloseBrackets = this[KEY_AUTO_CLOSE] ?: true,
        showKeyRow = this[KEY_KEY_ROW] ?: true,
        lastFolderUri = this[KEY_LAST_FOLDER],
        lastActiveUri = this[KEY_LAST_ACTIVE]
    )

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_SCHEME = stringPreferencesKey("editor_scheme")
        val KEY_FONT_FAMILY = stringPreferencesKey("font_family")
        val KEY_FONT_SIZE = floatPreferencesKey("font_size_sp")
        val KEY_LINE_NUMBERS = booleanPreferencesKey("show_line_numbers")
        val KEY_WORD_WRAP = booleanPreferencesKey("word_wrap")
        val KEY_TAB_WIDTH = intPreferencesKey("tab_width")
        val KEY_AUTOSAVE = booleanPreferencesKey("autosave")
        val KEY_AUTO_CLOSE = booleanPreferencesKey("auto_close_brackets")
        val KEY_KEY_ROW = booleanPreferencesKey("show_key_row")
        val KEY_LAST_FOLDER = stringPreferencesKey("last_folder_uri")
        val KEY_LAST_ACTIVE = stringPreferencesKey("last_active_uri")
    }
}
