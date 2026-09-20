package com.cyh128.hikari_novel.data.repository.reader

import com.cyh128.hikari_novel.data.model.ChapterRef

/** One match inside a chapter body. */
data class BookSearchHit(
    val ref: ChapterRef,
    /** Chapter-local UTF-16 offset of the match, ready to become a ReaderAnchor. */
    val charOffset: Int,
    /** Match length in UTF-16 units, used by the reader to highlight the hit. */
    val matchLength: Int,
    /** Position inside the chapter, used by the paged reader to pick a page. */
    val normalizedPercent: Int,
    /** Short readable context around the match. */
    val snippet: String
)

/** Where a hit's chapter body came from. */
enum class SearchHitSource { CACHE, NETWORK }

/**
 * Pure full-text matching for the reader's in-book search.
 *
 * Deliberately not a tokenizer: wenku8 chapters are plain Chinese prose, so a
 * case-insensitive substring scan is both accurate and cheap. Free of
 * Android/Room so the matching rules can be unit-tested.
 *
 * See docs/reader-koodo-alignment.md P1-4.
 */
object BookSearchMatcher {

    /** Characters of context kept on each side of a match. */
    const val SNIPPET_RADIUS = 24

    /** A very common term must not flood the result list. */
    const val MAX_HITS_PER_CHAPTER = 30

    fun normalizeQuery(query: String): String = query.trim()

    /** Chapter-local offsets of every match, capped at [MAX_HITS_PER_CHAPTER]. */
    fun findOffsets(content: String, query: String): List<Int> {
        if (query.isEmpty() || content.isEmpty()) return emptyList()
        val offsets = mutableListOf<Int>()
        var index = content.indexOf(query, 0, ignoreCase = true)
        while (index >= 0 && offsets.size < MAX_HITS_PER_CHAPTER) {
            offsets += index
            index = content.indexOf(query, index + query.length, ignoreCase = true)
        }
        return offsets
    }

    fun hits(ref: ChapterRef, content: String, query: String): List<BookSearchHit> =
        findOffsets(content, query).map { offset ->
            BookSearchHit(
                ref = ref,
                charOffset = offset,
                matchLength = query.length,
                normalizedPercent = percent(offset, content.length),
                snippet = snippet(content, offset, query.length)
            )
        }

    /** Mirrors ChapterBlockMapper.percent so anchors from both paths agree. */
    private fun percent(offset: Int, length: Int): Int =
        if (length <= 0) 0 else (offset * 100 / length).coerceIn(0, 100)

    /** Newlines are flattened so a hit stays on one line in the result list. */
    fun snippet(content: String, offset: Int, matchLength: Int): String {
        val start = (offset - SNIPPET_RADIUS).coerceAtLeast(0)
        val end = (offset + matchLength + SNIPPET_RADIUS).coerceAtMost(content.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < content.length) "…" else ""
        val body = content.substring(start, end).replace('\n', ' ').trim()
        return prefix + body + suffix
    }
}
