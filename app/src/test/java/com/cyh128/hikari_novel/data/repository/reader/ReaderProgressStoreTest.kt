package com.cyh128.hikari_novel.data.repository.reader

import com.cyh128.hikari_novel.data.model.ChapterRef
import com.cyh128.hikari_novel.data.model.ReaderAnchor
import com.cyh128.hikari_novel.data.model.ReaderOrientation
import com.cyh128.hikari_novel.data.source.local.database.read_history.horizontal_read_history.HorizontalReadHistoryEntity
import com.cyh128.hikari_novel.data.source.local.database.read_history.vertical_read_history.VerticalReadHistoryEntity
import com.cyh128.hikari_novel.data.source.local.database.reader.ReaderProgressEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderProgressStoreTest {
    private val ref = ChapterRef("book", "c1", 1, 2, "Chapter")

    private fun oldVertical(
        aid: String = ref.aid,
        percent: Int = 40,
        location: Int = 222
    ) = VerticalReadHistoryEntity(
        cid = ref.cid,
        aid = aid,
        volume = ref.volumeIndex,
        chapter = ref.chapterIndex,
        location = location,
        progressPercent = percent,
        isLatest = true
    )

    private fun oldHorizontal(
        aid: String = ref.aid,
        percent: Int = 40,
        location: Int = 3
    ) = HorizontalReadHistoryEntity(
        cid = ref.cid,
        aid = aid,
        volume = ref.volumeIndex,
        chapter = ref.chapterIndex,
        location = location,
        progressPercent = percent,
        isLatest = true
    )

    @Test
    fun legacyVerticalProgressMigratesWhenContentIsAvailable() = runBlocking {
        val readerDao = FakeReaderDao()
        val verticalDao = FakeVerticalReadHistoryDao().apply { item = oldVertical() }
        val horizontalDao = FakeHorizontalReadHistoryDao()
        val store = RoomReaderProgressStore(readerDao, verticalDao, horizontalDao)

        val progress = store.get(ref, "abcd\nefghij", ReaderOrientation.Vertical)

        assertEquals(4, progress?.charOffset)
        assertEquals(40, progress?.normalizedPercent)
        assertEquals(4, readerDao.progress?.charOffset)
        assertTrue(verticalDao.writes.isEmpty())
    }

    @Test
    fun legacyHorizontalProgressMigratesWhenContentIsAvailable() = runBlocking {
        val readerDao = FakeReaderDao()
        val horizontalDao = FakeHorizontalReadHistoryDao().apply { item = oldHorizontal(percent = 60) }
        val store = RoomReaderProgressStore(
            readerDao,
            FakeVerticalReadHistoryDao(),
            horizontalDao
        )

        val progress = store.get(ref, "0123456789", ReaderOrientation.Horizontal)

        assertEquals(6, progress?.charOffset)
        assertEquals(60, progress?.normalizedPercent)
        assertEquals(6, readerDao.progress?.charOffset)
    }

    @Test
    fun contentlessLookupDoesNotPersistPlaceholderOffset() = runBlocking {
        val readerDao = FakeReaderDao()
        val verticalDao = FakeVerticalReadHistoryDao().apply { item = oldVertical(percent = 75) }
        val store = RoomReaderProgressStore(readerDao, verticalDao, FakeHorizontalReadHistoryDao())

        val progress = store.get(ref, content = null, orientation = ReaderOrientation.Vertical)

        assertEquals(75, progress?.normalizedPercent)
        assertEquals(0, progress?.charOffset)
        assertNull(readerDao.progress)
    }

    @Test
    fun emptyContentLookupDoesNotPersistPlaceholderOffset() = runBlocking {
        val readerDao = FakeReaderDao()
        val verticalDao = FakeVerticalReadHistoryDao().apply { item = oldVertical(percent = 75) }
        val store = RoomReaderProgressStore(readerDao, verticalDao, FakeHorizontalReadHistoryDao())

        val progress = store.get(ref, content = "", orientation = ReaderOrientation.Vertical)

        assertEquals(75, progress?.normalizedPercent)
        assertEquals(0, progress?.charOffset)
        assertNull(readerDao.progress)
    }

    @Test
    fun newProgressTakesPriorityOverLegacyHistory() = runBlocking {
        val readerDao = FakeReaderDao().apply {
            progress = ReaderProgressMapper.fromLegacy(ref, "0123456789", 30, 1L)
        }
        val verticalDao = FakeVerticalReadHistoryDao().apply { item = oldVertical(percent = 90) }
        val store = RoomReaderProgressStore(readerDao, verticalDao, FakeHorizontalReadHistoryDao())

        val progress = store.get(ref, "0123456789", ReaderOrientation.Vertical)

        assertEquals(30, progress?.normalizedPercent)
        assertEquals(3, progress?.charOffset)
        assertTrue(verticalDao.writes.isEmpty())
    }

    @Test
    fun placeholderRowIsRepairedFromItsStoredPercentage() = runBlocking {
        val readerDao = FakeReaderDao().apply {
            progress = ReaderProgressEntity(
                cid = ref.cid,
                aid = ref.aid,
                volumeIndex = ref.volumeIndex,
                chapterIndex = ref.chapterIndex,
                paragraphIndex = 0,
                charOffset = 0,
                normalizedPercent = 75,
                imageIndex = null,
                updatedAt = 1L,
                isLatest = true
            )
        }
        val store = RoomReaderProgressStore(
            readerDao,
            FakeVerticalReadHistoryDao(),
            FakeHorizontalReadHistoryDao()
        )

        val progress = store.get(ref, "0123456789", ReaderOrientation.Vertical)

        assertEquals(7, progress?.charOffset)
        assertEquals(70, progress?.normalizedPercent)
        assertEquals(7, readerDao.progress?.charOffset)
    }

    @Test
    fun newStoreReadFailureFallsBackToLegacyHistory() = runBlocking {
        val readerDao = FakeReaderDao().apply { failProgressReads = true }
        val verticalDao = FakeVerticalReadHistoryDao().apply { item = oldVertical(percent = 65) }
        val store = RoomReaderProgressStore(readerDao, verticalDao, FakeHorizontalReadHistoryDao())

        val progress = store.get(ref, "0123456789", ReaderOrientation.Vertical)

        assertEquals(6, progress?.charOffset)
        assertEquals(65, progress?.normalizedPercent)
    }

    @Test
    fun legacyReadFailureDoesNotCrashChapterRestore() = runBlocking {
        val verticalDao = FakeVerticalReadHistoryDao().apply { failReads = true }
        val horizontalDao = FakeHorizontalReadHistoryDao().apply { failReads = true }
        val store = RoomReaderProgressStore(FakeReaderDao(), verticalDao, horizontalDao)

        assertNull(store.get(ref, "content", ReaderOrientation.Vertical))
    }

    @Test
    fun progressRowsAreScopedByBookAid() = runBlocking {
        val readerDao = FakeReaderDao()
        val other = ref.copy(aid = "other-book")
        readerDao.upsertProgress(ReaderProgressMapper.fromLegacy(ref, "0123456789", 20, 1L))
        readerDao.upsertProgress(ReaderProgressMapper.fromLegacy(other, "0123456789", 80, 2L))
        val store = RoomReaderProgressStore(
            readerDao,
            FakeVerticalReadHistoryDao(),
            FakeHorizontalReadHistoryDao()
        )

        val progress = store.get(ref, "0123456789", ReaderOrientation.Vertical)

        assertEquals(ref.aid, progress?.aid)
        assertEquals(2, progress?.charOffset)
    }

    @Test
    fun savingWithLegacyPositionWritesBothModels() = runBlocking {
        val readerDao = FakeReaderDao()
        val verticalDao = FakeVerticalReadHistoryDao()
        val store = RoomReaderProgressStore(readerDao, verticalDao, FakeHorizontalReadHistoryDao())

        store.save(
            anchor = ReaderAnchor(ref.cid, 1, 5, 50),
            ref = ref,
            orientation = ReaderOrientation.Vertical,
            legacyLocation = 321
        )

        assertEquals(5, readerDao.progress?.charOffset)
        assertEquals(50, readerDao.progress?.normalizedPercent)
        assertEquals(321, verticalDao.item?.location)
        assertEquals(1, verticalDao.writes.size)
    }

    @Test
    fun anchorOnlySaveDoesNotWriteCharacterOffsetToLegacyTable() = runBlocking {
        val readerDao = FakeReaderDao()
        val verticalDao = FakeVerticalReadHistoryDao()
        val store = RoomReaderProgressStore(readerDao, verticalDao, FakeHorizontalReadHistoryDao())

        store.save(
            anchor = ReaderAnchor(ref.cid, 1, 5, 50),
            ref = ref,
            orientation = ReaderOrientation.Vertical,
            legacyLocation = null
        )

        assertEquals(5, readerDao.progress?.charOffset)
        assertTrue(verticalDao.writes.isEmpty())
    }

    @Test
    fun mismatchedLegacyAidIsIgnored() = runBlocking {
        val verticalDao = FakeVerticalReadHistoryDao().apply { item = oldVertical(aid = "other-book") }
        val store = RoomReaderProgressStore(FakeReaderDao(), verticalDao, FakeHorizontalReadHistoryDao())

        assertNull(store.get(ref, "content", ReaderOrientation.Vertical))
    }
}
