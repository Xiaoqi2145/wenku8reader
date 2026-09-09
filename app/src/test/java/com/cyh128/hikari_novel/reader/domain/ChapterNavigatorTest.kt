package com.cyh128.hikari_novel.reader.domain

import com.cyh128.hikari_novel.data.model.ChapterRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterNavigatorTest {
    private fun chapter(volume: Int, index: Int): ChapterRef = ChapterRef(
        aid = "book-1",
        cid = "v" + volume + "c" + index,
        volumeIndex = volume,
        chapterIndex = index,
        title = "Volume " + volume + " Chapter " + index
    )

    @Test
    fun nextAndPreviousSkipEmptyVolumes() {
        val first = chapter(0, 0)
        val thirdVolumeFirst = chapter(3, 0)
        val thirdVolumeLast = chapter(3, 1)
        val navigator = ChapterNavigator(
            listOf(
                listOf(first),
                emptyList(),
                emptyList(),
                listOf(thirdVolumeFirst, thirdVolumeLast),
                emptyList()
            )
        )

        assertEquals(thirdVolumeFirst, navigator.next(first))
        assertEquals(first, navigator.previous(thirdVolumeFirst))
        assertEquals(thirdVolumeLast, navigator.last()?.chapter)
        assertEquals(first, navigator.first()?.chapter)
    }

    @Test
    fun boundariesAndInvalidPositionsReturnNull() {
        val first = chapter(0, 0)
        val last = chapter(0, 1)
        val navigator = ChapterNavigator(listOf(listOf(first, last)))

        assertNull(navigator.previous(first))
        assertNull(navigator.next(last))
        assertNull(navigator.next(ChapterPosition(4, 0)))
        assertNull(navigator.next(first.copy(volumeIndex = 4)))
        assertNull(navigator.locate("missing"))
        assertEquals(2, navigator.chapterCount)
    }

    @Test
    fun allEmptyVolumesHaveNoNavigation() {
        val navigator = ChapterNavigator(listOf(emptyList(), emptyList()))

        assertNull(navigator.first())
        assertNull(navigator.last())
        assertEquals(0, navigator.chapterCount)
    }
}
