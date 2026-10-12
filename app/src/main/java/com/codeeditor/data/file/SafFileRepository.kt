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
import com.codeeditor.domain.model.FileTooLargeException
import com.codeeditor.domain.model.LoadedDocument
import com.codeeditor.domain.repository.FileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
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
        val size = runCatching {
            context.contentResolver.openFileDescriptor(u, "r")?.use { it.statSize }
        }.getOrNull() ?: -1L
        if (size > MAX_DOCUMENT_BYTES) throw FileTooLargeException(MAX_DOCUMENT_BYTES)
        val bytes = readBytesLimited(u, MAX_DOCUMENT_BYTES)
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

    /** Reads a stream but aborts with [FileTooLargeException] past [limit] bytes. */
    private fun readBytesLimited(uri: Uri, limit: Long): ByteArray {
        val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open $uri")
        input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > limit) throw FileTooLargeException(limit)
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }
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

    private companion object {
        /** Refuse to open files larger than this to protect device memory. */
        const val MAX_DOCUMENT_BYTES = 8L * 1024 * 1024
    }
}
