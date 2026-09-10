package com.cyh128.hikari_novel.reader.domain

import com.cyh128.hikari_novel.data.model.CachedChapter
import com.cyh128.hikari_novel.data.model.ChapterRef
import com.cyh128.hikari_novel.data.model.ContentSource
import com.cyh128.hikari_novel.data.model.ReaderAnchor
import com.cyh128.hikari_novel.data.model.ReaderOrientation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSessionCoordinatorTest {
    private val first = ref("c1", 0)
    private val second = ref("c2", 1)
    private val third = ref("c3", 2)

    @Test
    fun configureSkipsEmptyVolumesAndExposesSharedNavigation() {
        val coordinator = ReaderSessionCoordinator(FakeDataSource())
        val firstInSecondVolume = ref("c1", 0, 1)
        val secondInSecondVolume = ref("c2", 1, 1)
        val thirdInFourthVolume = ref("c3", 2, 3)
        coordinator.configure(
            listOf(emptyList(), listOf(firstInSecondVolume, secondInSecondVolume), emptyList(), listOf(thirdInFourthVolume)),
            firstInSecondVolume
        )

        assertEquals(secondInSecondVolume, coordinator.nextChapterLocation()?.chapter)
        assertNull(coordinator.previousChapterLocation())
        assertEquals(firstInSecondVolume, coordinator.chapterNavigator().first()?.chapter)
        assertEquals(thirdInFourthVolume, coordinator.chapterNavigator().last()?.chapter)
    }

    @Test
    fun staleLoadCannotReplaceCurrentSession() = runBlocking {
        val dataSource = FakeDataSource()
        val coordinator = ReaderSessionCoordinator(dataSource)
        coordinator.configure(listOf(listOf(first, second)), first)

        val firstRequest = coordinator.beginLoad(first)
        val secondRequest = coordinator.beginLoad(second)
        val result = coordinator.load(firstRequest).getOrThrow()

        assertFalse(coordinator.isCurrent(firstRequest))
        assertTrue(coordinator.isCurrent(secondRequest))
        assertNull(coordinator.currentCachedChapter())
        assertEquals(first, result.request.ref)
    }

    @Test
    fun prefetchUsesRequestChapterAndRejectsStaleRequest() = runBlocking {
        val dataSource = FakeDataSource()
        val coordinator = ReaderSessionCoordinator(dataSource)
        coordinator.configure(listOf(listOf(first, second, third)), first)
        val firstRequest = coordinator.beginLoad(first)

        assertTrue(coordinator.prefetchNext(firstRequest))
        assertEquals(listOf(second), dataSource.prefetched)

        val secondRequest = coordinator.beginLoad(second)
        assertFalse(coordinator.prefetchNext(firstRequest))
        assertTrue(coordinator.prefetchNext(secondRequest))
        assertEquals(listOf(second, third), dataSource.prefetched)
    }

    @Test
    fun progressAndDownloadsAreForwardedWithoutStorageTypes() = runBlocking {
        val dataSource = FakeDataSource()
        val coordinator = ReaderSessionCoordinator(dataSource)
        coordinator.configure(listOf(listOf(first)), first)
        val anchor = ReaderAnchor(first.cid, 2, 8, 40, 1)

        coordinator.saveProgress(anchor, first, ReaderOrientation.Horizontal, legacyLocation = 6)
        coordinator.enqueueDownloads(listOf(first))

        assertEquals(anchor, dataSource.savedAnchor)
        assertEquals(ReaderOrientation.Horizontal, dataSource.savedOrientation)
        assertEquals(6, dataSource.savedLegacyLocation)
        assertEquals(listOf(first), dataSource.downloads)
        assertEquals(8, coordinator.getProgress(first)?.charOffset)
    }

    private fun ref(cid: String, index: Int, volumeIndex: Int = 0) = ChapterRef(
        aid = "book",
        cid = cid,
        volumeIndex = volumeIndex,
        chapterIndex = index,
        title = "Chapter " + index
    )

    private class FakeDataSource : ReaderSessionDataSource {
        val prefetched = mutableListOf<ChapterRef>()
        val downloads = mutableListOf<ChapterRef>()
        var savedAnchor: ReaderAnchor? = null
        var savedOrientation: ReaderOrientation? = null
        var savedLegacyLocation: Int? = null

        override suspend fun load(
            ref: ChapterRef,
            forceNetwork: Boolean
        ): Result<Pair<CachedChapter, ContentSource>> = Result.success(
            CachedChapter(ref, "content-" + ref.cid, emptyList(), updatedAt = 1L) to ContentSource.NETWORK
        )

        override suspend fun prefetch(ref: ChapterRef): Boolean {
            prefetched += ref
            return true
        }

        override suspend fun enqueueDownloads(refs: List<ChapterRef>) {
            downloads += refs
        }

        override suspend fun saveProgress(
            progress: ReaderAnchor,
            ref: ChapterRef,
            orientation: ReaderOrientation?,
            legacyLocation: Int?
        ) {
            savedAnchor = progress
            savedOrientation = orientation
            savedLegacyLocation = legacyLocation
        }

        override suspend fun getProgress(
            ref: ChapterRef,
            content: String?,
            orientation: ReaderOrientation?
        ): ReaderProgressSnapshot? = ReaderProgressSnapshot(
            cid = ref.cid,
            aid = ref.aid,
            volumeIndex = ref.volumeIndex,
            chapterIndex = ref.chapterIndex,
            paragraphIndex = 2,
            charOffset = 8,
            normalizedPercent = 40,
            imageIndex = 1,
            updatedAt = 1L,
            isLatest = true
        )
    }
}
