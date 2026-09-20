package com.cyh128.hikari_novel.ui.read.common

import kotlin.math.roundToInt

/**
 * Colour roles for the reader chrome (toolbar, status line, bottom dock).
 *
 * The reader chrome follows the *reading* colours instead of the App theme, so a
 * white page in a dark App theme no longer sits under dark bars. The roles mirror
 * the GalIsland surface language (`surface` / `onSurface` / `onSurfaceVariant` /
 * `outline`); only the values change, not the structure.
 *
 * Secondary and separator colours are blended from the reader text colour over
 * the reader background, so contrast holds for every bg/text pair the user can
 * pick — including the presets and arbitrary custom hex. The blend result is
 * opaque, so nothing depends on alpha compositing over the page.
 *
 * See docs/reader-koodo-alignment.md P0-1.
 */
data class ReaderChromePalette(
    /** Chrome background: exactly the reading background, so bars melt into the page. */
    val surface: Int,
    /** Primary chrome content: exactly the reading text colour. */
    val onSurface: Int,
    /** Secondary chrome content (labels, status text, icons). */
    val onSurfaceVariant: Int,
    /** Hairline separators between chrome and content. */
    val outline: Int
) {
    companion object {
        /** How far [onSurfaceVariant] moves from the background towards the text. */
        const val VARIANT_RATIO = 0.62f

        /** How far [outline] moves from the background towards the text. */
        const val OUTLINE_RATIO = 0.24f

        /** Build the palette from the active reader colours (hex, '#' optional). */
        fun from(bgColorHex: String, textColorHex: String): ReaderChromePalette {
            val surface = parseHex(bgColorHex)
            val onSurface = parseHex(textColorHex)
            return ReaderChromePalette(
                surface = surface,
                onSurface = onSurface,
                onSurfaceVariant = blend(surface, onSurface, VARIANT_RATIO),
                outline = blend(surface, onSurface, OUTLINE_RATIO)
            )
        }

        /**
         * Accepts 3- or 6-digit hex with an optional leading '#'.
         * Falls back to opaque black on malformed input rather than throwing:
         * a bad stored value must not take the reader down.
         */
        internal fun parseHex(value: String): Int {
            val hex = value.removePrefix("#").trim()
            val expanded = if (hex.length == 3) hex.map { "$it$it" }.joinToString("") else hex
            if (expanded.length != 6) return OPAQUE_BLACK
            val rgb = expanded.toLongOrNull(16) ?: return OPAQUE_BLACK
            return OPAQUE_BLACK or rgb.toInt()
        }

        /** Opaque blend of [to] over [from] at [ratio] (0 = from, 1 = to). */
        internal fun blend(from: Int, to: Int, ratio: Float): Int {
            val r = blendChannel(from, to, ratio, 16)
            val g = blendChannel(from, to, ratio, 8)
            val b = blendChannel(from, to, ratio, 0)
            return OPAQUE_BLACK or (r shl 16) or (g shl 8) or b
        }

        private const val OPAQUE_BLACK = 0xFF shl 24

        private fun blendChannel(from: Int, to: Int, ratio: Float, shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * ratio).roundToInt().coerceIn(0, 255)
        }
    }
}
