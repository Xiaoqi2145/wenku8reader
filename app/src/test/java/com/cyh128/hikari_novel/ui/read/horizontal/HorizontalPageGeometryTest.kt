package com.cyh128.hikari_novel.ui.read.horizontal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The horizontal reader's spread and pagination rules.
 *
 * Text pages and illustrations are numbered in one sequence (text first, then
 * images), but they only ever pair with their own kind. These cases pin that
 * down, plus the two geometry fixes: the paginator must budget the padded box
 * the renderer actually draws into, and a re-pagination must restore a fraction
 * rather than a page number.
 */
class HorizontalPageGeometryTest {

    // ===== Spreads =====

    @Test
    fun singlePageModeNeverPairsPages() {
        val spread = HorizontalPageGeometry.spreadAt(
            page = 1, textPageCount = 10, totalPages = 12, doublePage = false
        )
        assertEquals(1, spread.start)
        assertNull(spread.second)
    }

    @Test
    fun textSpreadPairsConsecutiveTextPages() {
        val spread = HorizontalPageGeometry.spreadAt(
            page = 3, textPageCount = 10, totalPages = 12, doublePage = true
        )
        assertEquals(3, spread.start)
        assertEquals(4, spread.second)
    }

    @Test
    fun lastTextPageOfAnOddSectionDoesNotPairWithTheFirstImage() {
        // 5 text pages: page 5 is the odd one out. Pairing it with image page 6
        // would mix a text page and an illustration on one screen, which the
        // page children cannot render.
        val spread = HorizontalPageGeometry.spreadAt(
            page = 5, textPageCount = 5, totalPages = 9, doublePage = true
        )
        assertEquals(5, spread.start)
        assertNull(spread.second)
    }

    @Test
    fun illustrationsPairWithinTheirOwnSection() {
        val spread = HorizontalPageGeometry.spreadAt(
            page = 6, textPageCount = 5, totalPages = 9, doublePage = true
        )
        assertEquals(6, spread.start)
        assertEquals(7, spread.second)
    }

    @Test
    fun anOddIllustrationSectionLeavesTheLastSpreadHalfEmpty() {
        val spread = HorizontalPageGeometry.spreadAt(
            page = 8, textPageCount = 5, totalPages = 8, doublePage = true
        )
        assertEquals(8, spread.start)
        assertNull(spread.second)
    }

    @Test
    fun spreadsNeverRunPastTheChapter() {
        val spread = HorizontalPageGeometry.spreadAt(
            page = 9, textPageCount = 5, totalPages = 9, doublePage = true
        )
        assertNull(spread.second)
    }

    // ===== Spread starts =====

    @Test
    fun anEvenTextPageNormalisesBackToItsSpreadStart() {
        assertEquals(
            3,
            HorizontalPageGeometry.spreadStartContaining(
                page = 4, textPageCount = 10, doublePage = true
            )
        )
    }

    @Test
    fun anIllustrationNormalisesWithinTheIllustrationSection() {
        // 5 text pages put the first illustration on page 6, so the second
        // illustration (page 7) is the right half of the spread starting at 6.
        // Normalising by the raw page parity would have returned 7 and hidden
        // that illustration behind its own left half.
        assertEquals(
            6,
            HorizontalPageGeometry.spreadStartContaining(
                page = 7, textPageCount = 5, doublePage = true
            )
        )
        assertEquals(
            8,
            HorizontalPageGeometry.spreadStartContaining(
                page = 9, textPageCount = 5, doublePage = true
            )
        )
    }

    @Test
    fun anOddIllustrationIndexIsNotNormalisedAway() {
        // textPageCount 6 puts images on 7,8. Page 7 is the left half of the
        // spread (7,8) and must stay put.
        assertEquals(
            7,
            HorizontalPageGeometry.spreadStartContaining(
                page = 7, textPageCount = 6, doublePage = true
            )
        )
    }

    @Test
    fun normalisationIsIdentityInSinglePageMode() {
        assertEquals(
            4,
            HorizontalPageGeometry.spreadStartContaining(
                page = 4, textPageCount = 10, doublePage = false
            )
        )
    }

    // ===== Navigation =====

    @Test
    fun forwardStepSkipsAWholeSpreadWhenOneIsShown() {
        assertEquals(
            2,
            HorizontalPageGeometry.forwardStep(
                page = 3, textPageCount = 10, totalPages = 12, doublePage = true
            )
        )
    }

    @Test
    fun forwardStepIsOneAtTheEndOfATextSection() {
        assertEquals(
            1,
            HorizontalPageGeometry.forwardStep(
                page = 5, textPageCount = 5, totalPages = 9, doublePage = true
            )
        )
    }

    @Test
    fun previousFromTheFirstIllustrationReturnsToTheLastTextSpread() {
        // 5 text pages + images: going back from image page 6 must land on the
        // text spread that contains page 5, not on page 4.
        assertEquals(
            5,
            HorizontalPageGeometry.previousStart(page = 6, textPageCount = 5, doublePage = true)
        )
    }

    @Test
    fun previousFromTheFirstPageAsksForThePreviousChapter() {
        assertEquals(
            0,
            HorizontalPageGeometry.previousStart(page = 1, textPageCount = 5, doublePage = true)
        )
    }

    @Test
    fun previousWalksBackOnePageInSinglePageMode() {
        assertEquals(
            4,
            HorizontalPageGeometry.previousStart(page = 5, textPageCount = 10, doublePage = false)
        )
    }

    // ===== Content height =====

    @Test
    fun contentHeightSubtractsTheParentPaddingTheRendererIsLaidOutInside() {
        // The regression: the paginator used the unpadded view height, so it
        // packed in more lines than the child could draw. Centring the block then
        // pushed the first line above the page top.
        val withPadding = HorizontalPageGeometry.contentHeight(
            viewportHeight = 1000,
            paddingTop = 120,
            paddingBottom = 60,
            bottomInset = 36f,
            footerReserve = 80f,
            minimum = 100f
        )
        assertEquals(704f, withPadding, 0.01f)
    }

    @Test
    fun contentHeightNeverCollapsesBelowOneLine() {
        assertEquals(
            110f,
            HorizontalPageGeometry.contentHeight(
                viewportHeight = 100,
                paddingTop = 120,
                paddingBottom = 60,
                bottomInset = 36f,
                footerReserve = 80f,
                minimum = 110f
            ),
            0.01f
        )
    }

    // ===== Fraction restore =====

    @Test
    fun aFractionMapsOntoTheNewPageCount() {
        assertEquals(5, HorizontalPageGeometry.pageForFraction(0.5f, 10))
    }

    @Test
    fun aFractionNeverFallsOutsideTheChapter() {
        assertEquals(1, HorizontalPageGeometry.pageForFraction(0f, 10))
        assertEquals(10, HorizontalPageGeometry.pageForFraction(1f, 10))
        // A page cut into fewer pages keeps the reader inside the new range.
        assertEquals(4, HorizontalPageGeometry.pageForFraction(1f, 4))
    }

    @Test
    fun anEmptyChapterHasNoPage() {
        assertEquals(0, HorizontalPageGeometry.pageForFraction(0.5f, 0))
    }

    @Test
    fun readingPositionSurvivesAChangeInPageCount() {
        // The reader was 3 pages into a 10-page chapter (30%). Re-cut into 5
        // pages, the same text is at page 2 rather than page 3.
        val fraction = 3f / 10f
        assertEquals(1, HorizontalPageGeometry.pageForFraction(fraction, 5))
        assertEquals(3, HorizontalPageGeometry.pageForFraction(fraction, 10))
    }
}
