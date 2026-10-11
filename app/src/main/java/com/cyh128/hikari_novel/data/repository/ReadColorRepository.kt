package com.cyh128.hikari_novel.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.cyh128.hikari_novel.data.model.ReaderColorPresets
import com.cyh128.hikari_novel.data.model.ReaderColorSwatches
import com.cyh128.hikari_novel.data.model.ReaderThemeMode
import com.cyh128.hikari_novel.data.source.local.mmkv.ReadColorConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Storage for the reader's two independent colour axes (background / text),
 * each with its own list of saved custom colours.
 *
 * The day/night pairs are kept as separate values on purpose: the reader theme
 * has exactly two states (see [convergeReaderThemeMode]), so a single-value
 * model like Koodo's would lose the other state's colours on every toggle.
 * Axis edits therefore write to whichever pair [ReaderThemeMode] currently
 * selects. See docs/plan/reader-settings-round4-plan.md R4-1.
 */
@Singleton
class ReadColorRepository @Inject constructor(
    private val readColorConfig: ReadColorConfig
) {
    // ===== Day / night pairs =====

    fun getTextColorDay() = readColorConfig.textColorDay

    fun setTextColorDay(color: String) {
        readColorConfig.textColorDay = ReaderColorSwatches.normalize(color)
    }

    fun getTextColorNight() = readColorConfig.textColorNight

    fun setTextColorNight(color: String) {
        readColorConfig.textColorNight = ReaderColorSwatches.normalize(color)
    }

    fun getBgColorDay() = readColorConfig.bgColorDay

    fun setBgColorDay(color: String) {
        readColorConfig.bgColorDay = ReaderColorSwatches.normalize(color)
    }

    fun getBgColorNight() = readColorConfig.bgColorNight

    fun setBgColorNight(color: String) {
        readColorConfig.bgColorNight = ReaderColorSwatches.normalize(color)
    }

    fun getReaderThemeMode() = readColorConfig.readerThemeMode

    fun setReaderThemeMode(mode: ReaderThemeMode) {
        readColorConfig.readerThemeMode = mode
    }

    // ===== Active pair (what the reader is showing right now) =====

    /** True when the active pair is the night one. `System` counts as day here. */
    fun isNightActive(): Boolean = readColorConfig.readerThemeMode == ReaderThemeMode.Night

    fun currentBgColor(): String =
        if (isNightActive()) getBgColorNight() else getBgColorDay()

    fun currentTextColor(): String =
        if (isNightActive()) getTextColorNight() else getTextColorDay()

    /** Writes the background axis for the active mode only. */
    fun setCurrentBgColor(hex: String) {
        if (isNightActive()) setBgColorNight(hex) else setBgColorDay(hex)
    }

    /** Writes the text axis for the active mode only. */
    fun setCurrentTextColor(hex: String) {
        if (isNightActive()) setTextColorNight(hex) else setTextColorDay(hex)
    }

    // ===== Custom colour axes =====

    /** Saved background colours, normalised and de-duplicated. */
    fun getCustomBgColors(): List<String> = decodeAxis(readColorConfig.customBgColorsJson)

    fun getCustomTextColors(): List<String> = decodeAxis(readColorConfig.customTextColorsJson)

    fun setCustomBgColors(colors: List<String>) {
        readColorConfig.customBgColorsJson = Gson().toJson(normalizeAxis(colors))
    }

    fun setCustomTextColors(colors: List<String>) {
        readColorConfig.customTextColorsJson = Gson().toJson(normalizeAxis(colors))
    }

    /** Returns false when [hex] is blank, already present, or the axis is full. */
    fun addCustomBgColor(hex: String): Boolean {
        val next = ReaderColorSwatches.addToAxis(
            getCustomBgColors(),
            hex,
            ReaderColorSwatches.BACKGROUNDS
        ) ?: return false
        setCustomBgColors(next)
        return true
    }

    fun addCustomTextColor(hex: String): Boolean {
        val next = ReaderColorSwatches.addToAxis(
            getCustomTextColors(),
            hex,
            ReaderColorSwatches.TEXTS
        ) ?: return false
        setCustomTextColors(next)
        return true
    }

    fun removeCustomBgColor(hex: String) {
        setCustomBgColors(ReaderColorSwatches.removeFromAxis(getCustomBgColors(), hex))
    }

    fun removeCustomTextColor(hex: String) {
        setCustomTextColors(ReaderColorSwatches.removeFromAxis(getCustomTextColors(), hex))
    }

    // ===== Migrations =====

    fun getHasMigratedThemeMode() = readColorConfig.hasMigratedThemeMode

    fun setHasMigratedThemeMode(value: Boolean) {
        readColorConfig.hasMigratedThemeMode = value
    }

    /**
     * Collapses the legacy [ReaderThemeMode.System] state into a concrete
     * Day/Night value, once, so the reader theme has exactly two states and stops
     * following the App theme.
     *
     * [systemIsDark] is read from the App's resolved configuration at the call
     * site, so a user who was reading in a dark App theme stays on Night instead
     * of being flipped to Day. Runs at most once; later calls only read.
     */
    fun convergeReaderThemeMode(systemIsDark: Boolean): ReaderThemeMode {
        val current = readColorConfig.readerThemeMode
        if (readColorConfig.hasMigratedThemeMode) return current.converge(systemIsDark)
        val converged = current.converge(systemIsDark)
        readColorConfig.readerThemeMode = converged
        readColorConfig.hasMigratedThemeMode = true
        return converged
    }

    /**
     * Moves a stored day background that is still exactly the legacy pure-white
     * default onto the App's warm off-white, once.
     *
     * The reading page now shares the browsing surfaces' grid, so it also shares
     * their paper tone; a stored `ffffff` is the old default rather than a choice,
     * and would otherwise leave existing users on a background the grid was not
     * designed for. Any other value — including the "White" axis preset, which
     * the user picks deliberately — is left untouched. Runs at most once.
     */
    fun migrateDefaultDayBackground() {
        if (readColorConfig.hasMigratedGridBackground) return
        if (readColorConfig.bgColorDay.equals(
                ReaderColorPresets.LEGACY_DEFAULT_DAY_BG,
                ignoreCase = true
            )
        ) {
            readColorConfig.bgColorDay = ReaderColorPresets.DEFAULT_DAY_BG
        }
        readColorConfig.hasMigratedGridBackground = true
    }

    /**
     * Splits the pre-axis `custom_colors_json` (a list of text+background
     * *combinations*) onto the two axes, once.
     *
     * The old key is left in place rather than deleted: a rollback to the
     * previous build then still finds its data. Nothing reads it afterwards.
     * The split is idempotent, so a second run cannot duplicate entries.
     */
    fun migrateLegacyCustomColors() {
        if (readColorConfig.hasMigratedCustomColorAxes) return
        val legacy = decodeLegacyPresets(readColorConfig.customColorsJson)
        if (legacy.isNotEmpty()) {
            var backgrounds = getCustomBgColors()
            var texts = getCustomTextColors()
            legacy.forEach { preset ->
                ReaderColorSwatches.addToAxis(
                    backgrounds,
                    preset.bgColor,
                    ReaderColorSwatches.BACKGROUNDS
                )?.let { backgrounds = it }
                ReaderColorSwatches.addToAxis(
                    texts,
                    preset.textColor,
                    ReaderColorSwatches.TEXTS
                )?.let { texts = it }
            }
            setCustomBgColors(backgrounds)
            setCustomTextColors(texts)
        }
        readColorConfig.hasMigratedCustomColorAxes = true
    }

    // ===== Presets =====

    /**
     * Reset both colour pairs to factory defaults.
     *
     * Only colours are restored — typography, saved custom colours and the
     * current Day/Night state are deliberately left alone, so this is a colour
     * reset rather than a full style wipe.
     */
    fun restoreDefaults() {
        readColorConfig.textColorDay = ReaderColorPresets.DEFAULT_DAY_TEXT
        readColorConfig.bgColorDay = ReaderColorPresets.DEFAULT_DAY_BG
        readColorConfig.textColorNight = ReaderColorPresets.DEFAULT_NIGHT_TEXT
        readColorConfig.bgColorNight = ReaderColorPresets.DEFAULT_NIGHT_BG
    }

    // ===== Internals =====

    private fun decodeAxis(json: String): List<String> = try {
        val type = object : TypeToken<List<String>>() {}.type
        Gson().fromJson<List<String>>(json, type) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    /** Legacy shape of the custom colour list, kept only for the one-shot split. */
    private data class LegacyPreset(val textColor: String, val bgColor: String)

    private fun decodeLegacyPresets(json: String): List<LegacyPreset> = try {
        val type = object : TypeToken<List<LegacyPreset>>() {}.type
        Gson().fromJson<List<LegacyPreset>>(json, type) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    private fun normalizeAxis(colors: List<String>): List<String> =
        colors.map { ReaderColorSwatches.normalize(it) }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(ReaderColorSwatches.MAX_CUSTOM_COLORS)
}
