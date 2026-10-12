package com.codeeditor.data.session

import com.codeeditor.data.db.RecentFileEntity
import com.codeeditor.data.db.SessionDao
import com.codeeditor.data.db.SessionDocumentEntity
import com.codeeditor.di.IoDispatcher
import com.codeeditor.domain.model.RecentFile
import com.codeeditor.domain.model.SessionDocument
import com.codeeditor.domain.repository.SessionRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RoomSessionRepository @Inject constructor(
    private val dao: SessionDao,
    @IoDispatcher private val io: CoroutineDispatcher
) : SessionRepository {

    override suspend fun loadSession(): List<SessionDocument> = withContext(io) {
        dao.loadSession().map { it.toDomain() }
    }

    override suspend fun saveSession(documents: List<SessionDocument>) = withContext(io) {
        dao.clearSession()
        documents.forEachIndexed { index, doc ->
            dao.insertDocument(doc.copy(orderIndex = index).toEntity())
        }
    }

    override suspend fun upsert(document: SessionDocument) = withContext(io) {
        dao.upsertDocument(document.toEntity())
    }

    override suspend fun remove(uri: String) = withContext(io) {
        dao.deleteDocument(uri)
    }

    override suspend fun addRecent(uri: String, title: String) = withContext(io) {
        dao.insertRecent(RecentFileEntity(uri, title, System.currentTimeMillis()))
    }

    override fun observeRecent(limit: Int): Flow<List<RecentFile>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    private fun SessionDocumentEntity.toDomain() = SessionDocument(
        uri = uri,
        title = title,
        languageId = languageId,
        content = content,
        cursorLine = cursorLine,
        cursorColumn = cursorColumn,
        isDirty = isDirty,
        orderIndex = orderIndex
    )

    private fun SessionDocument.toEntity() = SessionDocumentEntity(
        uri = uri,
        title = title,
        languageId = languageId,
        content = content,
        cursorLine = cursorLine,
        cursorColumn = cursorColumn,
        isDirty = isDirty,
        orderIndex = orderIndex
    )

    private fun RecentFileEntity.toDomain() = RecentFile(uri, title, lastOpenedAt)
}
