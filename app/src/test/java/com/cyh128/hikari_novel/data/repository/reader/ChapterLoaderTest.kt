package com.cyh128.hikari_novel.data.repository.reader

import com.cyh128.hikari_novel.data.model.CachedChapter
import com.cyh128.hikari_novel.data.model.ChapterContentResponse
import com.cyh128.hikari_novel.data.model.ChapterRef
import com.cyh128.hikari_novel.data.model.ContentSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterLoaderTest {
    @Test
    fun cacheHitDoesNotCallRemote() = runBlocking {
        val cache = FakeChapterCache()
        val chapter = CachedChapter(testChapterRef(), "cached", emptyList(), 1L)
        cache.values[chapter.ref.cid] = chapter
        val remote = FakeChapterRemoteSource(Result.success(ChapterContentResponse("network", emptyList())))

        val result = DefaultChapterLoader(remote, cache).load(testChapterRef())

        assertTrue(result.isSuccess)
        assertEquals("cached", result.getOrThrow().first.content)
        assertEquals(ContentSource.MEMORY_CACHE, result.getOrThrow().second)
        assertEquals(0, remote.calls)
    }

    @Test
    fun forceNetworkKeepsDownloadedFlagAndRefreshesCache() = runBlocking {
        val cache = FakeChapterCache()
        val old = CachedChapter(testChapterRef(), "old", emptyList(), 1L, isDownloaded = true)
        cache.values[old.ref.cid] = old
        val remote = FakeChapterRemoteSource(Result.success(ChapterContentResponse("new", listOf("image"))))

        val result = DefaultChapterLoader(remote, cache).load(testChapterRef(), forceNetwork = true)

        assertEquals("new", result.getOrThrow().first.content)
        assertEquals(listOf("image"), result.getOrThrow().first.images)
        assertTrue(result.getOrThrow().first.isDownloaded)
        assertEquals(ContentSource.NETWORK, result.getOrThrow().second)
        assertEquals(1, remote.calls)
        assertEquals("new", cache.values.getValue("c1").content)
    }

    @Test
    fun networkFailureFallsBackToStaleCache() = runBlocking {
        val cache = FakeChapterCache()
        val old = CachedChapter(testChapterRef(), "cached", emptyList(), 1L)
        cache.values[old.ref.cid] = old
        val remote = FakeChapterRemoteSource(Result.failure(IllegalStateException("offline")))

        val result = DefaultChapterLoader(remote, cache).load(testChapterRef(), forceNetwork = true)

        assertTrue(result.isSuccess)
        assertEquals("cached", result.getOrThrow().first.content)
        assertEquals(ContentSource.STALE_CACHE, result.getOrThrow().second)
    }

    @Test
    fun cacheWriteFailureDoesNotHideNetworkContent() = runBlocking {
        val cache = FakeChapterCache().apply { putFailure = IllegalStateException("disk full") }
        val remote = FakeChapterRemoteSource(Result.success(ChapterContentResponse("network", emptyList())))

        val result = DefaultChapterLoader(remote, cache).load(testChapterRef())

        assertTrue(result.isSuccess)
        assertEquals("network", result.getOrThrow().first.content)
    }

    @Test
    fun cacheReadFailureStillTriesNetwork() = runBlocking {
        val cache = FakeChapterCache().apply { getFailure = IllegalStateException("db unavailable") }
        val remote = FakeChapterRemoteSource(Result.success(ChapterContentResponse("network", emptyList())))

        val result = DefaultChapterLoader(remote, cache).load(testChapterRef())

        assertTrue(result.isSuccess)
        assertEquals("network", result.getOrThrow().first.content)
        assertEquals(1, remote.calls)
    }

    @Test
    fun networkFailureWithoutCacheRemainsFailure() = runBlocking {
        val cache = FakeChapterCache()
        val remote = FakeChapterRemoteSource(Result.failure(IllegalStateException("offline")))

        val result = DefaultChapterLoader(remote, cache).load(testChapterRef())

        assertFalse(result.isSuccess)
        assertEquals("offline", result.exceptionOrNull()?.message)
    }
}

class ChapterPrefetcherTest {
    @Test
    fun prefetchFailureIsBestEffort() = runBlocking {
        val loader = FakeChapterLoader().apply {
            result = Result.failure(IllegalStateException("offline"))
        }

        assertFalse(DefaultChapterPrefetcher(loader).prefetch(testChapterRef()))
        assertEquals(1, loader.calls)
    }

    @Test
    fun concurrentSameCidIsDeduplicatedAndDifferentCidIsDropped() = runBlocking {
        val loader = FakeChapterLoader().apply {
            entered = CompletableDeferred()
            release = CompletableDeferred()
        }
        val prefetcher = DefaultChapterPrefetcher(loader)
        val first = async { prefetcher.prefetch(testChapterRef("c1")) }
        loader.entered!!.await()
        val duplicate = async { prefetcher.prefetch(testChapterRef("c1")) }
        val different = async { prefetcher.prefetch(testChapterRef("c2")) }
        assertFalse(duplicate.await())
        assertFalse(different.await())
        loader.release!!.complete(Unit)
        assertTrue(first.await())
        assertEquals(1, loader.calls)
    }

    @Test
    fun cancellationReleasesCidForRetry() = runBlocking {
        val loader = FakeChapterLoader().apply {
            entered = CompletableDeferred()
            release = CompletableDeferred()
        }
        val prefetcher = DefaultChapterPrefetcher(loader)
        val running = async { prefetcher.prefetch(testChapterRef("c1")) }
        loader.entered!!.await()
        running.cancelAndJoin()
        loader.release!!.complete(Unit)
        loader.entered = CompletableDeferred()
        loader.release = CompletableDeferred()
        val retry = async { prefetcher.prefetch(testChapterRef("c1")) }
        loader.entered!!.await()
        loader.release!!.complete(Unit)
        assertTrue(retry.await())
        assertEquals(2, loader.calls)
    }
}
