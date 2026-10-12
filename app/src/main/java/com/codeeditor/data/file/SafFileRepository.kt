package com.codeeditor.data.file

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.codeeditor.core.editor.LineEnding
import com.codeeditor.core.encoding.TextCodec
import com.codeeditor.core.encoding.TextEncoding
import com.codeeditor.di.IoDispatcher
import com.codeeditor.domain.model.FileNode
import com.codeeditor.domain.model.LoadedDocument
import com.codeeditor.domain.repository.FileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.URLDecoder
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** [FileRepository] backed by the Storage Access Framework. */
class SafFileRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher
) : FileRepository {

    override suspend fun readDocument(uri: String): LoadedDocument = withContext(io) {
        val u = Uri.parse(uri)
        val bytes = context.contentResolver.openInputStream(u)
            ?.use { it.readBytes() }
            ?: error("Cannot open $uri")
        val decoded = TextCodec.decode(bytes)
        LoadedDocument(
            name = displayName(uri),
            text = decoded.text,
            encoding = decoded.encoding,
            hasBom = decoded.hasBom,
            lineEnding = decoded.lineEnding,
            encodingGuessed = decoded.usedFallback
        )
    }

    override suspend fun writeDocument(
        uri: String,
        text: String,
        encoding: TextEncoding,
        lineEnding: LineEnding,
        withBom: Boolean
    ) = withContext(io) {
        val u = Uri.parse(uri)
        val bytes = TextCodec.encode(text, encoding, lineEnding, withBom)
        context.contentResolver.openOutputStream(u, "wt")
            ?.use { it.write(bytes) }
            ?: error("Cannot write $uri")
    }

    override suspend fun listChildren(uri: String): List<FileNode> = withContext(io) {
        val u = Uri.parse(uri)
        val root = DocumentFile.fromTreeUri(context, u)
            ?: DocumentFile.fromSingleUri(context, u)
            ?: return@withContext emptyList()
        root.listFiles()
            .map { child ->
                FileNode(
                    uri = child.uri.toString(),
                    name = child.name ?: "(unnamed)",
                    isDirectory = child.isDirectory,
                    mimeType = child.type
                )
            }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    override suspend fun takePersistablePermission(uri: String) {
        withContext(io) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(Uri.parse(uri), flags)
            } catch (_: SecurityException) {
                // URI was not offered with persistable grant; ignore.
            }
        }
    }

    override fun displayName(uri: String): String {
        val tail = uri.substringAfterLast('/')
        return runCatching { URLDecoder.decode(tail, "UTF-8") }.getOrDefault(tail)
            .ifBlank { File(uri).name.ifBlank { "file" } }
    }
}
