package com.cyh128.hikari_novel.data.source.local.database.reader

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ChapterCacheEntity::class, ReaderProgressEntity::class, DownloadTaskEntity::class],
    version = 1,
    exportSchema = true
)
abstract class ReaderDatabase : RoomDatabase() {
    abstract fun readerDao(): ReaderDao
}
