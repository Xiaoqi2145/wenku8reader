package com.cyh128.hikari_novel

import com.cyh128.hikari_novel.util.ReaderAnchorMapper
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderAnchorTest {
    @Test fun percentAnchorIsClamped() {
        val anchor = ReaderAnchorMapper.fromPercent("c1", "abcd\nefgh", 150)
        assertEquals(100, anchor.normalizedPercent)
        assertEquals(9, anchor.charOffset)
    }

    @Test fun oldAnchorBeyondNewContentIsSafe() {
        val anchor = ReaderAnchorMapper.clamp(
            com.cyh128.hikari_novel.data.model.ReaderAnchor("c1", 9, 100, 99), "abc\ndef"
        )
        assertEquals(7, anchor.charOffset)
        assertEquals(100, anchor.normalizedPercent)
    }
}
