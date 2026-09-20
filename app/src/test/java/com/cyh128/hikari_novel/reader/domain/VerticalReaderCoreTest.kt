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

    @Test
    fun touchPolicyKeepsEdgesAndCenterDeterministic() {
        assertEquals(VerticalTapArea.Previous, VerticalTouchPolicy.tapArea(0f, 300))
        assertEquals(VerticalTapArea.Center, VerticalTouchPolicy.tapArea(100f, 300))
        assertEquals(VerticalTapArea.Center, VerticalTouchPolicy.tapArea(200f, 300))
        assertEquals(VerticalTapArea.Next, VerticalTouchPolicy.tapArea(299f, 300))
        assertEquals(true, VerticalTouchPolicy.shouldToggleReaderBar(150f, 300, true, false))
        assertEquals(false, VerticalTouchPolicy.shouldToggleReaderBar(150f, 300, false, false))
        assertEquals(false, VerticalTouchPolicy.shouldToggleReaderBar(150f, 300, true, true))
    }

    @Test
    fun tapActionSplitsTheScreenIntoThreeZones() {
        // P2-5: left third = previous chapter, right third = next, centre = chrome.
        assertEquals(VerticalTapArea.Previous, VerticalTouchPolicy.tapAction(0f, 300, true, false))
        assertEquals(VerticalTapArea.Center, VerticalTouchPolicy.tapAction(150f, 300, true, false))
        assertEquals(VerticalTapArea.Next, VerticalTouchPolicy.tapAction(299f, 300, true, false))
    }

    @Test
    fun tapActionIgnoresSwipesAndInteractiveChildren() {
        assertNull(VerticalTouchPolicy.tapAction(0f, 300, isTap = false, startedOnInteractiveChild = false))
        assertNull(VerticalTouchPolicy.tapAction(0f, 300, isTap = true, startedOnInteractiveChild = true))
        // A zero-width target must not throw or claim an edge zone.
        assertEquals(
            VerticalTapArea.Center,
            VerticalTouchPolicy.tapAction(0f, 0, isTap = true, startedOnInteractiveChild = false)
        )
    }

    @Test
    fun tapActionStaysConsistentWithShouldToggleReaderBar() {
        val widths = listOf(0, 1, 300, 1080)
        val xs = listOf(0f, 0.2f, 0.5f, 0.8f, 1f)
        for (width in widths) {
            for (ratio in xs) {
                val x = width * ratio
                val action = VerticalTouchPolicy.tapAction(x, width, true, false)
                assertEquals(
                    action == VerticalTapArea.Center,
                    VerticalTouchPolicy.shouldToggleReaderBar(x, width, true, false)
                )
            }
        }
    }

    private fun ref(cid: String, index: Int) = ChapterRef(
        aid = "book-1",
        cid = cid,
        volumeIndex = 0,
        chapterIndex = index,
        title = "Chapter $index"
    )
}
