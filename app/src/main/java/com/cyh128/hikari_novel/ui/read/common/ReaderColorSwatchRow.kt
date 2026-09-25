package com.cyh128.hikari_novel.ui.read.common

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import androidx.core.graphics.toColorInt
import com.cyh128.hikari_novel.R
import com.cyh128.hikari_novel.data.model.ReaderColorSwatch
import com.cyh128.hikari_novel.data.model.ReaderColorSwatches

/**
 * Renders one reader colour axis — its presets plus its saved customs — as a
 * horizontal row of [ReaderColorSwatchView]s, followed by an "add" affordance.
 *
 * The row is built in code rather than XML because the number of swatches
 * depends on how many colours the user has saved; a static XML list would cap
 * that at whatever was written at build time. See
 * docs/reader-settings-round4-plan.md R4-2.
 */
class ReaderColorSwatchRow @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    /** Invoked with the axis entry the user tapped. */
    var onColorPicked: ((String) -> Unit)? = null

    /** Invoked with a saved colour the user removed. */
    var onColorRemoved: ((String) -> Unit)? = null

    /** Invoked when the trailing "add" affordance is tapped. */
    var onAddRequested: (() -> Unit)? = null

    /** Ring colour for the swatches; follows the reader text colour. */
    var ringColor: Int = Color.GRAY

    /** Accent for the selected swatch and the add affordance; from the App theme. */
    var accentColor: Int = Color.BLACK

    private var axis: List<ReaderColorSwatch> = emptyList()
    private var selectedHex: String = ""

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val spacing = resources.getDimensionPixelSize(R.dimen.gi_space_sm)
        setPadding(spacing, spacing, spacing, spacing)
    }

    /**
     * Replaces the rendered axis.
     *
     * @param axis presets first, then customs — see [ReaderColorSwatches.axis]
     * @param selectedHex the colour currently applied on this axis, matched case-
     *   and '#'-insensitively
     */
    fun submit(axis: List<ReaderColorSwatch>, selectedHex: String) {
        this.axis = axis
        this.selectedHex = ReaderColorSwatches.normalize(selectedHex)
        rebuild()
    }

    /**
     * Sets both palette colours with a single rebuild.
     *
     * The individual setters each rebuild, so a caller updating both would
     * otherwise construct the row two extra times.
     */
    fun setPalette(ring: Int, accent: Int) {
        val changed = ringColor != ring || accentColor != accent
        ringColor = ring
        accentColor = accent
        if (changed) rebuild()
    }

    private fun rebuild() {
        removeAllViews()
        val size = resources.getDimensionPixelSize(R.dimen.gi_color_swatch_size)
        val gap = resources.getDimensionPixelSize(R.dimen.gi_space_sm)

        axis.forEach { entry ->
            addView(
                ReaderColorSwatchView(context).apply {
                    layoutParams = LayoutParams(size, size).apply { marginEnd = gap }
                    swatchColor = ("#" + entry.hex).toColorInt()
                    ringColor = this@ReaderColorSwatchRow.ringColor
                    accentColor = this@ReaderColorSwatchRow.accentColor
                    isAxisSelected = entry.hex == selectedHex
                    contentDescription = entry.hex
                    onPick = { onColorPicked?.invoke(entry.hex) }
                    // Only saved colours can leave the axis; the presets are fixed.
                    onRemove = if (entry.isCustom) {
                        { onColorRemoved?.invoke(entry.hex) }
                    } else {
                        null
                    }
                }
            )
        }

        addView(
            ReaderColorSwatchView(context).apply {
                layoutParams = LayoutParams(size, size)
                // Transparent fill plus a "+": a bare ring would read as a
                // white swatch, which is itself a valid background choice.
                swatchColor = Color.TRANSPARENT
                isAddAffordance = true
                ringColor = this@ReaderColorSwatchRow.ringColor
                accentColor = this@ReaderColorSwatchRow.accentColor
                contentDescription = context.getString(R.string.reader_color_add_custom)
                onPick = { onAddRequested?.invoke() }
            }
        )
    }
}
