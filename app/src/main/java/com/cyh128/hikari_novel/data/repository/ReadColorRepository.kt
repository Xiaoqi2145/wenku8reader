package com.cyh128.hikari_novel.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.cyh128.hikari_novel.data.model.ReaderColorPreset
import com.cyh128.hikari_novel.data.model.ReaderColorPresets
import com.cyh128.hikari_novel.data.model.ReaderThemeMode
import com.cyh128.hikari_novel.data.source.local.mmkv.ReadColorConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadColorRepository @Inject constructor(
    private val readColorConfig: ReadColorConfig
) {
    fun getTextColorDay() = readColorConfig.textColorDay

    fun setTextColorDay(color: String) {
        readColorConfig.textColorDay = color
    }

    fun getTextColorNight() = readColorConfig.textColorNight

    fun setTextColorNight(color: String) {
        readColorConfig.textColorNight = color
    }

    fun getBgColorDay() = readColorConfig.bgColorDay

    fun setBgColorDay(color: String) {
        readColorConfig.bgColorDay = color
    }

    fun getBgColorNight() = readColorConfig.bgColorNight

    fun setBgColorNight(color: String) {
        readColorConfig.bgColorNight = color
    }

    fun getReaderThemeMode() = readColorConfig.readerThemeMode

    fun setReaderThemeMode(mode: ReaderThemeMode) {
        readColorConfig.readerThemeMode = mode
    }

    fun getPresetColorName() = readColorConfig.presetColorName

    fun setPresetColorName(name: String) {
        readColorConfig.presetColorName = name
    }

    fun getCustomColorsJson() = readColorConfig.customColorsJson

    fun setCustomColorsJson(json: String) {
        readColorConfig.customColorsJson = json
    }

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

    /** Deserialize custom color list from JSON; returns empty list on parse failure. */
    fun getCustomColors(): List<ReaderColorPreset> {
        val json = readColorConfig.customColorsJson
        return try {
            val type = object : TypeToken<List<ReaderColorPreset>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Save custom color list to JSON, enforcing the 8-item limit. */
    fun setCustomColors(colors: List<ReaderColorPreset>) {
        val trimmed = colors.take(ReaderColorPresets.MAX_CUSTOM_COLORS)
        readColorConfig.customColorsJson = Gson().toJson(trimmed)
    }

    /** Add a custom color combination; returns false if limit reached or duplicate. */
    fun addCustomColor(preset: ReaderColorPreset): Boolean {
        val current = getCustomColors()
        if (current.size >= ReaderColorPresets.MAX_CUSTOM_COLORS) return false
        if (current.any { it.textColor == preset.textColor && it.bgColor == preset.bgColor }) return false
        setCustomColors(current + preset)
        return true
    }

    /** Remove a custom color by name. */
    fun removeCustomColor(name: String) {
        val current = getCustomColors()
        setCustomColors(current.filterNot { it.name == name })
    }

    /**
     * Apply a preset to both day and night colors.
     *
     * The theme mode is deliberately left alone: a preset is a colour choice, and
     * resetting the mode here would resurrect the third [ReaderThemeMode.System]
     * state that [convergeReaderThemeMode] just removed. Both pairs get the same
     * values, so the mode cannot change what is displayed anyway.
     */
    fun applyPreset(preset: ReaderColorPreset) {
        readColorConfig.textColorDay = preset.textColor
        readColorConfig.bgColorDay = preset.bgColor
        readColorConfig.textColorNight = preset.textColor
        readColorConfig.bgColorNight = preset.bgColor
        readColorConfig.presetColorName = preset.name
    }

    /**
     * Reset all colors to factory defaults. Like [applyPreset], this restores the
     * colour pairs only and keeps the reader in its current Day/Night state.
     */
    fun restoreDefaults() {
        readColorConfig.textColorDay = ReaderColorPresets.DEFAULT_DAY_TEXT
        readColorConfig.bgColorDay = ReaderColorPresets.DEFAULT_DAY_BG
        readColorConfig.textColorNight = ReaderColorPresets.DEFAULT_NIGHT_TEXT
        readColorConfig.bgColorNight = ReaderColorPresets.DEFAULT_NIGHT_BG
        readColorConfig.presetColorName = ""
    }

    companion object {
        private const val READ_COLOR_CONFIG = "read_color_config"
    }
}
