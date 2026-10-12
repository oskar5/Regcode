package com.codeeditor.domain.usecase

import com.codeeditor.domain.model.LoadedDocument
import com.codeeditor.domain.repository.FileRepository
import com.codeeditor.domain.repository.SessionRepository
import javax.inject.Inject

/** Reads a document and records it in the recent-files history. */
class OpenDocumentUseCase @Inject constructor(
    private val fileRepository: FileRepository,
    private val sessionRepository: SessionRepository
) {
    suspend operator fun invoke(uri: String): LoadedDocument {
        fileRepository.takePersistablePermission(uri)
        val document = fileRepository.readDocument(uri)
        sessionRepository.addRecent(uri, document.name)
        return document
    }
}
