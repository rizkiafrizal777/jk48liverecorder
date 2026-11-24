package com.example.idnliverecorder.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        RecordingBookmark::class,
        RecordingFavorite::class,
        ExportTask::class,
        RecordingMetadataCache::class
    ],
    version = 1,
    exportSchema = false
)
abstract class IDNRecorderDatabase : RoomDatabase() {

    abstract fun bookmarkDao(): BookmarkDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun exportTaskDao(): ExportTaskDao
    abstract fun metadataCacheDao(): MetadataCacheDao

    companion object {
        @Volatile
        private var INSTANCE: IDNRecorderDatabase? = null

        fun getInstance(context: Context): IDNRecorderDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    IDNRecorderDatabase::class.java,
                    "idn_recorder_db.db"
                ).fallbackToDestructiveMigration()
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

