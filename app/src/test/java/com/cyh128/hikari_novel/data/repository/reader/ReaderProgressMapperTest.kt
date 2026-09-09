package com.cyh128.hikari_novel.data.repository.reader

import com.cyh128.hikari_novel.data.model.ChapterRef
import com.cyh128.hikari_novel.data.model.ReaderAnchor
import com.cyh128.hikari_novel.data.source.local.database.reader.ReaderProgressEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderProgressMapperTest {
    private val ref = ChapterRef("book", "c1", 1, 2, "Chapter")

    @Test
    fun legacyPercentBecomesCharacterAnchorWhenContentExists() {
        val progress = ReaderProgressMapper.fromLegacy(ref, "abcd\nefgh", 50, 42L)

        assertEquals(ref.cid, progress.cid)
        assertEquals(ref.aid, progress.aid)
        assertEquals(ref.volumeIndex, progress.volumeIndex)
        assertEquals(ref.chapterIndex, progress.chapterIndex)
        assertEquals(4, progress.charOffset)
        assertEquals(50, progress.normalizedPercent)
        assertEquals(42L, progress.updatedAt)
    }

    @Test
    fun legacyPercentIsClampedWithoutContent() {
        val progress = ReaderProgressMapper.fromLegacy(ref, null, 150, 42L)

        assertEquals(0, progress.charOffset)
        assertEquals(100, progress.normalizedPercent)
    }

    @Test
    fun malformedAnchorFieldsAreNormalized() {
        val normalized = ReaderProgressMapper.normalize(
            ReaderAnchor("other", -1, -4, 150, -2),
            ref.cid
        )

        assertEquals(ref.cid, normalized.cid)
        assertEquals(0, normalized.paragraphIndex)
        assertEquals(0, normalized.charOffset)
        assertEquals(100, normalized.normalizedPercent)
        assertEquals(0, normalized.imageIndex)
    }

    @Test
    fun persistedOffsetIsClampedToUpdatedContent() {
        val stored = ReaderProgressEntity(
            cid = ref.cid,
            aid = ref.aid,
            volumeIndex = ref.volumeIndex,
            chapterIndex = ref.chapterIndex,
            paragraphIndex = 99,
            charOffset = 99,
            normalizedPercent = 99,
            imageIndex = null,
            updatedAt = 1L,
            isLatest = true
        )

        val clamped = ReaderProgressMapper.clamp(stored, "abc\ndef")

        assertEquals(7, clamped.charOffset)
        assertEquals(100, clamped.normalizedPercent)
        assertEquals(1, clamped.paragraphIndex)
    }
}
