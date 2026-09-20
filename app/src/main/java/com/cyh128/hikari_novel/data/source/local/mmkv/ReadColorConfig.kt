package com.cyh128.hikari_novel.data.source.local.mmkv

import com.cyh128.hikari_novel.data.model.ReaderThemeMode
import com.tencent.mmkv.MMKV
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadColorConfig @Inject constructor() {
    private val cursor = MMKV.mmkvWithID("read_color_config")

    var textColorDay: String
        get() = cursor.decodeString("text_color_day", "000000")!!
        set(value) {
            cursor.encode("text_color_day", value)
        }

    var textColorNight: String
        get() = cursor.decodeString("text_color_night", "ffffff")!!
        set(value) {
            cursor.encode("text_color_night", value)
        }

    var bgColorDay: String
        get() = cursor.decodeString("bg_color_day", "ffffff")!!
        set(value) {
            cursor.encode("bg_color_day", value)
        }

    var bgColorNight: String
        get() = cursor.decodeString("bg_color_night", "000000")!!
        set(value) {
            cursor.encode("bg_color_night", value)
        }

    /** Reader-specific theme mode, decoupled from the App-wide dark mode. */
    var readerThemeMode: ReaderThemeMode
        get() = ReaderThemeMode.fromString(cursor.decodeString("reader_theme_mode", "System"))
        set(value) {
            cursor.encode("reader_theme_mode", value.name)
        }

    /** Preset color name ("Preset 1", "Preset 2", etc.) or empty if custom. */
    var presetColorName: String
        get() = cursor.decodeString("preset_color_name", "") ?: ""
        set(value) {
            cursor.encode("preset_color_name", value)
        }

    /** Custom saved colors as JSON array of {textColor, bgColor}. */
    var customColorsJson: String
        get() = cursor.decodeString("custom_colors_json", "[]") ?: "[]"
        set(value) {
            cursor.encode("custom_colors_json", value)
        }

    /**
     * One-shot migration flag: when the user first toggles the reader night button
     * after this code lands, we sync their App dark mode into the reader theme mode.
     */
    var hasMigratedThemeMode: Boolean
        get() = cursor.decodeBool("has_migrated_theme_mode", false)
        set(value) {
            cursor.encode("has_migrated_theme_mode", value)
        }
}
