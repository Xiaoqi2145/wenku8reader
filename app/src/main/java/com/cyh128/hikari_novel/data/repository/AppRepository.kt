package com.cyh128.hikari_novel.data.repository

import android.accounts.NetworkErrorException
import android.content.pm.PackageManager
import com.cyh128.hikari_novel.HikariApp
import com.cyh128.hikari_novel.data.model.AppTheme
import com.cyh128.hikari_novel.data.model.DarkMode
import com.cyh128.hikari_novel.data.model.DefaultTab
import com.cyh128.hikari_novel.data.model.Language
import com.cyh128.hikari_novel.data.model.ListViewType
import com.cyh128.hikari_novel.data.model.ReaderOrientation
import com.cyh128.hikari_novel.data.source.local.mmkv.AppConfig
import com.cyh128.hikari_novel.data.source.remote.Network
import com.cyh128.hikari_novel.util.enumValue
import com.cyh128.hikari_novel.util.VersionTag
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import rxhttp.awaitResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRepository @Inject constructor(
    private val appConfig: AppConfig,
    private val network: Network
) {
    //获取首次启动状态
    fun getIsFirstLaunch() = appConfig.isFirstLaunch

    //设置首次启动状态
    fun setIsFirstLaunch(isFirstLaunch: Boolean) {
        appConfig.isFirstLaunch = isFirstLaunch
    }

    //获取横向阅读器首次启动状态
    fun getIsHorizontalFirstLaunch() = appConfig.isHorizontalFirstLaunch

    //设置横向阅读器首次启动状态
    fun setIsHorizontalFirstLaunch(isHorizontalFirstLaunch: Boolean) {
        appConfig.isHorizontalFirstLaunch = isHorizontalFirstLaunch
    }

    //获取竖向阅读器首次启动状态
    fun getIsVerticalFirstLaunch() = appConfig.isVerticalFirstLaunch

    //设置竖向阅读器首次启动状态
    fun setIsVerticalFirstLaunch(isVerticalFirstLaunch: Boolean) {
        appConfig.isVerticalFirstLaunch = isVerticalFirstLaunch
    }

    //获取自动更新状态
    fun getIsAutoUpdate() = appConfig.isAutoUpdate

    //设置自动更新状态
    fun setIsAutoUpdate(isAutoUpdate: Boolean) {
        appConfig.isAutoUpdate = isAutoUpdate
    }

    //获取震动反馈状态
    fun getIsVibrationEnabled() = appConfig.isVibrationEnabled

    //设置震动反馈状态
    fun setIsVibrationEnabled(isVibrationEnabled: Boolean) {
        appConfig.isVibrationEnabled = isVibrationEnabled
    }

    //获取阅读器阅读方式
    fun getReaderOrientation() = enumValue(
        ReaderOrientation.entries,
        appConfig.readerOrientation,
        ReaderOrientation.Horizontal
    )

    //设置阅读器阅读方式
    fun setReaderOrientation(readerOrientation: ReaderOrientation) {
        appConfig.readerOrientation = readerOrientation.ordinal
    }

    fun getLanguage() = enumValue(Language.entries, appConfig.language, Language.FOLLOW_SYSTEM)

    fun setLanguage(language: Language) {
        appConfig.language = language.ordinal
    }

    fun getDefaultTab() = enumValue(DefaultTab.entries, appConfig.defaultTab, DefaultTab.Home)

    fun setDefaultTab(defaultTab: DefaultTab) {
        appConfig.defaultTab = defaultTab.ordinal
    }

    fun getAppTheme() = enumValue(AppTheme.entries, appConfig.appTheme, AppTheme.GalIsland)

    fun setAppTheme(appTheme: AppTheme) {
        appConfig.appTheme = appTheme.ordinal
    }

    fun getDarkMode() = enumValue(DarkMode.entries, appConfig.darkMode, DarkMode.System)

    fun setDarkMode(darkMode: DarkMode) {
        appConfig.darkMode = darkMode.ordinal
    }

    fun getListViewType() = enumValue(ListViewType.entries, appConfig.listViewType, ListViewType.Grid)

    fun setListViewType(listViewType: ListViewType) {
        appConfig.listViewType = listViewType.ordinal
    }

    /**
     * Whether a newer release is published.
     *
     * Two things had to change here. The endpoint was the *upstream* repository,
     * which stopped shipping after archiving, so every fork user was compared
     * against a build they could not install. And the comparison was string
     * equality, which a tag like `bye` — the upstream "stopped maintaining"
     * notice — can never satisfy, so the dialog appeared on every launch.
     *
     * Only well-formed numeric tags count now: `bye`, a `v` prefix and any
     * prerelease suffix are ignored, and the current version is parsed into
     * numbers instead of compared as text. A malformed or older tag therefore
     * reports "no update" rather than nagging forever.
     */
    suspend fun checkUpdate(): Result<Boolean> {
        network.getWithoutCookie(
            "https://api.github.com/repos/Xiaoqi2145/wenku8reader/releases?per_page=20"
        ).awaitResult { response ->
            response.body()?.let { data ->
                val releases: List<Map<String, Any?>> = Gson().fromJson(
                    data.string(),
                    object : TypeToken<List<Map<String, Any?>>>() {}.type
                )
                val tags = releases
                    .asSequence()
                    .filter { it["draft"] != true }
                    .map { it["tag_name"] as? String }
                    .toList()
                return Result.success(VersionTag.anyNewerThan(getVersion(), tags))
            }
            return Result.success(false)
        }.onFailure {
            return Result.failure(NetworkErrorException(it.message))
        }
        throw RuntimeException()
    }

    private fun getVersion(): String? {
        val manager = HikariApp.application.packageManager
        var name: String? = null
        try {
            val info = manager.getPackageInfo(HikariApp.application.packageName, 0)
            name = info.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return name
    }
}
