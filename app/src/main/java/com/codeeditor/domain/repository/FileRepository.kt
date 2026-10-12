package com.codeeditor.domain.repository

import com.codeeditor.core.editor.LineEnding
import com.codeeditor.core.encoding.TextEncoding
import com.codeeditor.domain.model.FileNode
import com.codeeditor.domain.model.LoadedDocument

/** Reads and writes documents through the Storage Access Framework. */
interface FileRepository {

    suspend fun readDocument(uri: String): LoadedDocument

    suspend fun writeDocument(
        uri: String,
        text: String,
        encoding: TextEncoding,
        lineEnding: LineEnding,
        withBom: Boolean
    )

    /** Lists the direct children of a directory (tree or single-document URI). */
    suspend fun listChildren(uri: String): List<FileNode>

    /** Persists the read/write grant for a URI so it survives process death. */
    suspend fun takePersistablePermission(uri: String)

    /** Human readable display name for a URI, without reading the file. */
    fun displayName(uri: String): String
}
