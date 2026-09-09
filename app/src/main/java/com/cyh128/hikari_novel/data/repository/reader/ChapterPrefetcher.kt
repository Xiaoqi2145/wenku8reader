package com.cyh128.hikari_novel.data.repository.reader

import com.cyh128.hikari_novel.data.model.ChapterRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Schedules best-effort loading of one already-selected next chapter. */
interface ChapterPrefetcher {
    suspend fun prefetch(ref: ChapterRef): Boolean
}

/**
 * A bounded, deduplicating prefetcher. It deliberately has no queue: if a
 * chapter is already being prefetched, another request is ignored. This keeps
 * rapid traversal of short chapters from expanding into a whole-book download.
 */
@Singleton
class DefaultChapterPrefetcher @Inject constructor(
    private val loader: ChapterLoader
) : ChapterPrefetcher {
    private val semaphore = Semaphore(1)
    private val stateMutex = Mutex()
    private val inFlight = mutableSetOf<String>()

    override suspend fun prefetch(ref: ChapterRef): Boolean {
        // Do not queue prefetches behind one another. The active reader only
        // needs the latest caller-selected next chapter.
        if (!semaphore.tryAcquire()) return false
        var registered = false
        try {
            registered = stateMutex.withLock { inFlight.add(ref.cid) }
            if (!registered) return false

            return try {
                loader.load(ref).isSuccess
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // Prefetch is opportunistic and must never fail the active reader.
                false
            }
        } finally {
            withContext(NonCancellable) {
                stateMutex.withLock { inFlight.remove(ref.cid) }
                semaphore.release()
            }
        }
    }
}
