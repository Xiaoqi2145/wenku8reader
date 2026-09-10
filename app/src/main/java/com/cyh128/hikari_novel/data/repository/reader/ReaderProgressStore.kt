package com.cyh128.hikari_novel.data.repository.reader

import com.cyh128.hikari_novel.data.model.ChapterRef
import com.cyh128.hikari_novel.data.model.ReaderAnchor
import com.cyh128.hikari_novel.data.model.ReaderOrientation
import com.cyh128.hikari_novel.data.source.local.database.read_history.BaseReadHistoryEntity
import com.cyh128.hikari_novel.data.source.local.database.read_history.horizontal_read_history.HorizontalReadHistoryDao
import com.cyh128.hikari_novel.data.source.local.database.read_history.horizontal_read_history.HorizontalReadHistoryEntity
import com.cyh128.hikari_novel.data.source.local.database.read_history.vertical_read_history.VerticalReadHistoryDao
import com.cyh128.hikari_novel.data.source.local.database.read_history.vertical_read_history.VerticalReadHistoryEntity
import com.cyh128.hikari_novel.data.source.local.database.reader.ReaderDao
import com.cyh128.hikari_novel.data.source.local.database.reader.ReaderProgressEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Unified progress storage for both reader orientations. */
interface ReaderProgressStore {
    /** Legacy compatibility only; cid is not globally scoped. */
    @Deprecated("Use get(ref, content, orientation) to validate the book aid")
    suspend fun get(cid: String): ReaderProgressEntity?

    suspend fun get(
        ref: ChapterRef,
        content: String?,
        orientation: ReaderOrientation?
    ): ReaderProgressEntity?

    suspend fun save(
        anchor: ReaderAnchor,
        ref: ChapterRef,
        orientation: ReaderOrientation? = null,
        legacyLocation: Int? = null
    )

    fun observe(aid: String): Flow<List<ReaderProgressEntity>>
}

/**
 * Reader progress backed by the new anchor table, with lazy migration from
 * the existing horizontal/vertical history tables.
 */
@Singleton
class RoomReaderProgressStore @Inject constructor(
    private val readerDao: ReaderDao,
    private val verticalDao: VerticalReadHistoryDao,
    private val horizontalDao: HorizontalReadHistoryDao
) : ReaderProgressStore {
    /** Serialize migration reads with saves so stale legacy data cannot win. */
    private val progressMutex = Mutex()
    /**
     * Legacy compatibility lookup without an aid. New reader code must use the
     * metadata-aware overload below because cid is not globally scoped.
     */
    @Deprecated("Use get(ref, content, orientation) to validate the book aid")
    override suspend fun get(cid: String): ReaderProgressEntity? = progressMutex.withLock {
        safeReaderRead { readerDao.getProgress(cid) }?.let { return@withLock it }

        val legacy = findLegacy(cid, orientation = null) ?: return@withLock null
        val ref = ChapterRef(
            aid = legacy.aid,
            cid = legacy.cid,
            volumeIndex = legacy.volume,
            chapterIndex = legacy.chapter,
            title = ""
        )
        ReaderProgressMapper.fromLegacy(
            ref = ref,
            content = null,
            progressPercent = legacy.progressPercent,
            updatedAt = System.currentTimeMillis()
        )
    }

    override suspend fun get(
        ref: ChapterRef,
        content: String?,
        orientation: ReaderOrientation?
    ): ReaderProgressEntity? = progressMutex.withLock {
        safeReaderRead { readerDao.getProgressForBook(ref.aid, ref.cid) }?.let { current ->
            return@withLock if (content.isNullOrBlank()) {
                current
            } else {
                val repaired = ReaderProgressMapper.clamp(current, content)
                if (repaired != current) persistMigration(repaired)
                repaired
            }
        }

        val legacy = findLegacy(ref.cid, orientation, ref.aid) ?: return@withLock null
        val usableContent = content?.takeIf { it.isNotBlank() }
        val migrated = ReaderProgressMapper.fromLegacy(
            ref = ref,
            content = usableContent,
            progressPercent = legacy.progressPercent,
            updatedAt = System.currentTimeMillis()
        )
        if (usableContent != null) persistMigration(migrated)
        migrated
    }

    override suspend fun save(
        anchor: ReaderAnchor,
        ref: ChapterRef,
        orientation: ReaderOrientation?,
        legacyLocation: Int?
    ) = progressMutex.withLock {
        if (anchor.cid != ref.cid) return@withLock
        val normalized = ReaderProgressMapper.normalize(anchor, ref.cid)
        val progress = ReaderProgressEntity(
            cid = ref.cid,
            aid = ref.aid,
            volumeIndex = ref.volumeIndex,
            chapterIndex = ref.chapterIndex,
            paragraphIndex = normalized.paragraphIndex,
            charOffset = normalized.charOffset,
            normalizedPercent = normalized.normalizedPercent,
            imageIndex = normalized.imageIndex,
            updatedAt = System.currentTimeMillis(),
            isLatest = true
        )
        readerDao.upsertLatestProgress(progress)

        // Keep the legacy stores current for old app versions only when the
        // caller supplied a real legacy position. Anchor-only saves must not
        // write a character offset into a page/scrollY column.
        if (legacyLocation == null) return@withLock
        try {
            when (orientation) {
                ReaderOrientation.Vertical -> verticalDao.addOrReplace(
                    ref.aid,
                    VerticalReadHistoryEntity(
                        cid = ref.cid,
                        aid = ref.aid,
                        volume = ref.volumeIndex,
                        chapter = ref.chapterIndex,
                        location = legacyLocation,
                        progressPercent = normalized.normalizedPercent,
                        isLatest = true
                    )
                )

                ReaderOrientation.Horizontal -> horizontalDao.addOrReplace(
                    ref.aid,
                    HorizontalReadHistoryEntity(
                        cid = ref.cid,
                        aid = ref.aid,
                        volume = ref.volumeIndex,
                        chapter = ref.chapterIndex,
                        location = legacyLocation,
                        progressPercent = normalized.normalizedPercent,
                        isLatest = true
                    )
                )

                null -> Unit
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            // The new anchor table remains the source of truth if mirroring
            // an old database fails temporarily.
        }
    }

    override fun observe(aid: String): Flow<List<ReaderProgressEntity>> = readerDao.observeProgress(aid)

    private suspend fun persistMigration(progress: ReaderProgressEntity) {
        try {
            readerDao.upsertLatestProgress(progress)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            // A read-only/temporarily unavailable database must not prevent
            // opening a chapter from the legacy history value.
        }
    }

    private suspend fun findLegacy(
        cid: String,
        orientation: ReaderOrientation?,
        aid: String? = null
    ): BaseReadHistoryEntity? {
        suspend fun vertical(): VerticalReadHistoryEntity? = safeLegacyRead {
            if (aid == null) verticalDao.getByCid(cid).first()
            else verticalDao.getByAidAndCid(aid, cid).first()
        }

        suspend fun horizontal(): HorizontalReadHistoryEntity? = safeLegacyRead {
            if (aid == null) horizontalDao.getByCid(cid).first()
            else horizontalDao.getByAidAndCid(aid, cid).first()
        }

        val candidates = when (orientation) {
            ReaderOrientation.Vertical -> listOf(vertical(), horizontal())
            ReaderOrientation.Horizontal -> listOf(horizontal(), vertical())
            null -> listOf(vertical(), horizontal())
        }
        return candidates.firstOrNull { it != null }
    }

    private suspend fun <T> safeReaderRead(
        read: suspend () -> T?
    ): T? = try {
        read()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        null
    }

    private suspend fun <T : BaseReadHistoryEntity> safeLegacyRead(
        read: suspend () -> T?
    ): T? = try {
        read()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        null
    }
}

/**
 * Minimal adapter used by the deprecated direct ReaderRepository constructor.
 * It intentionally only sees the new table and does not mirror legacy history.
 */
internal class ReaderDaoProgressStore(
    private val dao: ReaderDao
) : ReaderProgressStore {
    @Deprecated("Use the metadata-aware progress lookup")
    override suspend fun get(cid: String): ReaderProgressEntity? = dao.getProgress(cid)

    override suspend fun get(
        ref: ChapterRef,
        content: String?,
        orientation: ReaderOrientation?
    ): ReaderProgressEntity? {
        val progress = dao.getProgressForBook(ref.aid, ref.cid) ?: return null
        return if (content.isNullOrBlank()) progress else ReaderProgressMapper.clamp(progress, content)
    }

    override suspend fun save(
        anchor: ReaderAnchor,
        ref: ChapterRef,
        orientation: ReaderOrientation?,
        legacyLocation: Int?
    ) {
        if (anchor.cid != ref.cid) return
        val normalized = ReaderProgressMapper.normalize(anchor, ref.cid)
        dao.upsertLatestProgress(
            ReaderProgressEntity(
                cid = ref.cid,
                aid = ref.aid,
                volumeIndex = ref.volumeIndex,
                chapterIndex = ref.chapterIndex,
                paragraphIndex = normalized.paragraphIndex,
                charOffset = normalized.charOffset,
                normalizedPercent = normalized.normalizedPercent,
                imageIndex = normalized.imageIndex,
                updatedAt = System.currentTimeMillis(),
                isLatest = true
            )
        )
    }

    override fun observe(aid: String): Flow<List<ReaderProgressEntity>> = dao.observeProgress(aid)
}

/** Pure conversion helpers shared by migration code and JVM tests. */
object ReaderProgressMapper {
    fun fromLegacy(
        ref: ChapterRef,
        content: String?,
        progressPercent: Int,
        updatedAt: Long
    ): ReaderProgressEntity {
        val safePercent = progressPercent.coerceIn(0, 100)
        val anchor = if (content == null) {
            ReaderAnchor(ref.cid, 0, 0, safePercent)
        } else {
            com.cyh128.hikari_novel.util.ReaderAnchorMapper.fromPercent(ref.cid, content, safePercent)
        }
        return ReaderProgressEntity(
            cid = ref.cid,
            aid = ref.aid,
            volumeIndex = ref.volumeIndex,
            chapterIndex = ref.chapterIndex,
            paragraphIndex = anchor.paragraphIndex,
            charOffset = anchor.charOffset,
            normalizedPercent = anchor.normalizedPercent,
            imageIndex = anchor.imageIndex,
            updatedAt = updatedAt,
            isLatest = true
        )
    }

    fun normalize(anchor: ReaderAnchor, cid: String): ReaderAnchor = anchor.copy(
        cid = cid,
        paragraphIndex = anchor.paragraphIndex.coerceAtLeast(0),
        charOffset = anchor.charOffset.coerceAtLeast(0),
        normalizedPercent = anchor.normalizedPercent.coerceIn(0, 100),
        imageIndex = anchor.imageIndex?.coerceAtLeast(0)
    )

    fun clamp(progress: ReaderProgressEntity, content: String): ReaderProgressEntity {
        // Rows written by an early migration could contain a percentage with a
        // zero offset because content was unavailable. Reconstruct that offset
        // once content is present instead of permanently restoring to the start.
        val sourceAnchor = if (progress.charOffset == 0 && progress.normalizedPercent > 0) {
            com.cyh128.hikari_novel.util.ReaderAnchorMapper.fromPercent(
                progress.cid,
                content,
                progress.normalizedPercent
            )
        } else {
            ReaderAnchor(
                cid = progress.cid,
                paragraphIndex = progress.paragraphIndex,
                charOffset = progress.charOffset,
                normalizedPercent = progress.normalizedPercent,
                imageIndex = progress.imageIndex
            )
        }
        val anchor = com.cyh128.hikari_novel.util.ReaderAnchorMapper.clamp(sourceAnchor, content)
        return progress.copy(
            paragraphIndex = anchor.paragraphIndex,
            charOffset = anchor.charOffset,
            normalizedPercent = anchor.normalizedPercent,
            imageIndex = progress.imageIndex?.coerceAtLeast(0)
        )
    }
}
