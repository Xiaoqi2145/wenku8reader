package com.cyh128.hikari_novel.ui.read.vertical

import com.cyh128.hikari_novel.data.model.CachedChapter
import com.cyh128.hikari_novel.data.model.ChapterRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class VerticalRenderModelTest {
    @Test
    fun modelKeepsPrefixUtf16LengthAndImageOrder() {
        val chapter = CachedChapter(
            ref = ChapterRef("book-1", "c1", 0, 0, "第一章😀"),
            content = "正文😀\n结尾",
            images = listOf("image://one", "image://two"),
            updatedAt = 0L
        )
        val model = renderModel(chapter)

        assertEquals("\n\n第一章😀\n\n", model.prefix)
        assertEquals(model.prefix.length, model.prefixLength)
        assertEquals(model.prefix.length + chapter.content.length, model.text.length)
        assertEquals(chapter.images, model.images)
        assertSame(chapter, model.chapter)
    }

    @Test
    fun emptyChapterStillRendersItsTitlePrefix() {
        val chapter = CachedChapter(
            ref = ChapterRef("book-1", "empty", 0, 1, "Empty"),
            content = "",
            images = emptyList(),
            updatedAt = 0L
        )
        val model = renderModel(chapter)

        assertEquals(model.prefix, model.text)
        assertEquals(model.prefix.length, model.text.length)
    }

    private fun renderModel(chapter: CachedChapter): VerticalRenderModel =
        VerticalRenderModelFactory.create(chapter)
}
