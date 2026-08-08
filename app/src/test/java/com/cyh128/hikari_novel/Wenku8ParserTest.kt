package com.cyh128.hikari_novel

import com.cyh128.hikari_novel.util.Wenku8Parser
import org.junit.Assert.assertTrue
import org.junit.Test

class Wenku8ParserTest {
    @Test fun parsesReaderTextFixture() {
        val html = javaClass.getResourceAsStream("/fixtures/reader_simple.html")!!
            .bufferedReader().readText()
        val content = Wenku8Parser.parseReaderPage(html)
        assertTrue(content.contains("这是第一段"))
        assertTrue(content.contains("这是第二段"))
    }

    @Test fun parsesReaderImagesFixture() {
        val html = javaClass.getResourceAsStream("/fixtures/reader_images.html")!!
            .bufferedReader().readText()
        assertTrue(Wenku8Parser.getImagesFromReaderPage(html).isNotEmpty())
    }
}
