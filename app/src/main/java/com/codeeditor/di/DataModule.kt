package com.codeeditor.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.codeeditor.data.db.SessionDao
import com.codeeditor.data.db.SessionDatabase
import com.codeeditor.data.file.SafFileRepository
import com.codeeditor.data.session.RoomSessionRepository
import com.codeeditor.data.settings.DataStoreSettingsRepository
import com.codeeditor.domain.repository.FileRepository
import com.codeeditor.domain.repository.SessionRepository
import com.codeeditor.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SessionDatabase =
        Room.databaseBuilder(context, SessionDatabase::class.java, "codeeditor-session.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideSessionDao(database: SessionDatabase): SessionDao = database.sessionDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindFileRepository(impl: SafFileRepository): FileRepository

    @Binds
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    abstract fun bindSessionRepository(impl: RoomSessionRepository): SessionRepository
}
