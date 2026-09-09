package com.cyh128.hikari_novel.reader.domain

import com.cyh128.hikari_novel.data.model.CachedChapter
import com.cyh128.hikari_novel.data.model.ChapterRef
import com.cyh128.hikari_novel.ui.read.vertical.VerticalRenderModelFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class VerticalRenderModelFactoryTest {
    @Test
    fun createsStablePrefixAndUtf16Text() {
        val chapter = CachedChapter(
            ref = ChapterRef("book", "c1", 0, 0, "标题😀"),
            content = "正文😀",
            images = listOf("one"),
            updatedAt = 0L
        )
        val model = VerticalRenderModelFactory.create(chapter)
        assertEquals("\n\n标题😀\n\n", model.prefix)
        assertEquals(model.prefix.length, model.prefixLength)
        assertEquals(model.prefix + chapter.content, model.text)
        assertEquals(chapter.images, model.images)
    }
}
