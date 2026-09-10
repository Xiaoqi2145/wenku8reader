package com.cyh128.hikari_novel.reader.domain

import com.cyh128.hikari_novel.data.model.CachedChapter
import com.cyh128.hikari_novel.data.model.ChapterRef
import com.cyh128.hikari_novel.data.model.ContentSource
import com.cyh128.hikari_novel.data.model.ReaderAnchor
import com.cyh128.hikari_novel.data.model.ReaderOrientation
import kotlinx.coroutines.CancellationException

/** A storage-neutral snapshot of a persisted reader anchor. */
data class ReaderProgressSnapshot(
    val cid: String,
    val aid: String,
    val volumeIndex: Int,
    val chapterIndex: Int,
    val paragraphIndex: Int,
    val charOffset: Int,
    val normalizedPercent: Int,
    val imageIndex: Int?,
    val updatedAt: Long,
    val isLatest: Boolean
) {
    fun anchor(): ReaderAnchor = ReaderAnchor(
        cid = cid,
        paragraphIndex = paragraphIndex,
        charOffset = charOffset,
        normalizedPercent = normalizedPercent,
        imageIndex = imageIndex
    )
}

/**
 * Data boundary shared by horizontal and vertical reader sessions.
 *
 * The interface deliberately exposes a storage-neutral progress snapshot so
 * the domain coordinator does not depend on Room or Android classes.
 */
interface ReaderSessionDataSource {
    suspend fun load(
        ref: ChapterRef,
        forceNetwork: Boolean = false
    ): Result<Pair<CachedChapter, ContentSource>>

    suspend fun prefetch(ref: ChapterRef): Boolean

    suspend fun enqueueDownloads(refs: List<ChapterRef>)

    suspend fun saveProgress(
        progress: ReaderAnchor,
        ref: ChapterRef,
        orientation: ReaderOrientation? = null,
        legacyLocation: Int? = null
    )

    suspend fun getProgress(
        ref: ChapterRef,
        content: String? = null,
        orientation: ReaderOrientation? = null
    ): ReaderProgressSnapshot?
}

/** One load request, including the generation used to reject stale results. */
data class ReaderLoadRequest(
    val ref: ChapterRef,
    val generation: Long,
    val append: Boolean
)

/** A loaded chapter annotated with the request that produced it. */
data class ReaderLoadResult(
    val request: ReaderLoadRequest,
    val chapter: CachedChapter,
    val source: ContentSource
)

/**
 * Shared session coordinator for both reader orientations.
 *
 * It owns chapter-tree navigation and request generations. Loading, progress
 * persistence, and prefetch are delegated to the data boundary; rendering and
 * orientation-specific position sampling remain in their UI layers.
 */
class ReaderSessionCoordinator(
    private val dataSource: ReaderSessionDataSource
) {
    private var navigator = ChapterNavigator(emptyList())
    private var current: ChapterRef? = null
    private var loaded: CachedChapter? = null
    private var generation = 0L

    /**
     * Configure the chapter tree and current chapter. A changed book/chapter
     * invalidates older requests and clears the single-chapter cache.
     */
    @Synchronized
    fun configure(
        volumes: List<List<ChapterRef>>,
        currentChapter: ChapterRef?
    ) {
        val nextNavigator = ChapterNavigator(volumes)
        val canonical = currentChapter?.let { requested ->
            nextNavigator.locate(requested.cid)
                ?.chapter
                ?.takeIf { it.aid == requested.aid }
        }
        val changed = current != canonical
        navigator = nextNavigator
        current = canonical
        if (changed) {
            loaded = null
            generation++
        }
    }

    @Synchronized
    fun chapterNavigator(): ChapterNavigator = navigator

    @Synchronized
    fun currentChapterRef(): ChapterRef? = current

    /** The most recently accepted chapter, if it is still the current chapter. */
    @Synchronized
    fun currentCachedChapter(): CachedChapter? = loaded?.takeIf { it.ref == current }

    /** Start a request and invalidate every older request. */
    @Synchronized
    fun beginLoad(ref: ChapterRef, append: Boolean = false): ReaderLoadRequest {
        current = ref
        if (!append) loaded = null
        return ReaderLoadRequest(
            ref = ref,
            generation = ++generation,
            append = append
        )
    }

    /** Load through the shared cache/network pipeline. */
    suspend fun load(
        request: ReaderLoadRequest,
        forceNetwork: Boolean = false
    ): Result<ReaderLoadResult> {
        val result = dataSource.load(request.ref, forceNetwork)
        return result.map { (chapter, source) ->
            synchronized(this) {
                if (isCurrentLocked(request)) loaded = chapter
            }
            ReaderLoadResult(request, chapter, source)
        }
    }

    /** Prefetch the request's next chapter without delaying the foreground load. */
    suspend fun prefetchNext(request: ReaderLoadRequest): Boolean {
        if (!isCurrent(request)) return false
        val next = synchronized(this) { navigator.next(request.ref) } ?: return false
        return try {
            dataSource.prefetch(next)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            // Prefetch is opportunistic and must never fail the active reader.
            false
        }
    }

    @Synchronized
    fun isCurrent(request: ReaderLoadRequest): Boolean = isCurrentLocked(request)

    /** Event-friendly generation check used by Activities after recreation. */
    @Synchronized
    fun acceptsLoadEvent(generation: Long, aid: String, cid: String): Boolean =
        this.generation == generation && current?.aid == aid && current?.cid == cid

    @Synchronized
    private fun isCurrentLocked(request: ReaderLoadRequest): Boolean =
        generation == request.generation && current == request.ref

    @Synchronized
    fun nextChapterLocation(): ChapterLocation? = current
        ?.let { navigator.locate(it.cid) }
        ?.let(navigator::next)

    @Synchronized
    fun previousChapterLocation(): ChapterLocation? = current
        ?.let { navigator.locate(it.cid) }
        ?.let(navigator::previous)

    suspend fun saveProgress(
        progress: ReaderAnchor,
        ref: ChapterRef,
        orientation: ReaderOrientation? = null,
        legacyLocation: Int? = null
    ) {
        if (progress.cid == ref.cid) {
            dataSource.saveProgress(progress, ref, orientation, legacyLocation)
        }
    }

    suspend fun getProgress(
        ref: ChapterRef,
        content: String? = null,
        orientation: ReaderOrientation? = null
    ): ReaderProgressSnapshot? = dataSource.getProgress(ref, content, orientation)

    suspend fun enqueueDownloads(refs: List<ChapterRef>) = dataSource.enqueueDownloads(refs)
}
