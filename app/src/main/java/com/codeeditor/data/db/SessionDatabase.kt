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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

    companion object {
        /**
         * v1 → v2: session buffers moved out of Room into
         * [com.codeeditor.data.session.SessionBufferStore]. The old `content`
         * column is dropped (replaced by `hasBufferedContent`) via a table
         * rebuild, because SQLite before 3.35 (API < 33) cannot DROP COLUMN.
         * The tab list and recent files are preserved; previously dirty text is
         * re-read from the source file on the next open.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `session_documents_new` (
                        `uri` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `languageId` TEXT NOT NULL,
                        `hasBufferedContent` INTEGER NOT NULL,
                        `cursorLine` INTEGER NOT NULL,
                        `cursorColumn` INTEGER NOT NULL,
                        `isDirty` INTEGER NOT NULL,
                        `orderIndex` INTEGER NOT NULL,
                        PRIMARY KEY(`uri`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `session_documents_new` (
                        `uri`, `title`, `languageId`, `cursorLine`, `cursorColumn`,
                        `isDirty`, `orderIndex`, `hasBufferedContent`
                    )
                    SELECT `uri`, `title`, `languageId`, `cursorLine`, `cursorColumn`,
                           `isDirty`, `orderIndex`, 0
                    FROM `session_documents`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `session_documents`")
                db.execSQL("ALTER TABLE `session_documents_new` RENAME TO `session_documents`")
            }
        }
    }
}
