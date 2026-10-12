package com.codeeditor.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "session_documents")
data class SessionDocumentEntity(
    @PrimaryKey val uri: String,
    val title: String,
    val languageId: String,
    /** True when the dirty buffer text lives in [com.codeeditor.data.session.SessionBufferStore]. */
    val hasBufferedContent: Boolean,
    val cursorLine: Int,
    val cursorColumn: Int,
    val isDirty: Boolean,
    val orderIndex: Int
)

@Entity(tableName = "recent_files")
data class RecentFileEntity(
    @PrimaryKey val uri: String,
    val title: String,
    val lastOpenedAt: Long
)

@Dao
interface SessionDao {

    @Query("SELECT * FROM session_documents ORDER BY orderIndex ASC")
    suspend fun loadSession(): List<SessionDocumentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: SessionDocumentEntity)

    @Upsert
    suspend fun upsertDocument(document: SessionDocumentEntity)

    @Query("DELETE FROM session_documents")
    suspend fun clearSession()

    @Query("DELETE FROM session_documents WHERE uri = :uri")
    suspend fun deleteDocument(uri: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecent(recent: RecentFileEntity)

    @Query("SELECT * FROM recent_files ORDER BY lastOpenedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RecentFileEntity>>

    @Query("DELETE FROM recent_files WHERE uri = :uri")
    suspend fun deleteRecent(uri: String)
}

@Database(
    entities = [SessionDocumentEntity::class, RecentFileEntity::class],
    version = 2,
    exportSchema = false
)
abstract class SessionDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
}
