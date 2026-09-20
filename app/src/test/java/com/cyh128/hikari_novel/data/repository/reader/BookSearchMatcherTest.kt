package com.cyh128.hikari_novel.data.repository.reader

import com.cyh128.hikari_novel.data.model.ChapterRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the in-book search matching rules. See
 * docs/reader-koodo-alignment.md P1-4.
 */
class BookSearchMatcherTest {

    private val ref = ChapterRef("book", "c1", 0, 0, "第一章")

    @Test
    fun findsEveryOccurrenceInChapterLocalOffsets() {
        val content = "abXYabXYab"
        assertEquals(listOf(0, 4, 8), BookSearchMatcher.findOffsets(content, "ab"))
    }

    @Test
    fun matchesCaseInsensitively() {
        assertEquals(listOf(0, 6), BookSearchMatcher.findOffsets("Hello hello", "hello"))
    }

    @Test
    fun matchesChineseProse() {
        val content = "他抬起头，看见了那片星空。星空之下，无人说话。"
        // 星 at 10 and 13 (each CJK char is one UTF-16 unit here).
        assertEquals(listOf(10, 13), BookSearchMatcher.findOffsets(content, "星空"))
    }

    @Test
    fun capsHitsPerChapter() {
        val content = "a".repeat(BookSearchMatcher.MAX_HITS_PER_CHAPTER * 3)
        assertEquals(
            BookSearchMatcher.MAX_HITS_PER_CHAPTER,
            BookSearchMatcher.findOffsets(content, "a").size
        )
    }

    @Test
    fun returnsNothingWhenTheQueryDoesNotOccur() {
        assertTrue(BookSearchMatcher.findOffsets("some content", "").isEmpty())
        assertTrue(BookSearchMatcher.findOffsets("some content", "zzz").isEmpty())
    }

    @Test
    fun normalizesTheQuery() {
        assertEquals("星空", BookSearchMatcher.normalizeQuery("  星空 \n"))
        assertEquals("", BookSearchMatcher.normalizeQuery("   "))
    }

    @Test
    fun snippetKeepsContextAndFlattensNewlines() {
        val content = "第一行\n第二行\n目标词在这里\n第四行"
        val offset = content.indexOf("目标词")
        val snippet = BookSearchMatcher.snippet(content, offset, 3)
        assertEquals("第一行 第二行 目标词在这里 第四行", snippet)
    }

    @Test
    fun snippetAddsEllipsisOnlyWhenTruncated() {
        val long = "x".repeat(100) + "needle" + "y".repeat(100)
        val snippet = BookSearchMatcher.snippet(long, 100, "needle".length)
        assertTrue(snippet.startsWith("…"))
        assertTrue(snippet.endsWith("…"))
        assertTrue(snippet.contains("needle"))

        val exact = "needle"
        assertEquals("needle", BookSearchMatcher.snippet(exact, 0, exact.length))
    }

    @Test
    fun hitsCarryTheChapterRefAndMatchLength() {
        val hits = BookSearchMatcher.hits(ref, "看星空，看星空", "星空")
        assertEquals(2, hits.size)
        assertEquals(ref, hits[0].ref)
        assertEquals(2, hits[0].matchLength)
        assertEquals(1, hits[0].charOffset)
    }
}
