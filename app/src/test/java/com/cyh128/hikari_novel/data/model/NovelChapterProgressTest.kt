package com.cyh128.hikari_novel.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the shared "x/y" chapter counter used by both reader orientations.
 *
 * Regression note: the vertical ViewModel used to build this label inline and
 * accidentally wrapped it in backticks, which rendered literally in the status
 * line. See docs/reader-koodo-alignment.md P1-2.
 */
class NovelChapterProgressTest {

    private fun chapter(cid: String) = Chapter("第${cid}章", "url/$cid", cid)

    private val novel = Novel(
        aid = "book",
        volume = listOf(
            Volume("v1", listOf(chapter("a"), chapter("b"))),
            Volume("v2", listOf(chapter("c")))
        )
    )

    @Test
    fun countsChaptersAcrossVolumes() {
        assertEquals("1/3", novel.chapterProgressLabel(0, 0))
        assertEquals("2/3", novel.chapterProgressLabel(0, 1))
        assertEquals("3/3", novel.chapterProgressLabel(1, 0))
    }

    @Test
    fun containsNoFormattingCharacters() {
        assertEquals(true, novel.chapterProgressLabel(0, 0).none { !it.isDigit() && it != '/' })
    }

    @Test
    fun fallsBackWhenThePositionIsOutOfRange() {
        assertEquals("0/0", novel.chapterProgressLabel(9, 9))
        assertEquals("0/0", Novel("empty", emptyList()).chapterProgressLabel(0, 0))
    }
}
