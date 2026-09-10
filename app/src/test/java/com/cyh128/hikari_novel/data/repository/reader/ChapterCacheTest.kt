package com.cyh128.hikari_novel.data.repository.reader

import com.cyh128.hikari_novel.data.model.CachedChapter
import com.cyh128.hikari_novel.data.model.ChapterRef
import com.cyh128.hikari_novel.data.model.ContentSource
import com.cyh128.hikari_novel.data.source.local.database.reader.ChapterCacheEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterCacheTest {
    private fun ref(cid: String, aid: String = "book") =
        ChapterRef(aid, cid, 0, 0, "Chapter " + cid)

    private fun chapter(cid: String, aid: String = "book", updatedAt: Long = 1L, downloaded: Boolean = false) =
        CachedChapter(ref(cid, aid), "content-" + cid, emptyList(), updatedAt, downloaded)

    @Test
    fun memoryAndDiskHitsAreDistinguished() = runBlocking {
        val dao = FakeReaderDao()
        val first = RoomChapterCache(dao)
        first.put(chapter("c1"))

        assertEquals(ContentSource.MEMORY_CACHE, first.get(ref("c1"))?.source)

        val second = RoomChapterCache(dao)
        assertEquals(ContentSource.DISK_CACHE, second.get(ref("c1"))?.source)
    }

    @Test
    fun memoryCacheEvictsLeastRecentlyUsedEntry() = runBlocking {
        val dao = FakeReaderDao()
        val cache = RoomChapterCache(dao)
        repeat(9) { index -> cache.put(chapter("c" + (index + 1))) }

        val evicted = cache.get(ref("c1"))

        assertEquals(ContentSource.DISK_CACHE, evicted?.source)
        assertEquals("content-c1", evicted?.chapter?.content)
    }

    @Test
    fun malformedImagesJsonBecomesCacheMissAndIsRemoved() = runBlocking {
        val dao = FakeReaderDao()
        dao.chapters["bad"] = ChapterCacheEntity(
            cid = "bad",
            aid = "book",
            volumeIndex = 0,
            chapterIndex = 0,
            title = "Bad",
            content = "content",
            imagesJson = "{not-json",
            updatedAt = 1L,
            isDownloaded = false,
            sizeBytes = 7L
        )

        assertNull(RoomChapterCache(dao).get(ref("bad")))
        assertTrue(dao.chapters.values.none { it.cid == "bad" })
    }

    @Test
    fun downloadedEntriesSurviveExpiryCleanup() = runBlocking {
        val dao = FakeReaderDao()
        val cache = RoomChapterCache(dao)
        cache.put(chapter("old", updatedAt = 1L))
        cache.put(chapter("downloaded", updatedAt = 1L, downloaded = true))

        cache.clearExpired(before = 100L)

        assertTrue(dao.chapters.values.none { it.cid == "old" })
        assertTrue(dao.chapters.values.any { it.cid == "downloaded" })
    }

    @Test
    fun cacheDoesNotReturnAnotherBooksEntryForSameCid() = runBlocking {
        val dao = FakeReaderDao()
        val cache = RoomChapterCache(dao)
        cache.put(chapter("same", aid = "book-a"))

        assertNull(cache.get(ref("same", aid = "book-b")))
    }
}
