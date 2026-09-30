package com.cyh128.hikari_novel.ui.read.horizontal

/**
 * Page and spread geometry for the horizontal reader.
 *
 * Kept free of Android types (the same reason [com.cyh128.hikari_novel.ui.read.common.ReaderChromeGeometry]
 * lives in its own object) so the pagination rules can be validated on the JVM.
 * The full contract — why the paginator must subtract the parent's padding, why
 * text and illustrations never share a spread, and why illustration spreads
 * cannot be normalised by page parity — is in
 * docs/reader-horizontal-spread.md.
 *
 * The reader numbers every text page and every illustration with its own page
 * number; a *spread* groups two of them onto one screen in double-page mode.
 * Text pages only ever pair with text pages and illustrations only with
 * illustrations, because one page child renders one kind: a section of odd
 * length therefore ends with a half-empty spread instead of a mixed one.
 */
object HorizontalPageGeometry {

    /** The page numbers one screen shows: [start], plus an optional right-hand page. */
    data class Spread(val start: Int, val second: Int?)

    /**
     * The spread that begins at [page].
     *
     * [second] is null in single-page mode, on the last page of a section with
     * an odd length, and past the end of the chapter.
     */
    fun spreadAt(page: Int, textPageCount: Int, totalPages: Int, doublePage: Boolean): Spread {
        if (!doublePage || page < 1) return Spread(page, null)
        val second = if (page <= textPageCount) {
            (page + 1).takeIf { it <= textPageCount }
        } else {
            (page + 1).takeIf { it <= totalPages }
        }
        return Spread(page, second)
    }

    /**
     * The first page of the spread containing [page]; [page] itself when
     * single-page mode is off.
     *
     * Text spreads start on odd page numbers because pairing is greedy from page
     * one. Illustration spreads are paired inside the illustration section, so
     * their start depends on where that section begins — an odd text-page count
     * puts the first illustration on an even page number, and normalising by the
     * raw page parity would then drop that illustration.
     */
    fun spreadStartContaining(page: Int, textPageCount: Int, doublePage: Boolean): Int {
        if (!doublePage || page < 1) return page
        return if (page <= textPageCount) {
            if (page % 2 == 0) page - 1 else page
        } else {
            val imageIndex = page - textPageCount - 1
            textPageCount + 1 + (imageIndex - imageIndex % 2)
        }
    }

    /** How far one "next page" moves: past the whole spread, or a single page. */
    fun forwardStep(page: Int, textPageCount: Int, totalPages: Int, doublePage: Boolean): Int =
        if (spreadAt(page, textPageCount, totalPages, doublePage).second != null) 2 else 1

    /**
     * The page a "previous page" lands on, or 0 when the current page is the
     * chapter's first — the caller turns that into "previous chapter".
     */
    fun previousStart(page: Int, textPageCount: Int, doublePage: Boolean): Int {
        if (!doublePage) return page - 1
        val candidate = page - 1
        if (candidate < 1) return 0
        return spreadStartContaining(candidate, textPageCount, true)
    }

    /**
     * The height the paginator may fill.
     *
     * The parent's own padding is subtracted because the page child is laid out
     * *inside* it: the child is that much shorter than the parent. Paginating
     * against the unpadded height is what let a page hold more lines than fit,
     * which pushed the first line above the page top once the page was centred.
     */
    fun contentHeight(
        viewportHeight: Int,
        paddingTop: Int,
        paddingBottom: Int,
        bottomInset: Float,
        footerReserve: Float,
        minimum: Float
    ): Float = (viewportHeight - paddingTop - paddingBottom - bottomInset - footerReserve)
        .coerceAtLeast(minimum)

    /**
     * The reading fraction to restore after a re-pagination, as a page number.
     *
     * Carrying the fraction rather than the page number is what keeps the reader
     * on the same sentence when the pages are cut differently.
     */
    fun pageForFraction(fraction: Float, pageCount: Int): Int {
        if (pageCount <= 0) return 0
        return (fraction.coerceIn(0f, 1f) * pageCount).toInt().coerceIn(1, pageCount)
    }
}
