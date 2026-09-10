package com.cyh128.hikari_novel.ui.read.vertical

import com.cyh128.hikari_novel.data.model.CachedChapter
import com.cyh128.hikari_novel.data.model.ChapterRef
import com.cyh128.hikari_novel.data.model.ReaderAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerticalReaderControllerTest {
    @Test
    fun startAndAppendKeepMapperOffsetsStable() {
        val controller = VerticalReaderController()
        val first = controller.start(chapter("c1", 0, "First", "a😀\nb"))
        val firstAnchorOffset = controller.globalOffset(ReaderAnchor("c1", 0, 3, 0))
        val second = controller.append(chapter("c2", 1, "Second", "tail"))

        assertEquals("\n\nFirst\n\na😀\nb", first.renderedText)
        assertEquals(first.block.startOffset + 3, firstAnchorOffset)
        assertEquals(first.block.endOffsetExclusive + second.prefix.length, second.block.startOffset)
        assertEquals("c1", controller.chapterAt(firstAnchorOffset!!)!!.ref.cid)
        assertEquals("c2", controller.chapterAt(second.block.startOffset)!!.ref.cid)
        assertEquals(2, controller.blocks.size)
    }

    @Test
    fun startClearsManualJumpAndRequestGuard() {
        val controller = VerticalReaderController()
        controller.start(chapter("old", 0, "Old", "old"))
        controller.markNextChapterRequested()

        assertFalse(controller.canRequestNextChapter())
        controller.start(chapter("new", 0, "New", "new"))

        assertTrue(controller.canRequestNextChapter())
        assertFalse(controller.contains("old"))
        assertTrue(controller.contains("new"))
        assertEquals("new", controller.anchorAt(controller.blocks.single().startOffset)!!.cid)
    }

    @Test
    fun emptyChapterStillHasPrefixBoundaryAndImagesAreRetained() {
        val chapter = chapter("empty", 0, "Empty", "", listOf("image://one"))
        val render = VerticalReaderController().start(chapter)

        assertEquals(render.prefix.length, render.block.startOffset)
        assertEquals(render.block.startOffset, render.block.endOffsetExclusive)
        assertEquals(listOf("image://one"), render.chapter.images)
    }

    @Test
    fun automaticSessionStopsAtConfiguredChapterLimit() {
        val controller = VerticalReaderController(maxLoadedChapters = 2)
        controller.start(chapter("c1", 0, "One", "one"))
        controller.append(chapter("c2", 1, "Two", "two"))

        assertTrue(controller.sessionLimitReached)
        assertFalse(controller.canRequestNextChapter())
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateChapterIsRejectedByMapper() {
        val controller = VerticalReaderController()
        controller.start(chapter("same", 0, "Same", "one"))
        controller.append(chapter("same", 1, "Same again", "two"))
    }

    private fun chapter(
        cid: String,
        index: Int,
        title: String,
        content: String,
        images: List<String> = emptyList()
    ) = CachedChapter(
        ref = ChapterRef(
            aid = "book-1",
            cid = cid,
            volumeIndex = 0,
            chapterIndex = index,
            title = title
        ),
        content = content,
        images = images,
        updatedAt = 0L
    )
}
