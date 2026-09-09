package com.cyh128.hikari_novel

import com.cyh128.hikari_novel.util.ReaderAnchorMapper
import com.cyh128.hikari_novel.util.Wenku8Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase-one behavior baseline. These tests intentionally describe the
 * currently supported parser and anchor behavior before UI refactoring.
 */
class ReaderBehaviorTest {
    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/fixtures/$name")!!
            .bufferedReader()
            .use { it.readText() }

    @Test
    fun emptyAndShortChaptersRemainSafe() {
        assertEquals("", Wenku8Parser.parseReaderPage(fixture("reader_empty.html")))
        assertEquals("短章节。", Wenku8Parser.parseReaderPage(fixture("reader_short.html")))
    }

    @Test
    fun longChapterFixturePreservesOrderedText() {
        val content = fixture("reader_long.txt")

        assertTrue(content.length > 1_000)
        assertTrue(content.indexOf("长章节开始") < content.indexOf("长章节结束"))
        assertTrue(content.lineSequence().count() > 20)
    }

    @Test
    fun imageChapterKeepsTextAndImageReference() {
        val html = fixture("reader_images.html")
        val content = Wenku8Parser.parseReaderPage(html)
        val images = Wenku8Parser.getImagesFromReaderPage(html)

        assertTrue(content.contains("正文"))
        assertTrue(content.contains("结尾"))
        assertEquals(1, images.size)
        assertTrue(images.single().startsWith("https://pic.777743.xyz/"))
    }

    @Test
    fun emptyContentProducesZeroAnchor() {
        val anchor = ReaderAnchorMapper.fromPercent("empty", "", 75)

        assertEquals(0, anchor.paragraphIndex)
        assertEquals(0, anchor.charOffset)
        assertEquals(0, anchor.normalizedPercent)
    }

    @Test
    fun beginningMiddleAndEndAnchorsAreStable() {
        val content = fixture("reader_anchor.txt")

        val beginning = ReaderAnchorMapper.fromPercent("c1", content, -10)
        val middle = ReaderAnchorMapper.fromPercent("c1", content, 50)
        val end = ReaderAnchorMapper.fromPercent("c1", content, 120)

        assertEquals(0, beginning.charOffset)
        assertEquals(0, beginning.normalizedPercent)
        assertEquals(50, middle.normalizedPercent)
        assertEquals(content.length, end.charOffset)
        assertEquals(100, end.normalizedPercent)
        assertEquals(end, ReaderAnchorMapper.clamp(end, content))
    }
}
