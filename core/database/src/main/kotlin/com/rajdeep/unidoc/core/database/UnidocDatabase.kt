package com.rajdeep.unidoc.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(entities = [DocumentEntity::class, FolderEntity::class, PageTextFts::class], version = 1, exportSchema = true)
abstract class UnidocDatabase : RoomDatabase() {
    abstract fun library(): LibraryDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun providesDatabase(@ApplicationContext context: Context): UnidocDatabase =
        Room.databaseBuilder(context, UnidocDatabase::class.java, "unidoc.db").build()

    @Provides
    fun providesLibraryDao(db: UnidocDatabase): LibraryDao = db.library()
}
