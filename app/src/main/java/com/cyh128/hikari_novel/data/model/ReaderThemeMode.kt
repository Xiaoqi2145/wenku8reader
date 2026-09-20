package com.cyh128.hikari_novel.data.model

/**
 * Reader color theme mode, independent of the App-wide dark/light theme.
 *
 * [Day]   - always use the day color pair (bg + text)
 * [Night] - always use the night color pair
 * [System] - follow the App's system dark/light preference (legacy behavior)
 *
 * See docs/reader-koodo-alignment.md P0-1.
 */
enum class ReaderThemeMode {
    Day,
    Night,
    System;

    companion object {
        /** Parse a persisted string value; falls back to [System] for legacy data. */
        fun fromString(value: String?): ReaderThemeMode = when (value) {
            "Day" -> Day
            "Night" -> Night
            else -> System
        }
    }
}
