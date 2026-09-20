package com.cyh128.hikari_novel.data.repository

import android.content.Context
import com.cyh128.hikari_novel.data.model.CachedChapter
import com.cyh128.hikari_novel.data.model.ChapterRef
import com.cyh128.hikari_novel.data.model.ContentSource
import com.cyh128.hikari_novel.data.model.ReaderAnchor
import com.cyh128.hikari_novel.data.model.ReaderOrientation
import com.cyh128.hikari_novel.data.repository.reader.ChapterCache
import com.cyh128.hikari_novel.data.repository.reader.ChapterLoader
import com.cyh128.hikari_novel.data.repository.reader.ChapterPrefetcher
import com.cyh128.hikari_novel.data.repository.reader.DefaultChapterLoader
import com.cyh128.hikari_novel.data.repository.reader.DefaultChapterPrefetcher
import com.cyh128.hikari_novel.data.repository.reader.ReaderDaoProgressStore
import com.cyh128.hikari_novel.data.repository.reader.RoomChapterCache
import com.cyh128.hikari_novel.data.repository.reader.ReaderProgressStore
import com.cyh128.hikari_novel.data.repository.reader.Wenku8ChapterRemoteSource
import com.cyh128.hikari_novel.data.source.local.database.reader.BookmarkEntity
import com.cyh128.hikari_novel.data.source.local.database.reader.DownloadBookEntity
import com.cyh128.hikari_novel.data.source.local.database.reader.DownloadTaskEntity
import com.cyh128.hikari_novel.data.source.local.database.reader.ReaderDao
import com.cyh128.hikari_novel.data.source.local.database.reader.ReaderProgressEntity
import com.cyh128.hikari_novel.data.source.remote.Network
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Facade for reader data operations.
 *
 * Chapter resolution, cache storage, progress migration, and prefetching live
 * in focused collaborators. The facade keeps the existing API used by the UI
 * and owns only download-task orchestration for backwards compatibility.
 */
@Singleton
class ReaderRepository @Inject constructor(
    private val chapterLoader: ChapterLoader,
    private val chapterCache: ChapterCache,
    private val progressStore: ReaderProgressStore,
    private val chapterPrefetcher: ChapterPrefetcher,
    private val dao: ReaderDao,
    private val network: Network,
    @ApplicationContext private val context: Context
) {
    /**
     * Source-compatible bridge for tests or integrations that used to construct
     * the repository directly. Hilt uses the primary collaborator-based ctor.
     */
    @Deprecated("Use dependency injection with the collaborator-based constructor")
    constructor(
        wenku8Repository: Wenku8Repository,
        dao: ReaderDao,
        network: Network,
        context: Context
    ) : this(
        chapterLoader = DefaultChapterLoader(
            Wenku8ChapterRemoteSource(wenku8Repository),
            RoomChapterCache(dao)
        ),
        chapterCache = RoomChapterCache(dao),
        progressStore = ReaderDaoProgressStore(dao),
        chapterPrefetcher = DefaultChapterPrefetcher(
            DefaultChapterLoader(
                Wenku8ChapterRemoteSource(wenku8Repository),
                RoomChapterCache(dao)
            )
        ),
        dao = dao,
        network = network,
        context = context
    )

    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadMutex = Mutex()

    /** Return cached content without making a network request. */
    suspend fun cached(ref: ChapterRef): CachedChapter? = chapterCache.get(ref)?.chapter

    /** Resolve a chapter through cache and network according to [forceNetwork]. */
    suspend fun load(
        ref: ChapterRef,
        forceNetwork: Boolean = false
    ): Result<Pair<CachedChapter, ContentSource>> = chapterLoader.load(ref, forceNetwork)

    /** Best-effort prefetch of one caller-selected next chapter. */
    suspend fun prefetch(ref: ChapterRef): Boolean = chapterPrefetcher.prefetch(ref)

    suspend fun enqueueDownloads(refs: List<ChapterRef>) {
        val distinctRefs = refs.distinctBy { it.aid to it.cid }
        if (distinctRefs.isEmpty()) return

        val now = System.currentTimeMillis()
        distinctRefs.forEach { ref ->
            dao.upsertDownload(
                DownloadTaskEntity(
                    cid = ref.cid,
                    aid = ref.aid,
                    volumeIndex = ref.volumeIndex,
                    chapterIndex = ref.chapterIndex,
                    title = ref.title,
                    status = DOWNLOAD_QUEUED,
                    progress = 0,
                    error = null,
                    updatedAt = now
                )
            )
        }
        downloadScope.launch { runPendingDownloads(distinctRefs.first().aid) }
    }

    suspend fun enqueueDownloads(
        refs: List<ChapterRef>,
        title: String,
        imageUrl: String?,
        totalChapters: Int
    ) {
        val distinctRefs = refs.distinctBy { it.aid to it.cid }
        if (distinctRefs.isEmpty()) return
        dao.upsertDownloadBook(
            DownloadBookEntity(
                aid = distinctRefs.first().aid,
                title = title,
                imageUrl = imageUrl,
                totalChapters = totalChapters,
                updatedAt = System.currentTimeMillis()
            )
        )
        enqueueDownloads(distinctRefs)
    }

    /** Resume queued, failed, and interrupted (RUNNING) tasks after process restart. */
    suspend fun resumeQueuedDownloads() {
        dao.resetInterruptedDownloads(System.currentTimeMillis())
        dao.pendingAids().forEach { aid ->
            downloadScope.launch { runPendingDownloads(aid) }
        }
    }

    /**
     * Execute one book's pending download tasks. This operation is serialized
     * independently from normal chapter loading and treats every task as
     * best-effort: a failed task is recorded while later tasks can continue.
     */
    suspend fun runPendingDownloads(aid: String): Boolean = downloadMutex.withLock {
        var allSucceeded = true
        dao.pendingDownloads(aid).forEach { task ->
            if (dao.getDownloadStatus(task.aid, task.cid) == DOWNLOAD_PAUSED) return@forEach

            dao.upsertDownload(
                task.copy(
                    status = DOWNLOAD_RUNNING,
                    progress = 5,
                    error = null,
                    updatedAt = System.currentTimeMillis()
                )
            )
            val ref = ChapterRef(task.aid, task.cid, task.volumeIndex, task.chapterIndex, task.title)

            try {
                // Downloads may use an already cached chapter, but never make
                // the foreground reader wait for this queue's mutex.
                val chapter = cached(ref) ?: load(ref, forceNetwork = true).getOrThrow().first
                val localImages = downloadImages(chapter)
                val downloaded = chapter.copy(
                    images = localImages,
                    isDownloaded = true,
                    updatedAt = System.currentTimeMillis()
                )
                chapterCache.put(downloaded)
                dao.markDownloaded(task.aid, task.cid, true)
                dao.upsertDownload(
                    task.copy(
                        status = DOWNLOAD_COMPLETED,
                        progress = 100,
                        error = null,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } catch (error: CancellationException) {
                // Leave the task RUNNING so the next process start can recover it.
                throw error
            } catch (error: Throwable) {
                allSucceeded = false
                dao.upsertDownload(
                    task.copy(
                        status = DOWNLOAD_FAILED,
                        progress = 0,
                        error = error.message,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
        allSucceeded
    }

    fun observeDownloads(aid: String) = dao.observeDownloads(aid)

    fun observeDownloadBooks() = dao.observeDownloadBooks()

    suspend fun pauseBook(aid: String) =
        dao.updateDownloadStatus(aid, DOWNLOAD_PAUSED, System.currentTimeMillis())

    suspend fun resumeBook(aid: String) {
        dao.updateDownloadStatus(aid, DOWNLOAD_QUEUED, System.currentTimeMillis())
        downloadScope.launch { runPendingDownloads(aid) }
    }

    suspend fun retryBook(aid: String) = resumeBook(aid)

    /** Legacy cid-only retry; use the aid-scoped overload when possible. */
    @Deprecated("Use retryChapter(aid, cid)")
    suspend fun retryChapter(cid: String) {
        dao.updateChapterStatus(cid, DOWNLOAD_QUEUED, System.currentTimeMillis())
        dao.getChapterTaskAid(cid)?.let { aid ->
            downloadScope.launch { runPendingDownloads(aid) }
        }
    }

    suspend fun retryChapter(aid: String, cid: String) {
        dao.updateChapterStatus(aid, cid, DOWNLOAD_QUEUED, System.currentTimeMillis())
        downloadScope.launch { runPendingDownloads(aid) }
    }

    suspend fun deleteBookDownloads(aid: String) {
        dao.deleteDownloadTasks(aid)
        dao.deleteDownloadBook(aid)
    }

    /** Legacy cid-only deletion; use the aid-scoped overload when possible. */
    @Deprecated("Use deleteChapterDownload(aid, cid)")
    suspend fun deleteChapterDownload(cid: String) {
        dao.deleteDownloadTask(cid)
        chapterCache.remove(cid)
    }

    suspend fun deleteChapterDownload(aid: String, cid: String) {
        dao.deleteDownloadTask(aid, cid)
        chapterCache.remove(ChapterRef(aid, cid, 0, 0, ""))
    }

    suspend fun clearExpiredCache(before: Long) = chapterCache.clearExpired(before)

    /** Save the new anchor and optionally mirror it to the legacy history table. */
    suspend fun saveProgress(
        progress: ReaderAnchor,
        ref: ChapterRef,
        orientation: ReaderOrientation? = null,
        legacyLocation: Int? = null
    ) {
        // Keep the old facade contract: a stale anchor for another chapter is
        // ignored instead of being rewritten under the current cid.
        if (progress.cid != ref.cid) return
        progressStore.save(progress, ref, orientation, legacyLocation)
    }

    /**
     * Legacy-compatible lookup by cid. New reader code must use the ref-aware
     * overload because chapter ids are only unique within a book.
     */
    @Deprecated("Use getProgress(ref, content, orientation)")
    suspend fun getProgress(cid: String): ReaderProgressEntity? = progressStore.get(cid)

    /** Explicit startup hook for callers that do not own an application scope. */
    suspend fun recoverInterruptedDownloads() = resumeQueuedDownloads()

    /** Lookup with chapter metadata/content so old percentages become stable anchors. */
    suspend fun getProgress(
        ref: ChapterRef,
        content: String? = null,
        orientation: ReaderOrientation? = null
    ): ReaderProgressEntity? = progressStore.get(ref, content, orientation)

    fun observeProgress(aid: String) = progressStore.observe(aid)

    // ===== P1-3: Bookmarks =====

    fun observeBookmarks(aid: String) = dao.observeBookmarks(aid)

    suspend fun insertBookmark(bookmark: BookmarkEntity): Long = dao.insertBookmark(bookmark)

    suspend fun hasBookmark(aid: String, cid: String): Boolean = dao.hasBookmark(aid, cid)

    suspend fun deleteBookmark(id: Long) = dao.deleteBookmark(id)

    suspend fun deleteBookmarkByCid(aid: String, cid: String) = dao.deleteBookmarkByCid(aid, cid)

    // ===== P1-4: Full-book search =====

    /** Cached chapter bodies for a book, keyed by cid. One query, no network. */
    suspend fun cachedChapterContents(aid: String): Map<String, String> =
        dao.cachedChapterContents(aid).associate { it.cid to it.content }

    /**
     * Cache-first chapter body lookup with a network fallback, used by the
     * search's opt-in online phase. A successful load also fills the cache, so
     * repeating the same search gets cheaper.
     */
    suspend fun loadChapterContent(ref: ChapterRef): String? =
        chapterLoader.load(ref).getOrNull()?.first?.content

    private suspend fun downloadImages(chapter: CachedChapter): List<String> =
        withContext(Dispatchers.IO) {
            if (chapter.images.isEmpty()) return@withContext emptyList()
            val directory = File(context.filesDir, "chapters/${chapter.ref.aid}/${chapter.ref.cid}")
                .apply { mkdirs() }

            chapter.images.mapIndexed { index, url ->
                if (url.startsWith(directory.absolutePath)) return@mapIndexed url

                val target = File(directory, "image_$index")
                val temporary = File(directory, "image_$index.tmp")
                try {
                    temporary.writeBytes(network.downloadBytes(url))
                    if (target.exists()) target.delete()
                    check(temporary.renameTo(target)) { "Unable to finalize image download" }
                    target.absolutePath
                } catch (error: Throwable) {
                    temporary.delete()
                    throw error
                }
            }
        }

    private companion object {
        const val DOWNLOAD_QUEUED = "QUEUED"
        const val DOWNLOAD_RUNNING = "RUNNING"
        const val DOWNLOAD_PAUSED = "PAUSED"
        const val DOWNLOAD_COMPLETED = "COMPLETED"
        const val DOWNLOAD_FAILED = "FAILED"
    }
}
