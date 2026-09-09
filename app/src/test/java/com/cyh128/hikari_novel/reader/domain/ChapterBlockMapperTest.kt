package com.cyh128.hikari_novel.reader.domain

import com.cyh128.hikari_novel.data.model.ChapterRef
import com.cyh128.hikari_novel.data.model.ReaderAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterBlockMapperTest {
    private fun chapter(cid: String, index: Int) = ChapterRef(
        aid = "book-1",
        cid = cid,
        volumeIndex = 0,
        chapterIndex = index,
        title = "Chapter $index"
    )

    @Test
    fun emptyMapperReturnsNull() {
        val mapper = ChapterBlockMapper()

        assertEquals(0, mapper.totalLength)
        assertNull(mapper.chapterAt(0))
        assertNull(mapper.anchorAt(0))
        assertNull(mapper.globalOffset(ReaderAnchor("missing", 0, 0, 0)))
    }

    @Test
    fun mapsPrefixedAndAppendedChapters() {
        val first = chapter("c1", 0)
        val second = chapter("c2", 1)
        val mapper = ChapterBlockMapper()

        val firstBlock = mapper.append(first, "a" + 10.toChar() + "bc", prefixLength = 2)
        val firstAnchorOffset = mapper.globalOffset(ReaderAnchor("c1", 0, 2, 0))
        val secondBlock = mapper.append(second, "de", prefixLength = 3)

        assertEquals(2, firstBlock.startOffset)
        assertEquals(6, firstBlock.endOffsetExclusive)
        assertEquals(9, secondBlock.startOffset)
        assertEquals(11, mapper.totalLength)
        assertEquals(4, firstAnchorOffset)
        assertEquals(firstAnchorOffset, mapper.globalOffset(ReaderAnchor("c1", 0, 2, 0)))
        assertEquals("c1", mapper.chapterAt(6)?.ref?.cid)
        assertEquals(4, mapper.chapterAt(6)?.localOffset)
        assertEquals("c2", mapper.chapterAt(9)?.ref?.cid)
        assertEquals(0, mapper.chapterAt(9)?.localOffset)
    }

    @Test
    fun mapsParagraphsAndClampsGlobalOffsets() {
        val mapper = ChapterBlockMapper()
        mapper.append(chapter("c1", 0), "a" + 10.toChar() + "bc", prefixLength = 2)

        val middle = mapper.anchorAt(4)
        assertEquals("c1", middle?.cid)
        assertEquals(1, middle?.paragraphIndex)
        assertEquals(2, middle?.charOffset)
        assertEquals(50, middle?.normalizedPercent)

        val before = mapper.chapterAt(-10)
        assertEquals(0, before?.localOffset)
        assertEquals(0, before?.normalizedPercent)
        val after = mapper.chapterAt(100)
        assertEquals(4, after?.localOffset)
        assertEquals(100, after?.normalizedPercent)
    }

    @Test
    fun emptyChapterStillHasAStableBoundary() {
        val mapper = ChapterBlockMapper()
        mapper.append(chapter("empty", 0), "", prefixLength = 2)

        assertEquals("empty", mapper.chapterAt(2)?.ref?.cid)
        assertEquals(0, mapper.chapterAt(2)?.localOffset)
        assertEquals("empty", mapper.anchorAt(2)?.cid)
    }

    @Test
    fun appendingChapterDoesNotMoveExistingAnchor() {
        val mapper = ChapterBlockMapper()
        val first = chapter("c1", 0)
        mapper.append(first, "first")
        val before = mapper.globalOffset(ReaderAnchor("c1", 0, 3, 75))

        mapper.append(chapter("c2", 1), "second", prefixLength = 4)

        assertEquals(before, mapper.globalOffset(ReaderAnchor("c1", 0, 3, 75)))
    }

    @Test
    fun persistedAnchorIsClampedToCurrentContent() {
        val mapper = ChapterBlockMapper()
        mapper.append(chapter("c1", 0), "a" + 10.toChar() + "bc")

        val clamped = mapper.clamp(ReaderAnchor("c1", 99, 100, 99))

        assertEquals(4, clamped?.charOffset)
        assertEquals(1, clamped?.paragraphIndex)
        assertEquals(100, clamped?.normalizedPercent)
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateChapterCidIsRejected() {
        val mapper = ChapterBlockMapper()
        mapper.append(chapter("same", 0), "one")
        mapper.append(chapter("same", 1), "two")
    }

    @Test
    fun clearRemovesOldChapterAfterManualJump() {
        val mapper = ChapterBlockMapper()
        mapper.append(chapter("old", 0), "old")
        mapper.clear()
        mapper.append(chapter("new", 0), "new")

        assertNull(mapper.globalOffset(ReaderAnchor("old", 0, 1, 50)))
        assertEquals("new", mapper.anchorAt(0)?.cid)
    }
}
