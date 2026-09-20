package com.cyh128.hikari_novel.data.source.local.mmkv

import com.tencent.mmkv.MMKV
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VerticalReadConfig @Inject constructor() {
    private val cursor = MMKV.mmkvWithID("vertical_read_config")

    var fontSize: Float
        get() = cursor.decodeFloat("font_size", 16f)
        set(value) {
            cursor.encode("font_size", value)
        }

    var lineSpacing: Float
        get() = cursor.decodeFloat("line_spacing", 1f)
        set(value) {
            cursor.encode("line_spacing", value)
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

    /** Paragraph spacing in px, 0–40. */
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
     * Text alignment: "left" or "justify".
     * Vertical reader supports left and justify.
     */
    var textAlignment: String
        get() = cursor.decodeString("text_alignment", "left") ?: "left"
        set(value) {
            cursor.encode("text_alignment", value)
        }

    /**
     * First line indent: 0 (none) or 2 (two characters).
     */
    var firstLineIndent: Int
        get() = cursor.decodeInt("first_line_indent", 0)
        set(value) {
            cursor.encode("first_line_indent", value)
        }
}
