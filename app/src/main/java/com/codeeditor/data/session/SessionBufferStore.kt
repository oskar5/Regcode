package com.codeeditor.data.session

import android.content.Context
import com.codeeditor.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Stores the in-memory text of dirty editor buffers as app-private files, so the
 * Room session table only keeps lightweight metadata instead of file contents.
 *
 * The file name is the SHA-256 of the document URI, which keeps it stable across
 * runs while avoiding unsafe characters from arbitrary content-provider URIs.
 */
@Singleton
class SessionBufferStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher
) {

    /** Replaces the whole buffer set, deleting files for closed/clean tabs. */
    suspend fun replaceAll(contents: Map<String, String>) = withContext(io) {
        ensureDir()
        val keep = contents.keys.mapTo(HashSet()) { fileFor(it).name }
        dir.listFiles()?.forEach { if (it.name !in keep) it.delete() }
        contents.forEach { (uri, content) -> fileFor(uri).writeText(content) }
    }

    suspend fun write(uri: String, content: String) = withContext(io) {
        ensureDir()
        fileFor(uri).writeText(content)
    }

    suspend fun read(uri: String): String = withContext(io) {
        val file = fileFor(uri)
        if (file.exists()) runCatching { file.readText() }.getOrDefault("") else ""
    }

    suspend fun delete(uri: String) = withContext(io) {
        fileFor(uri).delete()
        Unit
    }

    private val dir: File
        get() = File(context.filesDir, BUFFER_DIR)

    private fun ensureDir() {
        if (!dir.exists()) dir.mkdirs()
    }

    private fun fileFor(uri: String): File = File(dir, sha256(uri))

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val BUFFER_DIR = "session-buffers"
    }
}
