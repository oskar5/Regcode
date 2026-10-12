package com.codeeditor.domain.usecase

import com.codeeditor.core.session.EditorSession
import com.codeeditor.domain.repository.FileRepository
import javax.inject.Inject

/** Writes the current buffer of [session] back to its URI using its encoding. */
class SaveDocumentUseCase @Inject constructor(
    private val fileRepository: FileRepository
) {
    suspend operator fun invoke(session: EditorSession) {
        val uri = session.uri ?: return
        fileRepository.writeDocument(
            uri = uri,
            text = session.content,
            encoding = session.encoding,
            lineEnding = session.lineEnding,
            withBom = session.hasBom
        )
        session.markSaved()
    }
}
