package com.cyh128.hikari_novel.data.model

/**
 * Factory defaults for the reader colour pair.
 *
 * The presets themselves and the axis helpers live in [ReaderColorSwatches];
 * this object only holds the values "Restore defaults" writes and the one-shot
 * migration constants. See docs/reader-settings-round4-plan.md R4-1.
 */
object ReaderColorPresets {
    /**
     * Default day colours for "Restore defaults".
     *
     * The day background is the App's warm off-white (`galisland_background`),
     * not pure white: the reading page shares the browsing surfaces' grid, so it
     * also shares their paper tone. The "White" background preset still offers
     * pure white for anyone who wants it.
     */
    const val DEFAULT_DAY_TEXT = "000000"
    const val DEFAULT_DAY_BG = "EFEDEA"

    /** The day background this app shipped before the grid surface landed. */
    const val LEGACY_DEFAULT_DAY_BG = "FFFFFF"

    /** Default night colours for "Restore defaults". */
    const val DEFAULT_NIGHT_TEXT = "FFFFFF"
    const val DEFAULT_NIGHT_BG = "000000"
}
