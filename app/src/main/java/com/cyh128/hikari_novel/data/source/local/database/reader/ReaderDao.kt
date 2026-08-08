package com.cyh128.hikari_novel.data.source.local.database.reader

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ReaderDao {
    @Query("SELECT * FROM chapter_cache WHERE cid = :cid LIMIT 1")
    suspend fun getChapter(cid: String): ChapterCacheEntity?

    @Upsert
    suspend fun upsertChapter(entity: ChapterCacheEntity)

    @Query("SELECT * FROM reader_progress WHERE cid = :cid LIMIT 1")
    suspend fun getProgress(cid: String): ReaderProgressEntity?

    @Query("SELECT * FROM reader_progress WHERE aid = :aid ORDER BY updatedAt DESC")
    fun observeProgress(aid: String): Flow<List<ReaderProgressEntity>>

    @Upsert
    suspend fun upsertProgress(entity: ReaderProgressEntity)

    @Query("UPDATE reader_progress SET isLatest = 0 WHERE aid = :aid")
    suspend fun clearLatest(aid: String)

    @Query("SELECT * FROM download_task WHERE aid = :aid ORDER BY updatedAt DESC")
    fun observeDownloads(aid: String): Flow<List<DownloadTaskEntity>>

    @Upsert
    suspend fun upsertDownload(entity: DownloadTaskEntity)

    @Query("SELECT * FROM download_task WHERE aid = :aid AND status IN ('QUEUED', 'FAILED') ORDER BY volumeIndex, chapterIndex")
    suspend fun pendingDownloads(aid: String): List<DownloadTaskEntity>

    @Query("SELECT DISTINCT aid FROM download_task WHERE status IN ('QUEUED', 'FAILED')")
    suspend fun pendingAids(): List<String>

    @Query("UPDATE chapter_cache SET isDownloaded = :downloaded WHERE cid = :cid")
    suspend fun markDownloaded(cid: String, downloaded: Boolean)

    @Query("DELETE FROM chapter_cache WHERE isDownloaded = 0 AND updatedAt < :before")
    suspend fun deleteExpiredCache(before: Long)
}
