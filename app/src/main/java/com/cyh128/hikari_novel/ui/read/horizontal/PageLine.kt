package com.cyh128.hikari_novel.ui.read.horizontal

/**
 * One rendered line inside a page.
 *
 * The horizontal reader paginates by accumulated line height instead of a fixed
 * lines-per-page count, because paragraph spacing makes lines differ in height.
 * See `docs/reader-koodo-alignment.md` P0-3.
 *
 * @param text the line's text, already split to fit the available width
 * @param indent extra left offset in px, used for the first-line indent
 * @param spacingBefore extra px above this line, used for paragraph spacing
 * @param isParagraphEnd true when this is the last line of its paragraph
 *   (the only line that must not be stretched by justify)
 */
data class PageLine(
    val text: String,
    val indent: Float = 0f,
    val spacingBefore: Float = 0f,
    val isParagraphEnd: Boolean = false
)
