package com.cyh128.hikari_novel.data.repository.reader

import com.cyh128.hikari_novel.data.model.CachedChapter
import com.cyh128.hikari_novel.data.model.ChapterRef
import com.cyh128.hikari_novel.data.model.ContentSource
import com.cyh128.hikari_novel.data.source.local.database.reader.ChapterCacheEntity
import com.cyh128.hikari_novel.data.source.local.database.reader.ReaderDao
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.charset.StandardCharsets
import java.util.LinkedHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** A cache hit together with the layer that supplied its content. */
data class ChapterCacheHit(
    val chapter: CachedChapter,
    val source: ContentSource
)

/**
 * Storage-only abstraction for reader chapters.
 *
 * Implementations must not make navigation or UI decisions. A cache hit is
 * labelled so callers can distinguish memory, disk, and network content.
 */
interface ChapterCache {
    suspend fun get(ref: ChapterRef): ChapterCacheHit?
    suspend fun put(chapter: CachedChapter)
    suspend fun remove(cid: String)
    suspend fun clearExpired(before: Long)
}

/** Room-backed disk cache with a small, access-ordered in-memory front cache. */
@Singleton
class RoomChapterCache @Inject constructor(
    private val dao: ReaderDao
) : ChapterCache {
    companion object {
        private const val MEMORY_CAPACITY = 8
    }

    private val gson = Gson()
    private val memoryMutex = Mutex()
    private val memory = object : LinkedHashMap<String, CachedChapter>(MEMORY_CAPACITY, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedChapter>?): Boolean =
            size > MEMORY_CAPACITY
    }

    override suspend fun get(ref: ChapterRef): ChapterCacheHit? {
        memoryMutex.withLock { memory[cacheKey(ref)] }?.let { chapter ->
            return ChapterCacheHit(chapter, ContentSource.MEMORY_CACHE)
        }

        val entity = dao.getChapter(ref.cid) ?: return null
        // Keep the legacy cid-only schema from leaking another book's cache.
        if (entity.aid != ref.aid) {
            return null
        }
        val chapter = try {
            entity.toModel(ref)
        } catch (error: Throwable) {
            // A malformed JSON payload must behave like a cache miss. The
            // loader can then retry the network instead of failing the reader.
            if (error is CancellationException) throw error
            try {
                dao.deleteChapterCache(ref.cid)
            } catch (cleanupError: Throwable) {
                if (cleanupError is CancellationException) throw cleanupError
            }
            return null
        }

        memoryMutex.withLock { memory[cacheKey(ref)] = chapter }
        return ChapterCacheHit(chapter, ContentSource.DISK_CACHE)
    }

    override suspend fun put(chapter: CachedChapter) {
        dao.upsertChapter(chapter.toEntity())
        memoryMutex.withLock { memory[cacheKey(chapter.ref)] = chapter }
    }

    override suspend fun remove(cid: String) {
        memoryMutex.withLock { memory.keys.removeAll { it.endsWith(":" + cid) } }
        dao.deleteChapterCache(cid)
    }

    override suspend fun clearExpired(before: Long) {
        memoryMutex.withLock {
            val iterator = memory.entries.iterator()
            while (iterator.hasNext()) {
                val chapter = iterator.next().value
                if (!chapter.isDownloaded && chapter.updatedAt < before) iterator.remove()
            }
        }
        dao.deleteExpiredCache(before)
    }

    private fun cacheKey(ref: ChapterRef): String = "${ref.aid}:${ref.cid}"

    private fun ChapterCacheEntity.toModel(ref: ChapterRef): CachedChapter {
        val parsed: List<*> = gson.fromJson(
            imagesJson,
            object : TypeToken<List<String>>() {}.type
        ) ?: emptyList<Any>()
        val images = parsed.map { value ->
            value as? String ?: error("Cached image list contains a non-string value")
        }
        return CachedChapter(
            ref = ref,
            content = content,
            images = images,
            updatedAt = updatedAt,
            isDownloaded = isDownloaded
        )
    }

    private fun CachedChapter.toEntity(): ChapterCacheEntity = ChapterCacheEntity(
        cid = ref.cid,
        aid = ref.aid,
        volumeIndex = ref.volumeIndex,
        chapterIndex = ref.chapterIndex,
        title = ref.title,
        content = content,
        imagesJson = gson.toJson(images),
        updatedAt = updatedAt,
        isDownloaded = isDownloaded,
        sizeBytes = content.toByteArray(StandardCharsets.UTF_8).size.toLong()
    )
}
