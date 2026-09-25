package com.cyh128.hikari_novel.data.model

/**
 * One colour candidate on a reader colour axis.
 *
 * [isCustom] distinguishes the four built-in presets from colours the user
 * saved: only custom entries can be removed from the axis.
 */
data class ReaderColorSwatch(
    val hex: String,
    val isCustom: Boolean
)

/**
 * The reader's two independent colour axes.
 *
 * Koodo Reader splits its colour settings into a **background** axis and a
 * **text** axis, each rendered as "presets + saved customs + clear". Picking a
 * background only changes the background; the two axes stay independent apart
 * from two linkage rules that keep a readable pair (see [linkedTextColorFor]).
 *
 * Everything here is pure: no Android types, no storage. See
 * docs/reader-settings-round4-plan.md R4-1.
 */
object ReaderColorSwatches {

    /** Background presets, taken verbatim from Koodo's `backgroundList`. */
    val BACKGROUNDS: List<String> = listOf("FFFFFF", "2C2F31", "E9D8BC", "C5E7CF")

    /** Text presets, taken verbatim from Koodo's `textList`. */
    val TEXTS: List<String> = listOf("000000", "FFFFFF", "594429", "36503E")

    /** Upper bound on saved customs per axis. */
    const val MAX_CUSTOM_COLORS = 8

    private const val WHITE = "FFFFFF"
    private const val BLACK = "000000"
    private const val DARK_BG = "2C2F31"

    /**
     * Canonical form: no leading '#', upper case, trimmed.
     *
     * Storage and comparison both go through this, so a value typed as
     * `#ffffff` and one stored as `FFFFFF` are the same colour.
     */
    fun normalize(hex: String): String = hex.trim().removePrefix("#").uppercase()

    /** Presets first, then customs — the order both axes render in. */
    fun axis(presets: List<String>, custom: List<String>): List<ReaderColorSwatch> =
        presets.map { ReaderColorSwatch(normalize(it), isCustom = false) } +
            custom.map { ReaderColorSwatch(normalize(it), isCustom = true) }

    /** Index of [hex] on [axis], or -1. Case and '#' insensitive. */
    fun indexOf(axis: List<ReaderColorSwatch>, hex: String): Int {
        val target = normalize(hex)
        return axis.indexOfFirst { it.hex == target }
    }

    /**
     * Koodo's two automatic pairings, reproduced exactly:
     *
     * - a dark background forces white text;
     * - a white background turns white text black (otherwise it would be invisible).
     *
     * Returns the text colour to adopt, or null when the text axis is untouched.
     * A deliberate non-white text colour is never overridden.
     */
    fun linkedTextColorFor(bgHex: String, currentTextHex: String): String? {
        val bg = normalize(bgHex)
        val text = normalize(currentTextHex)
        return when {
            bg == DARK_BG && text != WHITE -> WHITE
            bg == WHITE && text == WHITE -> BLACK
            else -> null
        }
    }

    /**
     * Appends [hex] to a custom axis, rejecting blanks, values already on the
     * axis (preset or custom) and anything past [MAX_CUSTOM_COLORS].
     *
     * Returns the new list, or null when nothing should change — which is what
     * the caller reports as "save failed".
     */
    fun addToAxis(
        custom: List<String>,
        hex: String,
        presets: List<String>
    ): List<String>? {
        val value = normalize(hex)
        if (value.isEmpty()) return null
        if (presets.any { normalize(it) == value }) return null
        if (custom.any { normalize(it) == value }) return null
        if (custom.size >= MAX_CUSTOM_COLORS) return null
        return custom + value
    }

    /** Drops [hex] from a custom axis; a value not present is a no-op. */
    fun removeFromAxis(custom: List<String>, hex: String): List<String> {
        val target = normalize(hex)
        return custom.filterNot { normalize(it) == target }
    }
}
