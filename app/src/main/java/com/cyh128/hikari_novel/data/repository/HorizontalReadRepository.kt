package com.cyh128.hikari_novel.data.repository

import com.cyh128.hikari_novel.data.source.local.database.read_history.horizontal_read_history.HorizontalReadHistoryDao
import com.cyh128.hikari_novel.data.source.local.database.read_history.horizontal_read_history.HorizontalReadHistoryEntity
import com.cyh128.hikari_novel.data.source.local.mmkv.HorizontalReadConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HorizontalReadRepository @Inject constructor(
    private val horizontalReadHistoryDao: HorizontalReadHistoryDao,
    private val horizontalReadConfig: HorizontalReadConfig
) {
    fun getByCid(cid: String) = horizontalReadHistoryDao.getByCid(cid)

    fun getByCid(aid: String, cid: String) = horizontalReadHistoryDao.getByAidAndCid(aid, cid)

    fun getByVolume(aid: String, volume: Int) = horizontalReadHistoryDao.getByVolume(aid, volume)

    suspend fun addOrReplace(
        aid: String,
        horizontalReadHistoryEntity: HorizontalReadHistoryEntity
    ) = horizontalReadHistoryDao.addOrReplace(aid, horizontalReadHistoryEntity)

    fun getLatestChapter(aid: String) = horizontalReadHistoryDao.getLatestChapter(aid)

    suspend fun delete(cid: String) = horizontalReadHistoryDao.delete(cid)

    suspend fun delete(aid: String, cid: String) = horizontalReadHistoryDao.delete(aid, cid)

    suspend fun deleteAll(aid: String) = horizontalReadHistoryDao.deleteAll(aid)

    fun getFontSize() = horizontalReadConfig.fontSize

    fun setFontSize(size: Float) {
        horizontalReadConfig.fontSize = size
    }

    fun getBottomFontSize() = horizontalReadConfig.bottomTextSize

    fun setBottomFontSize(size: Float) {
        horizontalReadConfig.bottomTextSize = size
    }

    fun getLineSpacing() = horizontalReadConfig.lineSpacing

    fun setLineSpacing(lineSpacing: Float) {
        horizontalReadConfig.lineSpacing = lineSpacing
    }

    fun getKeyDownSwitchChapter() = horizontalReadConfig.keyDownSwitchChapter

    fun setKeyDownSwitchChapter(value: Boolean) {
        horizontalReadConfig.keyDownSwitchChapter = value
    }

    fun getKeepScreenOn() = horizontalReadConfig.keepScreenOn

    fun setKeepScreenOn(value: Boolean) {
        horizontalReadConfig.keepScreenOn = value
    }

    fun getSwitchAnimation() = horizontalReadConfig.switchAnimation

    fun getTabletDoublePage() = horizontalReadConfig.tabletDoublePage

    fun setTabletDoublePage(value: Boolean) {
        horizontalReadConfig.tabletDoublePage = value
    }

    //获取是否显示当前章节的历史阅读记录
    fun getIsShowChapterReadHistory() = horizontalReadConfig.isShowChapterReadHistory

    fun setIsShowChapterReadHistory(value: Boolean) {
        horizontalReadConfig.isShowChapterReadHistory = value
    }

    fun getIsShowChapterReadHistoryWithoutConfirm() = horizontalReadConfig.isShowChapterReadHistoryWithoutConfirm

    fun setIsShowChapterReadHistoryWithoutConfirm(value: Boolean) {
        horizontalReadConfig.isShowChapterReadHistoryWithoutConfirm = value
    }

    // ===== P0-3: Typography =====

    fun getParagraphSpacing() = horizontalReadConfig.paragraphSpacing

    fun setParagraphSpacing(value: Float) {
        horizontalReadConfig.paragraphSpacing = value
    }

    fun getLetterSpacing() = horizontalReadConfig.letterSpacing

    fun setLetterSpacing(value: Float) {
        horizontalReadConfig.letterSpacing = value
    }

    fun getTextAlignment() = horizontalReadConfig.textAlignment

    fun setTextAlignment(value: String) {
        horizontalReadConfig.textAlignment = value
    }

    fun getFirstLineIndent() = horizontalReadConfig.firstLineIndent

    fun setFirstLineIndent(value: Int) {
        horizontalReadConfig.firstLineIndent = value
    }

    // ===== P1-1: Animation enum =====

    /**
     * Persist the page turn animation mode: "none", "sliding", or "mimical".
     * Reading is covered by [getSwitchAnimation]; the legacy boolean is
     * migrated inside HorizontalReadConfig.switchAnimation.
     */
    fun setSwitchAnimation(value: String) {
        horizontalReadConfig.switchAnimation = value
    }

    /**
     * Migrate from the old boolean value if the user hasn't set the new string
     * value yet. Returns the migrated value.
     */
    fun migrateSwitchAnimation(): String {
        val current = horizontalReadConfig.switchAnimation
        if (current == "none" || current == "sliding" || current == "mimical") return current
        // If the value is "false" or "true" (old boolean serialized), migrate
        return when (current) {
            "false" -> "none"
            "true" -> "sliding"
            else -> "none"
        }.also { horizontalReadConfig.switchAnimation = it }
    }
}
