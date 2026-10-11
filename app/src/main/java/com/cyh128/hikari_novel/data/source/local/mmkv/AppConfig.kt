package com.cyh128.hikari_novel.data.source.local.mmkv

import com.cyh128.hikari_novel.data.model.AppTheme
import com.cyh128.hikari_novel.data.model.DarkMode
import com.cyh128.hikari_novel.data.model.DefaultTab
import com.cyh128.hikari_novel.data.model.Language
import com.cyh128.hikari_novel.data.model.ListViewType
import com.cyh128.hikari_novel.data.model.ReaderOrientation
import com.tencent.mmkv.MMKV
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppConfig @Inject constructor() {
    private val cursor = MMKV.mmkvWithID("app_config")

    var isFirstLaunch: Boolean
        get() = cursor.decodeBool("is_first_launch", true)
        set(value) {
            cursor.encode("is_first_launch", value)
        }

    var isHorizontalFirstLaunch: Boolean
        get() = cursor.decodeBool("is_horizontal_first_launch", true)
        set(value) {
            cursor.encode("is_horizontal_first_launch", value)
        }

    var isVerticalFirstLaunch: Boolean
        get() = cursor.decodeBool("is_vertical_first_launch", true)
        set(value) {
            cursor.encode("is_vertical_first_launch", value)
        }

    var isAutoUpdate: Boolean
        get() = cursor.decodeBool("is_auto_update", true)
        set(value) {
            cursor.encode("is_auto_update", value)
        }

    /**
     * Haptic feedback for reader interactions (bar toggle, chapter step, slider
     * release).
     *
     * Defaults to true so existing users keep the feedback they already had.
     */
    var isVibrationEnabled: Boolean
        get() = cursor.decodeBool("is_vibration_enabled", true)
        set(value) {
            cursor.encode("is_vibration_enabled", value)
        }

    var readerOrientation: Int
        get() = cursor.decodeInt("reader_orientation",ReaderOrientation.Horizontal.ordinal)
        set(value) {
            cursor.encode("reader_orientation", value)
        }

    /**
     * Which wenku8 web host to talk to: `www.wenku8.net` or `www.wenku8.cc`.
     *
     * Defaults to `.net`. `.cc` is answered with a Cloudflare challenge that the
     * app cannot pass (see [com.cyh128.hikari_novel.data.source.remote.Wenku8Headers]),
     * so a fresh install has to land on a host that serves requests.
     */
    var node: String
        get() = cursor.decodeString("node", "www.wenku8.net")!!
        set(value) {
            cursor.encode("node", value)
        }

    /**
     * Whether the one-shot node repair has run.
     *
     * Older builds forced `www.wenku8.net` back to `www.wenku8.cc` on every read
     * and *persisted* that substitution, so an install that had ever used `.net`
     * has `.cc` in storage — the host that now answers 403. The repair runs once
     * at startup (see `Wenku8Repository.migrateForcedNodeHost`) and is recorded
     * here so a user who deliberately switches back to `.cc` keeps that choice.
     */
    var hasMigratedNodeHost: Boolean
        get() = cursor.decodeBool("has_migrated_node_host", false)
        set(value) {
            cursor.encode("has_migrated_node_host", value)
        }

    var language: Int
        get() = cursor.decodeInt("language",Language.FOLLOW_SYSTEM.ordinal)
        set(value) {
            cursor.encode("language", value)
        }

    var defaultTab: Int
        get() = cursor.decodeInt("default_tab", DefaultTab.Home.ordinal)
        set(value) {
            cursor.encode("default_tab", value)
        }

    var appTheme: Int
        get() = cursor.decodeInt("app_theme", AppTheme.GalIsland.ordinal)
        set(value) {
            cursor.encode("app_theme", value)
        }

    var darkMode: Int
        get() = cursor.decodeInt("dark_mode", DarkMode.System.ordinal)
        set(value) {
            cursor.encode("dark_mode", value)
        }

    var listViewType: Int
        get() = cursor.decodeInt("list_view_type", ListViewType.Grid.ordinal)
        set(value) {
            cursor.encode("list_view_type", value)
        }
}
