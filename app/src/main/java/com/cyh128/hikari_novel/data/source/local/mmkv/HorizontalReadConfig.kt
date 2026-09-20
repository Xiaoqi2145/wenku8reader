package com.cyh128.hikari_novel.data.source.local.mmkv

import com.tencent.mmkv.MMKV
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HorizontalReadConfig @Inject constructor() {
    private val cursor = MMKV.mmkvWithID("horizontal_read_config")

    var fontSize: Float
        get() = cursor.decodeFloat("font_size", 55f)
        set(value) {
            cursor.encode("font_size", value)
        }

    var lineSpacing: Float
        get() = cursor.decodeFloat("line_spacing", 1.5f)
        set(value) {
            cursor.encode("line_spacing", value)
        }

    var bottomTextSize: Float
        get() = cursor.decodeFloat("bottom_text_size", 45f)
        set(value) {
            cursor.encode("bottom_text_size", value)
        }

    var keyDownSwitchChapter: Boolean
        get() = cursor.decodeBool("key_down_switch_chapter", false)
        set(value) {
            cursor.encode("key_down_switch_chapter", value)
        }

    /**
     * Page turn animation mode.
     * Values: "none", "sliding", "mimical"
     * See docs/reader-koodo-alignment.md P1-1.
     *
     * Migration: the old code stored this as a Boolean under "switch_animation".
     * MMKV stores booleans and strings with different type tags, so decodeString
     * on the old key returns null. We detect that and fall back to the old
     * boolean value, then write the migrated string.
     */
    var switchAnimation: String
        get() {
            val migrated = cursor.decodeString("switch_animation")
            if (migrated != null) return migrated
            // Old boolean value: map false -> "none", true -> "sliding"
            val oldBool = cursor.decodeBool("switch_animation", false)
            val result = if (oldBool) "sliding" else "none"
            cursor.encode("switch_animation", result)
            return result
        }
        set(value) {
            cursor.encode("switch_animation", value)
        }

    var tabletDoublePage: Boolean
        get() = cursor.decodeBool("tablet_double_page", false)
        set(value) {
            cursor.encode("tablet_double_page", value)
        }

    var keepScreenOn: Boolean
        get() = cursor.decodeBool("keep_screen_on", false)
        set(value) {
            cursor.encode("keep_screen_on", value)
        }

    var isShowChapterReadHistory: Boolean
        get() = cursor.decodeBool("is_show_chapter_read_history", true)
        set(value) {
            cursor.encode("is_show_chapter_read_history", value)
        }

    var isShowChapterReadHistoryWithoutConfirm: Boolean
        get() = cursor.decodeBool("is_show_chapter_read_history_without_confirm", false)
        set(value) {
            cursor.encode("is_show_chapter_read_history_without_confirm", value)
        }

    // ===== P0-3: Typography core =====

    /** Paragraph spacing as multiple of font size, 0–4, step 0.1. */
    var paragraphSpacing: Float
        get() = cursor.decodeFloat("paragraph_spacing", 0f)
        set(value) {
            cursor.encode("paragraph_spacing", value)
        }

    /** Letter spacing in px, 0–20. */
    var letterSpacing: Float
        get() = cursor.decodeFloat("letter_spacing", 0f)
        set(value) {
            cursor.encode("letter_spacing", value)
        }

    /**
     * Text alignment: "left", "justify", or "right".
     */
    var textAlignment: String
        get() = cursor.decodeString("text_alignment", "left") ?: "left"
        set(value) {
            cursor.encode("text_alignment", value)
        }

    /**
     * First line indent: 0 (none) or 2 (two characters).
     *
     * Defaults to 2 because the horizontal reader has always indented the first
     * line by two characters; exposing the setting must not change the
     * rendering for existing users.
     */
    var firstLineIndent: Int
        get() = cursor.decodeInt("first_line_indent", 2)
        set(value) {
            cursor.encode("first_line_indent", value)
        }
}
