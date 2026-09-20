package com.cyh128.hikari_novel.ui.read.catalog

import com.cyh128.hikari_novel.data.model.ReaderAnchor

/**
 * Where the reader should go when a catalog row is tapped.
 *
 * The chapter list only needs a chapter; the bookmark tab and the search results
 * also carry an [anchor] so the reader can land on the exact spot. [highlightLength]
 * is non-zero only for search hits, where the reader briefly marks the match.
 *
 * See docs/reader-koodo-alignment.md P1-3 / P1-4.
 */
data class ReaderJumpTarget(
    val volumePos: Int,
    val chapterPos: Int,
    val anchor: ReaderAnchor? = null,
    val highlightLength: Int = 0
)
