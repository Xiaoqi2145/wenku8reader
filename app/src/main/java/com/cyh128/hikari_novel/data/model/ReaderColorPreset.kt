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

    /** Default day colors for "Restore defaults". */
    const val DEFAULT_DAY_TEXT = "000000"
    const val DEFAULT_DAY_BG = "ffffff"

    /** Default night colors for "Restore defaults". */
    const val DEFAULT_NIGHT_TEXT = "ffffff"
    const val DEFAULT_NIGHT_BG = "000000"

    /** Maximum number of custom saved colors. */
    const val MAX_CUSTOM_COLORS = 8

    fun findByName(name: String): ReaderColorPreset? =
        PRESETS.firstOrNull { it.name == name }
}
