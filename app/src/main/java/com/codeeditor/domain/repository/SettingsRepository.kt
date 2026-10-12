package com.codeeditor.domain.repository

import com.codeeditor.domain.model.EditorSettings
import kotlinx.coroutines.flow.Flow

/** Persisted user preferences (DataStore backed). */
interface SettingsRepository {
    val settings: Flow<EditorSettings>
    suspend fun update(transform: (EditorSettings) -> EditorSettings)
}
