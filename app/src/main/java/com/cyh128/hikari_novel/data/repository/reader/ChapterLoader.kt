package com.cyh128.hikari_novel.data.repository.reader

import com.cyh128.hikari_novel.data.model.CachedChapter
import com.cyh128.hikari_novel.data.model.ChapterContentResponse
import com.cyh128.hikari_novel.data.model.ChapterRef
import com.cyh128.hikari_novel.data.model.ContentSource
import com.cyh128.hikari_novel.data.repository.Wenku8Repository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/** Network boundary used by [ChapterLoader]. */
fun interface ChapterRemoteSource {
    suspend fun load(ref: ChapterRef): Result<ChapterContentResponse>
}

/** Adapter from the existing Wenku8 repository to the reader data pipeline. */
@Singleton
class Wenku8ChapterRemoteSource @Inject constructor(
    private val repository: Wenku8Repository
) : ChapterRemoteSource {
    override suspend fun load(ref: ChapterRef): Result<ChapterContentResponse> =
        repository.getNovelContent(ref.aid, ref.cid)
}

/** Chapter loading abstraction kept independent from Android UI. */
interface ChapterLoader {
    suspend fun load(
        ref: ChapterRef,
        forceNetwork: Boolean = false
    ): Result<Pair<CachedChapter, ContentSource>>
}

/**
 * Resolves memory/disk cache first, then network, and finally a stale cache
 * when refresh fails. Cache write failures do not prevent the current chapter
 * from being displayed.
 */
@Singleton
class DefaultChapterLoader @Inject constructor(
    private val remote: ChapterRemoteSource,
    private val cache: ChapterCache
) : ChapterLoader {
    override suspend fun load(
        ref: ChapterRef,
        forceNetwork: Boolean
    ): Result<Pair<CachedChapter, ContentSource>> {
        val cached = try {
            cache.get(ref)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            // A transient cache/DB failure should not hide a network response.
            null
        }
        if (!forceNetwork && cached != null) {
            return Result.success(cached.chapter to cached.source)
        }

        val remoteResult = try {
            remote.load(ref)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }

        return remoteResult.fold(
            onSuccess = { response ->
                val chapter = CachedChapter(
                    ref = ref,
                    content = response.content,
                    images = response.image,
                    updatedAt = System.currentTimeMillis(),
                    isDownloaded = cached?.chapter?.isDownloaded == true
                )
                try {
                    cache.put(chapter)
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    // The reader can still use the network response. A later
                    // request will simply try the network again.
                }
                Result.success(chapter to ContentSource.NETWORK)
            },
            onFailure = { error ->
                if (error is CancellationException) throw error
                if (cached != null) {
                    Result.success(cached.chapter to ContentSource.STALE_CACHE)
                } else {
                    Result.failure(error)
                }
            }
        )
    }
}
