package com.cyh128.hikari_novel.util

import android.content.Context
import android.content.res.Configuration
import com.cyh128.hikari_novel.data.model.ReaderThemeMode
import com.cyh128.hikari_novel.data.repository.ReadColorRepository

/**
 * Resolves the active reader color pair (textColor, bgColor) from the
 * [ReaderThemeMode] and the App's resolved dark-mode configuration.
 *
 * This is the single source of truth for reader colors — replaces the
 * scattered getIsInDarkMode() checks in the reader Activities/Fragment.
 * See docs/reader-koodo-alignment.md P0-1.
 *
 * [context] must be an Activity (or Activity-derived) context so that
 * `resources.configuration.uiMode` reflects the night mode AppCompat applied to
 * that Activity. Do NOT use `AppCompatDelegate.getDefaultNightMode()` here: the
 * default `DarkMode.System` maps to MODE_NIGHT_FOLLOW_SYSTEM (-1), which never
 * equals MODE_NIGHT_YES, so a dark system silently resolved to the day pair.
 */
class ReaderColorResolver(
    private val repository: ReadColorRepository,
    private val context: Context
) {
    /** True when the current reader colors are the night pair. */
    fun isNightActive(): Boolean {
        return when (repository.getReaderThemeMode()) {
            ReaderThemeMode.Day -> false
            ReaderThemeMode.Night -> true
            ReaderThemeMode.System -> isSystemDarkMode(context)
        }
    }

    /** Active text color as a "#" + hex string. */
    fun textColorHex(): String =
        "#" + (if (isNightActive()) repository.getTextColorNight() else repository.getTextColorDay())

    /** Active background color as a "#" + hex string. */
    fun bgColorHex(): String =
        "#" + (if (isNightActive()) repository.getBgColorNight() else repository.getBgColorDay())

    companion object {
        fun isSystemDarkMode(context: Context): Boolean {
            val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
        }
    }
}
