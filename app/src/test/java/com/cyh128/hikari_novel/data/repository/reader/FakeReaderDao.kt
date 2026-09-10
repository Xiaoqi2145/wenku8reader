package com.cyh128.hikari_novel.data.repository.reader

import com.cyh128.hikari_novel.data.source.local.database.reader.ChapterCacheEntity
import com.cyh128.hikari_novel.data.source.local.database.reader.DownloadBookEntity
import com.cyh128.hikari_novel.data.source.local.database.reader.DownloadTaskEntity
import com.cyh128.hikari_novel.data.source.local.database.reader.ReaderDao
import com.cyh128.hikari_novel.data.source.local.database.reader.ReaderProgressEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class FakeReaderDao : ReaderDao {
    var failProgressReads = false
    val chapters = linkedMapOf<String, ChapterCacheEntity>()
    private val progressRows = linkedMapOf<String, ReaderProgressEntity>()
    var progress: ReaderProgressEntity?
        get() = progressRows.values.maxByOrNull { it.updatedAt }
        set(value) {
            if (value == null) progressRows.clear()
            else progressRows[progressKey(value.aid, value.cid)] = value
        }

    override suspend fun getChapter(cid: String): ChapterCacheEntity? = chapters.values
        .filter { it.cid == cid }
        .maxByOrNull { it.updatedAt }

    override suspend fun getChapter(aid: String, cid: String): ChapterCacheEntity? =
        chapters.values.firstOrNull { it.aid == aid && it.cid == cid }

    override suspend fun upsertChapter(entity: ChapterCacheEntity) {
        chapters[progressKey(entity.aid, entity.cid)] = entity
    }

    override suspend fun getProgress(cid: String): ReaderProgressEntity? {
        check(!failProgressReads) { "reader progress read failed" }
        return progressRows.values
        .filter { it.cid == cid }
        .maxByOrNull { it.updatedAt }
    }

    override suspend fun getProgressForBook(aid: String, cid: String): ReaderProgressEntity? {
        check(!failProgressReads) { "reader progress read failed" }
        return progressRows[progressKey(aid, cid)]
    }

    override fun observeProgress(aid: String): Flow<List<ReaderProgressEntity>> = emptyFlow()

    override suspend fun upsertProgress(entity: ReaderProgressEntity) {
        progressRows[progressKey(entity.aid, entity.cid)] = entity
    }

    override suspend fun upsertLatestProgress(entity: ReaderProgressEntity) {
        clearLatest(entity.aid)
        upsertProgress(entity)
    }

    override suspend fun clearLatest(aid: String) {
        progressRows.replaceAll { _, value ->
            if (value.aid == aid) value.copy(isLatest = false) else value
        }
    }

    override fun observeDownloads(aid: String): Flow<List<DownloadTaskEntity>> = emptyFlow()

    override fun observeDownloadBooks(): Flow<List<DownloadBookEntity>> = emptyFlow()

    override suspend fun upsertDownloadBook(entity: DownloadBookEntity) = Unit

    override suspend fun deleteDownloadBook(aid: String) = Unit

    override suspend fun deleteDownloadTasks(aid: String) = Unit

    override suspend fun updateDownloadStatus(aid: String, status: String, updatedAt: Long) = Unit

    override suspend fun updateChapterStatus(cid: String, status: String, updatedAt: Long) = Unit

    override suspend fun updateChapterStatus(aid: String, cid: String, status: String, updatedAt: Long) = Unit

    override suspend fun getChapterTaskAid(cid: String): String? = null

    override suspend fun getChapterTaskAid(aid: String, cid: String): String? = null

    override suspend fun deleteDownloadTask(cid: String) = Unit

    override suspend fun deleteDownloadTask(aid: String, cid: String) = Unit

    override suspend fun deleteChapterCache(cid: String) {
        chapters.entries.removeIf { it.value.cid == cid }
    }

    override suspend fun deleteChapterCache(aid: String, cid: String) {
        chapters.entries.removeIf { it.value.aid == aid && it.value.cid == cid }
    }

    override suspend fun getDownloadStatus(cid: String): String? = null

    override suspend fun getDownloadStatus(aid: String, cid: String): String? = null

    override suspend fun upsertDownload(entity: DownloadTaskEntity) = Unit

    override suspend fun pendingDownloads(aid: String): List<DownloadTaskEntity> = emptyList()

    override suspend fun pendingAids(): List<String> = emptyList()

    override suspend fun resetInterruptedDownloads(updatedAt: Long) = Unit

    override suspend fun markDownloaded(cid: String, downloaded: Boolean) {
        chapters.entries
            .filter { it.value.cid == cid }
            .forEach { (key, value) -> chapters[key] = value.copy(isDownloaded = downloaded) }
    }

    override suspend fun markDownloaded(aid: String, cid: String, downloaded: Boolean) {
        chapters.entries
            .filter { it.value.aid == aid && it.value.cid == cid }
            .forEach { (key, value) -> chapters[key] = value.copy(isDownloaded = downloaded) }
    }

    override suspend fun deleteExpiredCache(before: Long) {
        chapters.entries.removeIf { !it.value.isDownloaded && it.value.updatedAt < before }
    }

    private fun progressKey(aid: String, cid: String) = aid + ":" + cid
}
