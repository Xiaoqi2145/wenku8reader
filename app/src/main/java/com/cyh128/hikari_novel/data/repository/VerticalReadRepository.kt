package com.cyh128.hikari_novel.data.repository

import com.cyh128.hikari_novel.data.source.local.database.read_history.vertical_read_history.VerticalReadHistoryDao
import com.cyh128.hikari_novel.data.source.local.database.read_history.vertical_read_history.VerticalReadHistoryEntity
import com.cyh128.hikari_novel.data.source.local.mmkv.VerticalReadConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VerticalReadRepository @Inject constructor(
    private val verticalReadHistoryDao: VerticalReadHistoryDao,
    private val verticalReadConfig: VerticalReadConfig
) {
    fun getByCid(cid: String) = verticalReadHistoryDao.getByCid(cid)

    fun getByCid(aid: String, cid: String) = verticalReadHistoryDao.getByAidAndCid(aid, cid)

    fun getByVolume(aid: String, volume: Int) = verticalReadHistoryDao.getByVolume(aid, volume)

    suspend fun addOrReplace(
        aid: String,
        verticalReadHistoryEntity: VerticalReadHistoryEntity
    ) = verticalReadHistoryDao.addOrReplace(aid, verticalReadHistoryEntity)

    fun getLatestChapter(aid: String) = verticalReadHistoryDao.getLatestChapter(aid)

    suspend fun delete(cid: String) = verticalReadHistoryDao.delete(cid)

    suspend fun delete(aid: String, cid: String) = verticalReadHistoryDao.delete(aid, cid)

    suspend fun deleteAll(aid: String) = verticalReadHistoryDao.deleteAll(aid)

    fun getIsShowChapterReadHistory() = verticalReadConfig.isShowChapterReadHistory

    fun setIsShowChapterReadHistory(value: Boolean) {
        verticalReadConfig.isShowChapterReadHistory = value
    }

    fun getFontSize() = verticalReadConfig.fontSize

    fun setFontSize(size: Float) {
        verticalReadConfig.fontSize = size
    }

    fun getLineSpacing() = verticalReadConfig.lineSpacing

    fun setLineSpacing(lineSpacing: Float) {
        verticalReadConfig.lineSpacing = lineSpacing
    }

    fun getKeepScreenOn() = verticalReadConfig.keepScreenOn

    fun setKeepScreenOn(value: Boolean) {
        verticalReadConfig.keepScreenOn = value
    }

    fun getIsShowChapterReadHistoryWithoutConfirm() = verticalReadConfig.isShowChapterReadHistoryWithoutConfirm

    fun setIsShowChapterReadHistoryWithoutConfirm(value: Boolean) {
        verticalReadConfig.isShowChapterReadHistoryWithoutConfirm = value
    }

    // ===== P0-3: Typography =====

    fun getParagraphSpacing() = verticalReadConfig.paragraphSpacing

    fun setParagraphSpacing(value: Float) {
        verticalReadConfig.paragraphSpacing = value
    }

    fun getLetterSpacing() = verticalReadConfig.letterSpacing

    fun setLetterSpacing(value: Float) {
        verticalReadConfig.letterSpacing = value
    }

    fun getTextAlignment() = verticalReadConfig.textAlignment

    fun setTextAlignment(value: String) {
        verticalReadConfig.textAlignment = value
    }

    fun getFirstLineIndent() = verticalReadConfig.firstLineIndent

    fun setFirstLineIndent(value: Int) {
        verticalReadConfig.firstLineIndent = value
    }
}