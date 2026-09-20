package com.cyh128.hikari_novel.util

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import androidx.core.graphics.toColorInt
import com.cyh128.hikari_novel.R

/**
 * Builds the circular colour swatch used by the reader colour presets.
 *
 * Koodo Reader shows presets as plain colour blocks; a swatch lets the user tell
 * the four presets apart without tapping each one. See
 * docs/reader-koodo-alignment.md P0-2.
 *
 * The swatch size comes from the hosting button's `iconSize`, so no dedicated
 * dimens token is introduced.
 */
object ReaderColorSwatch {

    /**
     * @param bgColorHex preset background colour, with or without a leading '#'
     * @param textColorHex preset text colour, drawn as the ring so a white
     *   background stays visible against the button surface
     */
    fun create(context: Context, bgColorHex: String, textColorHex: String): Drawable {
        val strokeWidth = context.resources.getDimensionPixelSize(R.dimen.gi_stroke)
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(parseHex(bgColorHex))
            setStroke(strokeWidth, parseHex(textColorHex))
        }
    }

    private fun parseHex(value: String): Int =
        if (value.startsWith("#")) value.toColorInt() else ("#" + value).toColorInt()
}
