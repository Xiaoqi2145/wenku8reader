package com.cyh128.hikari_novel.data.repository

import com.cyh128.hikari_novel.R
import com.cyh128.hikari_novel.data.model.NetworkException
import com.cyh128.hikari_novel.data.source.local.database.bookshelf.BookshelfEntity
import com.cyh128.hikari_novel.util.ResourceUtil
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

/** The highest shelf index the site exposes; its page also carries the quota. */
private const val LAST_CLASS_ID = 5

/**
 * Pulls every remote shelf page and replaces the local copy with it.
 *
 * This lived as two near-identical copies, one in each ViewModel that needed it
 * (login and the bookshelf tab). Besides the duplication, the login copy wiped
 * the local table *before* the requests went out, so a transient failure left
 * the user with an empty shelf and no way to tell why. The replacement now
 * happens only after the network has answered.
 *
 * The six pages are fetched concurrently, and the whole operation runs on
 * [Dispatchers.IO] because each page is Jsoup-parsed — that parsing used to
 * happen on the main thread, since both call sites launched from the default
 * dispatcher.
 */
@Singleton
class BookshelfSyncer @Inject constructor(
    private val wenku8Repository: Wenku8Repository,
    private val bookshelfRepository: BookshelfRepository
) {

    /**
     * Fetches all six shelves and writes them to the database.
     *
     * Fails only when every page failed. A partial result is still written: the
     * pages that answered are real data, and dropping them would throw away
     * more than it protects.
     */
    suspend fun sync(): Result<Unit> = withContext(Dispatchers.IO) {
        val results = (0..LAST_CLASS_ID).map { classId ->
            async { classId to wenku8Repository.getBookshelf(classId) }
        }.awaitAll()

        val loaded = results.mapNotNull { (classId, result) ->
            result.getOrNull()?.let { classId to it }
        }
        if (loaded.isEmpty()) {
            return@withContext Result.failure(
                NetworkException(ResourceUtil.getString(R.string.network_error_msg))
            )
        }

        val rows = loaded.flatMap { (classId, shelf) ->
            shelf.list.map { info ->
                BookshelfEntity(
                    aid = info.aid,
                    bid = info.bid,
                    detailUrl = info.detailUrl,
                    title = info.title,
                    img = info.img,
                    classId = classId
                )
            }
        }
        bookshelfRepository.replaceAll(rows)
        val lastShelf = loaded.firstOrNull { (classId, _) -> classId == LAST_CLASS_ID }?.second
        if (lastShelf != null) bookshelfRepository.setMaxCollection(lastShelf.maxNum)
        Result.success(Unit)
    }
}
