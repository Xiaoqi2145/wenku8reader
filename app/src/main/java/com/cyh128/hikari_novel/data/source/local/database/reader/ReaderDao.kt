package com.cyh128.hikari_novel.data.source.local.database.reader

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ReaderDao {
    /** Legacy cid-only cache lookup; use the book-scoped overload for reader code. */
    @Deprecated("Use getChapter(aid, cid) to validate the book")
    @Query("SELECT * FROM chapter_cache WHERE cid = :cid ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getChapter(cid: String): ChapterCacheEntity?

    @Query("SELECT * FROM chapter_cache WHERE aid = :aid AND cid = :cid LIMIT 1")
    suspend fun getChapter(aid: String, cid: String): ChapterCacheEntity?

    @Upsert
    suspend fun upsertChapter(entity: ChapterCacheEntity)

    @Query("SELECT * FROM reader_progress WHERE cid = :cid ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getProgress(cid: String): ReaderProgressEntity?

    @Query("SELECT * FROM reader_progress WHERE aid = :aid AND cid = :cid LIMIT 1")
    suspend fun getProgressForBook(aid: String, cid: String): ReaderProgressEntity?

    @Query("SELECT * FROM reader_progress WHERE aid = :aid ORDER BY updatedAt DESC")
    fun observeProgress(aid: String): Flow<List<ReaderProgressEntity>>

    @Upsert
    suspend fun upsertProgress(entity: ReaderProgressEntity)

    /** Replace the latest marker and write the anchor atomically. */
    @androidx.room.Transaction
    suspend fun upsertLatestProgress(entity: ReaderProgressEntity) {
        clearLatest(entity.aid)
        upsertProgress(entity)
    }

    @Query("UPDATE reader_progress SET isLatest = 0 WHERE aid = :aid")
    suspend fun clearLatest(aid: String)

    @Query("SELECT * FROM download_task WHERE aid = :aid ORDER BY updatedAt DESC")
    fun observeDownloads(aid: String): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_book ORDER BY updatedAt DESC")
    fun observeDownloadBooks(): Flow<List<DownloadBookEntity>>

    @Upsert
    suspend fun upsertDownloadBook(entity: DownloadBookEntity)

    @Query("DELETE FROM download_book WHERE aid = :aid")
    suspend fun deleteDownloadBook(aid: String)

    @Query("DELETE FROM download_task WHERE aid = :aid")
    suspend fun deleteDownloadTasks(aid: String)

    @Query("UPDATE download_task SET status = :status, updatedAt = :updatedAt WHERE aid = :aid AND status IN ('QUEUED','RUNNING','PAUSED','FAILED')")
    suspend fun updateDownloadStatus(aid: String, status: String, updatedAt: Long)

    @Query("UPDATE download_task SET status = :status, error = NULL, updatedAt = :updatedAt WHERE cid = :cid")
    suspend fun updateChapterStatus(cid: String, status: String, updatedAt: Long)



    @Query("SELECT aid FROM download_task WHERE cid = :cid LIMIT 1")
    suspend fun getChapterTaskAid(cid: String): String?

    @Query("SELECT aid FROM download_task WHERE aid = :aid AND cid = :cid LIMIT 1")
    suspend fun getChapterTaskAid(aid: String, cid: String): String?

    @Query("DELETE FROM download_task WHERE aid = :aid AND cid = :cid")
    suspend fun deleteDownloadTask(aid: String, cid: String)

    @Query("UPDATE download_task SET status = :status, error = NULL, updatedAt = :updatedAt WHERE aid = :aid AND cid = :cid")
    suspend fun updateChapterStatus(aid: String, cid: String, status: String, updatedAt: Long)

    @Query("DELETE FROM download_task WHERE cid = :cid")
    suspend fun deleteDownloadTask(cid: String)



    @Query("DELETE FROM chapter_cache WHERE cid = :cid")
    suspend fun deleteChapterCache(cid: String)

    @Query("DELETE FROM chapter_cache WHERE aid = :aid AND cid = :cid")
    suspend fun deleteChapterCache(aid: String, cid: String)

    @Query("SELECT status FROM download_task WHERE cid = :cid LIMIT 1")
    suspend fun getDownloadStatus(cid: String): String?

    @Query("SELECT status FROM download_task WHERE aid = :aid AND cid = :cid LIMIT 1")
    suspend fun getDownloadStatus(aid: String, cid: String): String?

    @Upsert
    suspend fun upsertDownload(entity: DownloadTaskEntity)

    @Query("SELECT * FROM download_task WHERE aid = :aid AND status IN ('QUEUED', 'RUNNING', 'FAILED') ORDER BY volumeIndex, chapterIndex")
    suspend fun pendingDownloads(aid: String): List<DownloadTaskEntity>

    @Query("SELECT DISTINCT aid FROM download_task WHERE status IN ('QUEUED', 'RUNNING', 'FAILED')")
    suspend fun pendingAids(): List<String>

    @Query("UPDATE download_task SET status = 'QUEUED', error = NULL, updatedAt = :updatedAt WHERE status = 'RUNNING'")
    suspend fun resetInterruptedDownloads(updatedAt: Long)

    @Query("UPDATE chapter_cache SET isDownloaded = :downloaded WHERE cid = :cid")
    suspend fun markDownloaded(cid: String, downloaded: Boolean)

    @Query("UPDATE chapter_cache SET isDownloaded = :downloaded WHERE aid = :aid AND cid = :cid")
    suspend fun markDownloaded(aid: String, cid: String, downloaded: Boolean)

    @Query("DELETE FROM chapter_cache WHERE isDownloaded = 0 AND updatedAt < :before")
    suspend fun deleteExpiredCache(before: Long)

    // ===== P1-4: Full-book search =====

    /** Every cached chapter body for a book, in a single query. */
    @Query("SELECT cid, content FROM chapter_cache WHERE aid = :aid")
    suspend fun cachedChapterContents(aid: String): List<CachedChapterContent>

    // ===== P1-3: Bookmarks =====

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Query("SELECT * FROM bookmark WHERE aid = :aid ORDER BY createdAt DESC")
    fun observeBookmarks(aid: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmark WHERE aid = :aid AND cid = :cid LIMIT 1")
    suspend fun getBookmark(aid: String, cid: String): BookmarkEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM bookmark WHERE aid = :aid AND cid = :cid)")
    suspend fun hasBookmark(aid: String, cid: String): Boolean

    @Query("DELETE FROM bookmark WHERE id = :id")
    suspend fun deleteBookmark(id: Long)

    @Query("DELETE FROM bookmark WHERE aid = :aid AND cid = :cid")
    suspend fun deleteBookmarkByCid(aid: String, cid: String)
}
