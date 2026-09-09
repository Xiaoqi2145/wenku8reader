package com.cyh128.hikari_novel.data.repository.reader

import com.cyh128.hikari_novel.data.model.CachedChapter
import com.cyh128.hikari_novel.data.model.ChapterContentResponse
import com.cyh128.hikari_novel.data.model.ChapterRef
import com.cyh128.hikari_novel.data.model.ContentSource
import kotlinx.coroutines.CompletableDeferred
fun testChapterRef(cid: String = "c1") = ChapterRef("book", cid, 0, 0, "Chapter " + cid)

class FakeChapterCache : ChapterCache {
    val values = linkedMapOf<String, CachedChapter>()
    var getFailure: Throwable? = null
    var putFailure: Throwable? = null

    override suspend fun get(ref: ChapterRef): ChapterCacheHit? {
        getFailure?.let { throw it }
        return values[ref.cid]?.let { ChapterCacheHit(it, ContentSource.MEMORY_CACHE) }
    }

    override suspend fun put(chapter: CachedChapter) {
        putFailure?.let { throw it }
        values[chapter.ref.cid] = chapter
    }

    override suspend fun remove(cid: String) {
        values.remove(cid)
    }

    override suspend fun clearExpired(before: Long) {
        values.entries.removeIf { !it.value.isDownloaded && it.value.updatedAt < before }
    }
}

class FakeChapterRemoteSource(
    private val response: Result<ChapterContentResponse>
) : ChapterRemoteSource {
    var calls = 0
    override suspend fun load(ref: ChapterRef): Result<ChapterContentResponse> {
        calls++
        return response
    }
}

class FakeChapterLoader : ChapterLoader {
    var calls = 0
    var entered: CompletableDeferred<Unit>? = null
    var release: CompletableDeferred<Unit>? = null
    var result: Result<Pair<CachedChapter, ContentSource>> = Result.success(
        CachedChapter(testChapterRef(), "content", emptyList(), 1L) to ContentSource.NETWORK
    )

    override suspend fun load(ref: ChapterRef, forceNetwork: Boolean): Result<Pair<CachedChapter, ContentSource>> {
        calls++
        entered?.complete(Unit)
        release?.await()
        return result
    }
}
