package com.cyh128.hikari_novel.ui.read.vertical

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the paragraph boundary scan that drives paragraph spacing and
 * first-line indent spans. See docs/reader-koodo-alignment.md P0-3.
 */
class VerticalTypographyApplierTest {

    @Test
    fun splitsParagraphsAtNewlines() {
        assertEquals(
            listOf(0..0, 2..2, 4..4),
            VerticalTypographyApplier.paragraphRanges("a\nb\nc")
        )
    }

    @Test
    fun skipsBlankLinesBetweenParagraphs() {
        // Blank lines already render as an empty line; spacing on top of them
        // would double the visual break.
        assertEquals(
            listOf(0..0, 4..4),
            VerticalTypographyApplier.paragraphRanges("a\n\n\nb")
        )
    }

    @Test
    fun ignoresTheRendererHeadingPrefix() {
        // The vertical renderer prefixes every chapter with "\n\n<title>\n\n".
        assertEquals(
            listOf(2..3, 6..7),
            VerticalTypographyApplier.paragraphRanges("\n\n标题\n\n正文")
        )
    }

    @Test
    fun ignoresBlankOnlyText() {
        assertEquals(emptyList<IntRange>(), VerticalTypographyApplier.paragraphRanges(""))
        assertEquals(emptyList<IntRange>(), VerticalTypographyApplier.paragraphRanges("\n\n"))
    }

    @Test
    fun keepsATrailingParagraphWithoutNewline() {
        assertEquals(listOf(0..2), VerticalTypographyApplier.paragraphRanges("abc"))
    }
}
