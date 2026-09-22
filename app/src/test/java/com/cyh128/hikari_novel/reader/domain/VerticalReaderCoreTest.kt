package com.cyh128.hikari_novel.reader.domain

import com.cyh128.hikari_novel.data.model.ChapterRef
import com.cyh128.hikari_novel.data.model.ReaderAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VerticalReaderCoreTest {
    @Test
    fun progressSampleMapsToChapterAnchorAndPreservesImageIndex() {
        val mapper = ChapterBlockMapper()
        val first = mapper.append(ref("c1", 0), "a\nb", prefixLength = 2)
        val second = mapper.append(ref("c2", 1), "tail", prefixLength = 3)
        val calculator = VerticalProgressCalculator(mapper)

        val result = calculator.map(
            VerticalProgressSample(
                globalOffset = first.startOffset + 2,
                displayPercent = 140,
                scrollY = 72,
                imageIndex = 1
            )
        )

        assertEquals("c1", result?.chapter?.cid)
        assertEquals(2, result?.anchor?.charOffset)
        assertEquals(1, result?.anchor?.paragraphIndex)
        assertEquals(100, result?.displayPercent)
        assertEquals(1, result?.anchor?.imageIndex)
        assertEquals(second.startOffset, mapper.globalOffset(ReaderAnchor("c2", 0, 0, 0)))
    }

    @Test
    fun progressSampleHandlesHeadingGapsAndMissingChapters() {
        val mapper = ChapterBlockMapper()
        val first = mapper.append(ref("c1", 0), "one", prefixLength = 2)
        val second = mapper.append(ref("c2", 1), "two", prefixLength = 4)
        val calculator = VerticalProgressCalculator(mapper)

        val gap = calculator.map(VerticalProgressSample(second.startOffset - 1, 25))
        assertEquals("c1", gap?.chapter?.cid)
        assertEquals(3, gap?.anchor?.charOffset)
        val after = calculator.map(VerticalProgressSample(mapper.totalLength + 1, 50))
        assertEquals("c2", after?.chapter?.cid)
        assertEquals(3, after?.anchor?.charOffset)
        assertEquals(first.startOffset, mapper.globalOffset(ReaderAnchor("c1", 0, 0, 0)))
    }

    @Test
    fun scrollTargetUsesClampedAnchorOffsetAndRejectsUnknownCid() {
        val mapper = ChapterBlockMapper()
        val block = mapper.append(ref("c1", 0), "content")
        val calculator = VerticalScrollTargetCalculator(mapper)

        assertEquals(block.endOffsetExclusive, calculator.target(ReaderAnchor("c1", 0, 999, 99))?.globalOffset)
        assertNull(calculator.target(ReaderAnchor("missing", 0, 0, 0)))
    }

    /**
     * The three-zone split (left third = previous chapter, right third = next)
     * is gone. It mixed paging semantics into the scrolling reader, and because
     * the tap classifier only looks at the net displacement, a tap meant to
     * stop a fling could jump a chapter and take the persisted position with
     * it. The policy now takes no horizontal input at all — which is what this
     * test pins down: position must not be able to influence the decision.
     */
    @Test
    fun tapTogglesChromeOnlyWhenItIsATapNoInteractiveChildOwns() {
        assertEquals(
            true,
            VerticalTouchPolicy.shouldToggleReaderBar(
                isTap = true,
                startedOnInteractiveChild = false
            )
        )
        assertEquals(
            false,
            VerticalTouchPolicy.shouldToggleReaderBar(
                isTap = false,
                startedOnInteractiveChild = false
            )
        )
        assertEquals(
            false,
            VerticalTouchPolicy.shouldToggleReaderBar(
                isTap = true,
                startedOnInteractiveChild = true
            )
        )
        assertEquals(
            false,
            VerticalTouchPolicy.shouldToggleReaderBar(
                isTap = false,
                startedOnInteractiveChild = true
            )
        )
    }

    private fun ref(cid: String, index: Int) = ChapterRef(
        aid = "book-1",
        cid = cid,
        volumeIndex = 0,
        chapterIndex = index,
        title = "Chapter $index"
    )
}
