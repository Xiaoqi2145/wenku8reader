package com.cyh128.hikari_novel.util

import android.content.Context
import android.content.res.Configuration
import com.cyh128.hikari_novel.data.model.ReaderThemeMode
import com.cyh128.hikari_novel.data.repository.ReadColorRepository

/**
 * Resolves the active reader color pair (textColor, bgColor) from the
 * [ReaderThemeMode] and the App's system dark-mode flag.
 *
 * This is the single source of truth for reader colors — replaces the
 * scattered getIsInDarkMode() checks in the reader Activities/Fragment.
 * See docs/reader-koodo-alignment.md P0-1.
 */
class ReaderColorResolver(
    private val repository: ReadColorRepository
) {
    /** True when the current reader colors are the night pair. */
    fun isNightActive(): Boolean {
        return when (repository.getReaderThemeMode()) {
            ReaderThemeMode.Day -> false
            ReaderThemeMode.Night -> true
            ReaderThemeMode.System -> isSystemDarkMode()
        }
    }

    /** Active text color as a "#" + hex string. */
    fun textColorHex(): String =
        "#" + (if (isNightActive()) repository.getTextColorNight() else repository.getTextColorDay())

    /** Active background color as a "#" + hex string. */
    fun bgColorHex(): String =
        "#" + (if (isNightActive()) repository.getBgColorNight() else repository.getBgColorDay())

    private fun isSystemDarkMode(): Boolean {
        // When readerThemeMode is System, we need to check the App's current night mode.
        // The App uses AppCompatDelegate for this.
        return androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode() ==
            androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
    }

    companion object {
        fun isSystemDarkMode(context: Context): Boolean {
            val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
        }
    }
}
