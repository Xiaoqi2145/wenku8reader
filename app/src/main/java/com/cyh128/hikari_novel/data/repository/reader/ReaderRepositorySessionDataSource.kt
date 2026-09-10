package com.cyh128.hikari_novel.data.repository.reader

import com.cyh128.hikari_novel.data.model.CachedChapter
import com.cyh128.hikari_novel.data.model.ChapterRef
import com.cyh128.hikari_novel.data.model.ContentSource
import com.cyh128.hikari_novel.data.model.ReaderAnchor
import com.cyh128.hikari_novel.data.model.ReaderOrientation
import com.cyh128.hikari_novel.data.repository.ReaderRepository
import com.cyh128.hikari_novel.data.source.local.database.reader.ReaderProgressEntity
import com.cyh128.hikari_novel.reader.domain.ReaderProgressSnapshot
import com.cyh128.hikari_novel.reader.domain.ReaderSessionDataSource
import javax.inject.Inject

/** Adapts the compatibility repository facade to the pure reader session API. */
class ReaderRepositorySessionDataSource @Inject constructor(
    private val repository: ReaderRepository
) : ReaderSessionDataSource {
    override suspend fun load(
        ref: ChapterRef,
        forceNetwork: Boolean
    ): Result<Pair<CachedChapter, ContentSource>> = repository.load(ref, forceNetwork)

    override suspend fun prefetch(ref: ChapterRef): Boolean = repository.prefetch(ref)

    override suspend fun enqueueDownloads(refs: List<ChapterRef>) {
        repository.enqueueDownloads(refs)
    }

    override suspend fun saveProgress(
        progress: ReaderAnchor,
        ref: ChapterRef,
        orientation: ReaderOrientation?,
        legacyLocation: Int?
    ) {
        repository.saveProgress(progress, ref, orientation, legacyLocation)
    }

    override suspend fun getProgress(
        ref: ChapterRef,
        content: String?,
        orientation: ReaderOrientation?
    ): ReaderProgressSnapshot? = repository.getProgress(ref, content, orientation)?.toSnapshot()

    private fun ReaderProgressEntity.toSnapshot() = ReaderProgressSnapshot(
        cid = cid,
        aid = aid,
        volumeIndex = volumeIndex,
        chapterIndex = chapterIndex,
        paragraphIndex = paragraphIndex,
        charOffset = charOffset,
        normalizedPercent = normalizedPercent,
        imageIndex = imageIndex,
        updatedAt = updatedAt,
        isLatest = isLatest
    )
}
