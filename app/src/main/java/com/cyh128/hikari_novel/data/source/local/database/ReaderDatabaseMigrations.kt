package com.cyh128.hikari_novel.data.source.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit, non-destructive reader database migrations.
 *
 * Reader progress/cache/download rows were originally keyed by cid only. The
 * v3 reader schema uses (aid, cid) so two books cannot overwrite one another.
 * The old rows are copied before each original table is replaced.
 */
object ReaderDatabaseMigrations {
    val READER_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS download_book (
                    aid TEXT NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL,
                    imageUrl TEXT,
                    totalChapters INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    val READER_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            recreateChapterCache(db)
            recreateReaderProgress(db)
            recreateDownloadTasks(db)
        }
    }

    val HORIZONTAL_HISTORY_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            recreateHistoryTable(db, "horizontal_read_history")
        }
    }

    val VERTICAL_HISTORY_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            recreateHistoryTable(db, "vertical_read_history")
        }
    }

    private fun recreateChapterCache(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS chapter_cache_new (
                cid TEXT NOT NULL,
                aid TEXT NOT NULL,
                volumeIndex INTEGER NOT NULL,
                chapterIndex INTEGER NOT NULL,
                title TEXT NOT NULL,
                content TEXT NOT NULL,
                imagesJson TEXT NOT NULL,
                updatedAt INTEGER NOT NULL,
                isDownloaded INTEGER NOT NULL,
                sizeBytes INTEGER NOT NULL,
                PRIMARY KEY(aid, cid)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO chapter_cache_new
                (cid, aid, volumeIndex, chapterIndex, title, content, imagesJson,
                 updatedAt, isDownloaded, sizeBytes)
            SELECT cid, aid, volumeIndex, chapterIndex, title, content, imagesJson,
                   updatedAt, isDownloaded, sizeBytes
            FROM chapter_cache
            """.trimIndent()
        )
        db.execSQL("DROP TABLE chapter_cache")
        db.execSQL("ALTER TABLE chapter_cache_new RENAME TO chapter_cache")
    }

    private fun recreateReaderProgress(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS reader_progress_new (
                cid TEXT NOT NULL,
                aid TEXT NOT NULL,
                volumeIndex INTEGER NOT NULL,
                chapterIndex INTEGER NOT NULL,
                paragraphIndex INTEGER NOT NULL,
                charOffset INTEGER NOT NULL,
                normalizedPercent INTEGER NOT NULL,
                imageIndex INTEGER,
                updatedAt INTEGER NOT NULL,
                isLatest INTEGER NOT NULL,
                PRIMARY KEY(aid, cid)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO reader_progress_new
                (cid, aid, volumeIndex, chapterIndex, paragraphIndex, charOffset,
                 normalizedPercent, imageIndex, updatedAt, isLatest)
            SELECT cid, aid, volumeIndex, chapterIndex, paragraphIndex, charOffset,
                   normalizedPercent, imageIndex, updatedAt, isLatest
            FROM reader_progress
            """.trimIndent()
        )
        db.execSQL("DROP TABLE reader_progress")
        db.execSQL("ALTER TABLE reader_progress_new RENAME TO reader_progress")
    }

    private fun recreateDownloadTasks(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS download_task_new (
                cid TEXT NOT NULL,
                aid TEXT NOT NULL,
                volumeIndex INTEGER NOT NULL,
                chapterIndex INTEGER NOT NULL,
                title TEXT NOT NULL,
                status TEXT NOT NULL,
                progress INTEGER NOT NULL,
                error TEXT,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(aid, cid)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO download_task_new
                (cid, aid, volumeIndex, chapterIndex, title, status, progress, error, updatedAt)
            SELECT cid, aid, volumeIndex, chapterIndex, title, status, progress, error, updatedAt
            FROM download_task
            """.trimIndent()
        )
        db.execSQL("DROP TABLE download_task")
        db.execSQL("ALTER TABLE download_task_new RENAME TO download_task")
    }

    private fun recreateHistoryTable(db: SupportSQLiteDatabase, table: String) {
        val temporary = table + "_new"
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $temporary (
                cid TEXT NOT NULL,
                aid TEXT NOT NULL,
                volume INTEGER NOT NULL,
                chapter INTEGER NOT NULL,
                location INTEGER NOT NULL,
                progress_percent INTEGER NOT NULL,
                is_latest INTEGER NOT NULL,
                PRIMARY KEY(aid, cid)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO $temporary
                (cid, aid, volume, chapter, location, progress_percent, is_latest)
            SELECT cid, aid, volume, chapter, location, progress_percent, is_latest
            FROM $table
            """.trimIndent()
        )
        db.execSQL("DROP TABLE $table")
        db.execSQL("ALTER TABLE $temporary RENAME TO $table")
    }
}
