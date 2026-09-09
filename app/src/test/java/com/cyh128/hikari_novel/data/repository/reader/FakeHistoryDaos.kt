package com.cyh128.hikari_novel.data.repository.reader

import com.cyh128.hikari_novel.data.source.local.database.read_history.horizontal_read_history.HorizontalReadHistoryDao
import com.cyh128.hikari_novel.data.source.local.database.read_history.horizontal_read_history.HorizontalReadHistoryEntity
import com.cyh128.hikari_novel.data.source.local.database.read_history.vertical_read_history.VerticalReadHistoryDao
import com.cyh128.hikari_novel.data.source.local.database.read_history.vertical_read_history.VerticalReadHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeVerticalReadHistoryDao : VerticalReadHistoryDao {
    var item: VerticalReadHistoryEntity? = null
    val writes = mutableListOf<VerticalReadHistoryEntity>()

    override fun getByCid(cid: String): Flow<VerticalReadHistoryEntity?> = flowOf(item?.takeIf { it.cid == cid })

    override fun getByVolume(aid: String, volume: Int): Flow<List<VerticalReadHistoryEntity>?> =
        flowOf(listOfNotNull(item?.takeIf { it.aid == aid && it.volume == volume }))

    override suspend fun addOrReplace(aid: String, verticalReadHistoryEntity: VerticalReadHistoryEntity) {
        item = verticalReadHistoryEntity
        writes += verticalReadHistoryEntity
    }

    override suspend fun upsert(verticalReadHistoryEntity: VerticalReadHistoryEntity) {
        item = verticalReadHistoryEntity
        writes += verticalReadHistoryEntity
    }

    override fun getLatestChapter(aid: String): Flow<VerticalReadHistoryEntity?> =
        flowOf(item?.takeIf { it.aid == aid && it.isLatest })

    override suspend fun cancelLatestChapter(aid: String) {
        item = item?.takeIf { it.aid != aid } ?: item?.copy(isLatest = false)
    }

    override suspend fun delete(cid: String) {
        if (item?.cid == cid) item = null
    }

    override suspend fun deleteAll(aid: String) {
        if (item?.aid == aid) item = null
    }
}

class FakeHorizontalReadHistoryDao : HorizontalReadHistoryDao {
    var item: HorizontalReadHistoryEntity? = null
    val writes = mutableListOf<HorizontalReadHistoryEntity>()

    override fun getByCid(cid: String): Flow<HorizontalReadHistoryEntity?> = flowOf(item?.takeIf { it.cid == cid })

    override fun getByVolume(aid: String, volume: Int): Flow<List<HorizontalReadHistoryEntity>?> =
        flowOf(listOfNotNull(item?.takeIf { it.aid == aid && it.volume == volume }))

    override suspend fun addOrReplace(aid: String, horizontalReadHistoryEntity: HorizontalReadHistoryEntity) {
        item = horizontalReadHistoryEntity
        writes += horizontalReadHistoryEntity
    }

    override suspend fun upsert(horizontalReadHistoryEntity: HorizontalReadHistoryEntity) {
        item = horizontalReadHistoryEntity
        writes += horizontalReadHistoryEntity
    }

    override fun getLatestChapter(aid: String): Flow<HorizontalReadHistoryEntity?> =
        flowOf(item?.takeIf { it.aid == aid && it.isLatest })

    override suspend fun cancelLatestChapter(aid: String) {
        item = item?.takeIf { it.aid != aid } ?: item?.copy(isLatest = false)
    }

    override suspend fun delete(cid: String) {
        if (item?.cid == cid) item = null
    }

    override suspend fun deleteAll(aid: String) {
        if (item?.aid == aid) item = null
    }
}
