package com.codeeditor.domain.repository

import com.codeeditor.domain.model.RecentFile
import com.codeeditor.domain.model.SessionDocument
import kotlinx.coroutines.flow.Flow

/** Persists the open-tab session and the recently opened files list. */
interface SessionRepository {

    suspend fun loadSession(): List<SessionDocument>

    /** Atomically replaces the whole open-tab list. */
    suspend fun saveSession(documents: List<SessionDocument>)

    suspend fun upsert(document: SessionDocument)

    suspend fun remove(uri: String)

    suspend fun addRecent(uri: String, title: String)

    fun observeRecent(limit: Int = 20): Flow<List<RecentFile>>
}
