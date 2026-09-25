package com.cyh128.hikari_novel.util

/**
 * Formatting and clamping for the reader's slider rows.
 *
 * Kept free of Android types so the rounding rules — which decide what the user
 * sees next to a slider and what a typed value is clamped to — are unit
 * testable. See docs/reader-settings-round4-plan.md R4-3.
 */
object ReaderSliderValue {

    /**
     * Renders [value] the way the slider row shows it.
     *
     * Whole numbers lose their decimal part ("1" rather than "1.0"), because a
     * step of 1 should not read like a fraction; fractional steps keep one
     * decimal, matching the 0.1 step of the horizontal panel.
     */
    fun format(value: Float): String {
        val rounded = Math.round(value * 10f) / 10f
        return if (rounded == rounded.toLong().toFloat()) {
            rounded.toLong().toString()
        } else {
            String.format(java.util.Locale.US, "%.1f", rounded)
        }
    }

    /** The label shown under the left end of a slider. */
    fun minLabel(valueFrom: Float): String = format(valueFrom)

    /** The label shown under the right end of a slider. */
    fun maxLabel(valueTo: Float): String = format(valueTo)

    /**
     * Parses a typed value and pins it into `[valueFrom, valueTo]`.
     *
     * Returns null when [text] is not a number at all, which tells the caller to
     * restore the slider's own value instead of applying a default.
     */
    fun parseAndClamp(text: String, valueFrom: Float, valueTo: Float): Float? {
        val parsed = text.trim().toFloatOrNull() ?: return null
        if (parsed.isNaN() || parsed.isInfinite()) return null
        val low = minOf(valueFrom, valueTo)
        val high = maxOf(valueFrom, valueTo)
        return parsed.coerceIn(low, high)
    }

    /**
     * Snaps [value] onto the slider's step grid.
     *
     * Material's `Slider` throws when a programmatically assigned value is not a
     * multiple of `stepSize` measured from `valueFrom`, so every externally
     * sourced value (a restored preference, a typed value) has to pass through
     * here first.
     */
    fun snapToStep(value: Float, valueFrom: Float, stepSize: Float): Float {
        if (stepSize <= 0f) return value
        val steps = Math.round((value - valueFrom) / stepSize)
        return valueFrom + steps * stepSize
    }
}
