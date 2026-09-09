package com.cyh128.hikari_novel.data.repository.reader

import com.cyh128.hikari_novel.data.source.local.database.reader.ChapterCacheEntity
import com.cyh128.hikari_novel.data.source.local.database.reader.DownloadBookEntity
import com.cyh128.hikari_novel.data.source.local.database.reader.DownloadTaskEntity
import com.cyh128.hikari_novel.data.source.local.database.reader.ReaderDao
import com.cyh128.hikari_novel.data.source.local.database.reader.ReaderProgressEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class FakeReaderDao : ReaderDao {
    val chapters = linkedMapOf<String, ChapterCacheEntity>()
    var progress: ReaderProgressEntity? = null

    override suspend fun getChapter(cid: String): ChapterCacheEntity? = chapters[cid]

    override suspend fun upsertChapter(entity: ChapterCacheEntity) {
        chapters[entity.cid] = entity
    }

    override suspend fun getProgress(cid: String): ReaderProgressEntity? = progress?.takeIf { it.cid == cid }

    override fun observeProgress(aid: String): Flow<List<ReaderProgressEntity>> = emptyFlow()

    override suspend fun upsertProgress(entity: ReaderProgressEntity) {
        progress = entity
    }

    override suspend fun upsertLatestProgress(entity: ReaderProgressEntity) {
        progress = entity
    }

    override suspend fun clearLatest(aid: String) = Unit

    override fun observeDownloads(aid: String): Flow<List<DownloadTaskEntity>> = emptyFlow()

    override fun observeDownloadBooks(): Flow<List<DownloadBookEntity>> = emptyFlow()

    override suspend fun upsertDownloadBook(entity: DownloadBookEntity) = Unit

    override suspend fun deleteDownloadBook(aid: String) = Unit

    override suspend fun deleteDownloadTasks(aid: String) = Unit

    override suspend fun updateDownloadStatus(aid: String, status: String, updatedAt: Long) = Unit

    override suspend fun updateChapterStatus(cid: String, status: String, updatedAt: Long) = Unit

    override suspend fun getChapterTaskAid(cid: String): String? = null

    override suspend fun deleteDownloadTask(cid: String) = Unit

    override suspend fun deleteChapterCache(cid: String) {
        chapters.remove(cid)
    }

    override suspend fun getDownloadStatus(cid: String): String? = null

    override suspend fun upsertDownload(entity: DownloadTaskEntity) = Unit

    override suspend fun pendingDownloads(aid: String): List<DownloadTaskEntity> = emptyList()

    override suspend fun pendingAids(): List<String> = emptyList()

    override suspend fun resetInterruptedDownloads(updatedAt: Long) = Unit

    override suspend fun markDownloaded(cid: String, downloaded: Boolean) {
        chapters[cid]?.let { chapters[cid] = it.copy(isDownloaded = downloaded) }
    }

    override suspend fun deleteExpiredCache(before: Long) {
        chapters.entries.removeIf { !it.value.isDownloaded && it.value.updatedAt < before }
    }
}
