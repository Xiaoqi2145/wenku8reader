package com.cyh128.hikari_novel.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.cyh128.hikari_novel.data.model.CachedChapter
import com.cyh128.hikari_novel.data.model.ChapterRef
import com.cyh128.hikari_novel.data.model.ContentSource
import com.cyh128.hikari_novel.data.source.local.database.reader.ChapterCacheEntity
import com.cyh128.hikari_novel.data.source.local.database.reader.ReaderDao
import com.cyh128.hikari_novel.data.source.local.database.reader.DownloadTaskEntity
import com.cyh128.hikari_novel.data.source.remote.Network
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import java.io.File

@Singleton
class ReaderRepository @Inject constructor(
    private val wenku8Repository: Wenku8Repository,
    private val dao: ReaderDao,
    private val network: Network,
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadMutex = Mutex()
    private val memory = object : LinkedHashMap<String, CachedChapter>(8, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedChapter>?) = size > 8
    }
    private val memoryMutex = Mutex()

    suspend fun cached(ref: ChapterRef): CachedChapter? = memoryMutex.withLock {
        memory[ref.cid] ?: dao.getChapter(ref.cid)?.toModel(ref, gson)?.also { memory[ref.cid] = it }
    }

    suspend fun load(ref: ChapterRef, forceNetwork: Boolean = false): Result<Pair<CachedChapter, ContentSource>> {
        val local = cached(ref)
        if (local != null && !forceNetwork) return Result.success(local to ContentSource.DISK_CACHE)
        return wenku8Repository.getNovelContent(ref.aid, ref.cid).map { response ->
            val model = CachedChapter(ref, response.content, response.image, System.currentTimeMillis(), local?.isDownloaded == true)
            dao.upsertChapter(model.toEntity(gson))
            memoryMutex.withLock { memory[ref.cid] = model }
            model to ContentSource.NETWORK
        }.recoverCatching { error ->
            if (local != null) local to ContentSource.STALE_CACHE else throw error
        }
    }

    suspend fun prefetch(ref: ChapterRef) {
        if (cached(ref) == null) load(ref)
    }

    suspend fun enqueueDownloads(refs: List<ChapterRef>) {
        if (refs.isEmpty()) return
        val now = System.currentTimeMillis()
        refs.forEach { ref ->
            dao.upsertDownload(DownloadTaskEntity(ref.cid, ref.aid, ref.volumeIndex, ref.chapterIndex, ref.title, "QUEUED", 0, null, now))
        }
        downloadScope.launch { runPendingDownloads(refs.first().aid) }
    }

    suspend fun resumeQueuedDownloads() {
        dao.pendingAids().forEach { aid -> downloadScope.launch { runPendingDownloads(aid) } }
    }

    suspend fun runPendingDownloads(aid: String): Boolean = downloadMutex.withLock {
        var allSucceeded = true
        dao.pendingDownloads(aid).forEach { task ->
            dao.upsertDownload(task.copy(status = "RUNNING", progress = 5, error = null, updatedAt = System.currentTimeMillis()))
            val ref = ChapterRef(task.aid, task.cid, task.volumeIndex, task.chapterIndex, task.title)
            try {
                val chapter = cached(ref) ?: load(ref, forceNetwork = true).getOrThrow().first
                val localImages = downloadImages(chapter)
                val downloaded = chapter.copy(images = localImages, isDownloaded = true, updatedAt = System.currentTimeMillis())
                dao.upsertChapter(downloaded.toEntity(gson))
                dao.markDownloaded(task.cid, true)
                memoryMutex.withLock { memory[task.cid] = downloaded }
                dao.upsertDownload(task.copy(status = "COMPLETED", progress = 100, error = null, updatedAt = System.currentTimeMillis()))
            } catch (error: Throwable) {
                allSucceeded = false
                dao.upsertDownload(task.copy(status = "FAILED", progress = 0, error = error.message, updatedAt = System.currentTimeMillis()))
            }
        }
        allSucceeded
    }

    fun observeDownloads(aid: String) = dao.observeDownloads(aid)

    private suspend fun downloadImages(chapter: CachedChapter): List<String> {
        if (chapter.images.isEmpty()) return emptyList()
        val directory = File(context.filesDir, "chapters/${chapter.ref.aid}/${chapter.ref.cid}").apply { mkdirs() }
        return chapter.images.mapIndexed { index, url ->
            if (url.startsWith(directory.absolutePath)) return@mapIndexed url
            val target = File(directory, "image_$index")
            val temporary = File(directory, "image_$index.tmp")
            temporary.writeBytes(network.downloadBytes(url))
            if (target.exists()) target.delete()
            check(temporary.renameTo(target)) { "Unable to finalize image download" }
            target.absolutePath
        }
    }

    suspend fun saveProgress(progress: com.cyh128.hikari_novel.data.model.ReaderAnchor, ref: ChapterRef) {
        dao.clearLatest(ref.aid)
        dao.upsertProgress(com.cyh128.hikari_novel.data.source.local.database.reader.ReaderProgressEntity(
            ref.cid, ref.aid, ref.volumeIndex, ref.chapterIndex, progress.paragraphIndex,
            progress.charOffset, progress.normalizedPercent, progress.imageIndex,
            System.currentTimeMillis(), true
        ))
    }

    suspend fun getProgress(cid: String) = dao.getProgress(cid)

    private fun ChapterCacheEntity.toModel(ref: ChapterRef, gson: Gson) = CachedChapter(
        ref, content, gson.fromJson(imagesJson, object : TypeToken<List<String>>() {}.type) ?: emptyList(),
        updatedAt, isDownloaded
    )

    private fun CachedChapter.toEntity(gson: Gson) = ChapterCacheEntity(
        ref.cid, ref.aid, ref.volumeIndex, ref.chapterIndex, ref.title, content,
        gson.toJson(images), updatedAt, isDownloaded, content.toByteArray().size.toLong()
    )
}
