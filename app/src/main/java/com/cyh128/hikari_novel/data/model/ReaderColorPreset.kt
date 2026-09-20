package com.cyh128.hikari_novel.data.model

/**
 * A named reader color combination (text color + background color).
 * Both are hex strings without '#' prefix.
 *
 * Presets are taken directly from Koodo Reader's themeList values.
 * See docs/reader-koodo-alignment.md P0-2.
 */
data class ReaderColorPreset(
    val name: String,
    val textColor: String,
    val bgColor: String
)

object ReaderColorPresets {
    /** Four preset groups aligned with Koodo Reader's default theme list. */
    val PRESETS: List<ReaderColorPreset> = listOf(
        ReaderColorPreset("White + Black", "000000", "ffffff"),
        ReaderColorPreset("Dark Gray + White", "ffffff", "2C2F31"),
        ReaderColorPreset("Beige + Brown", "594429", "E9D8BC"),
        ReaderColorPreset("Light Green + Dark Green", "36503E", "C5E7CF")
    )

    /**
     * Default day colors for "Restore defaults".
     *
     * The day background is the App's warm off-white (`galisland_background`),
     * not pure white: the reading page now shares the browsing surfaces' grid, so
     * it also shares their paper tone. The "White + Black" preset still offers
     * pure white for anyone who wants it.
     */
    const val DEFAULT_DAY_TEXT = "000000"
    const val DEFAULT_DAY_BG = "EFEDEA"

    /** The day background this app shipped before the grid surface landed. */
    const val LEGACY_DEFAULT_DAY_BG = "ffffff"

    /** Default night colors for "Restore defaults". */
    const val DEFAULT_NIGHT_TEXT = "ffffff"
    const val DEFAULT_NIGHT_BG = "000000"

    /** Maximum number of custom saved colors. */
    const val MAX_CUSTOM_COLORS = 8

    fun findByName(name: String): ReaderColorPreset? =
        PRESETS.firstOrNull { it.name == name }
}
