package com.cyh128.hikari_novel.data.model

/**
 * Reader colour theme mode. The reader page is deliberately **two-state**: the
 * reading colours never follow the App theme, so a white page can live inside a
 * dark App theme.
 *
 * [Day]   - always use the day colour pair (bg + text)
 * [Night] - always use the night colour pair
 * [System] - legacy / first-launch value only: "whatever the App theme was when
 *   the reader was first opened". [converge] folds it into a concrete Day/Night
 *   value once, after which nothing writes it again.
 *
 * See docs/reader-koodo-alignment.md P0-1.
 */
enum class ReaderThemeMode {
    Day,
    Night,
    System;

    /** Collapse the legacy [System] state into a concrete Day/Night value. */
    fun converge(systemIsDark: Boolean): ReaderThemeMode = when (this) {
        Day -> Day
        Night -> Night
        System -> if (systemIsDark) Night else Day
    }

    companion object {
        /** Parse a persisted string value; falls back to [System] for legacy data. */
        fun fromString(value: String?): ReaderThemeMode = when (value) {
            "Day" -> Day
            "Night" -> Night
            else -> System
        }
    }
}
