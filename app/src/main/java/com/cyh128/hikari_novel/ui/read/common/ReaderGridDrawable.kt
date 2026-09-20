package com.cyh128.hikari_novel.ui.read.common

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.View
import com.cyh128.hikari_novel.R
import com.google.android.material.color.MaterialColors

/**
 * The reader page background: the user's reading colour with the GalIsland
 * 40dp grid laid over it.
 *
 * The reading page used to be a flat fill, which made it the one surface in the
 * app that did not sit on the shared grid. This draws the same texture as
 * [com.cyh128.hikari_novel.ui.other.LibrarySurfaceLayout], so the reader and the
 * browsing pages read as one material.
 *
 * The grid is *over* the user's colour rather than replacing it: the reading
 * colour still decides day/night and every preset, only the texture is added.
 *
 * Drawing is done in one pass — colour first, then the lines — so callers can use
 * this directly as a `View.background` or draw it into a page canvas without an
 * extra layer.
 */
class ReaderGridDrawable(
    private val bgColor: Int,
    private val gridColor: Int,
    private val step: Float,
    private val strokeWidth: Float
) : Drawable() {

    private val gridPaint = Paint().apply {
        color = gridColor
        this.strokeWidth = strokeWidth
    }

    override fun draw(canvas: Canvas) {
        canvas.drawColor(bgColor)
        if (step <= 0f) return

        val left = bounds.left.toFloat()
        val top = bounds.top.toFloat()
        val right = bounds.right.toFloat()
        val bottom = bounds.bottom.toFloat()

        var x = left
        while (x < right) {
            canvas.drawLine(x, top, x, bottom, gridPaint)
            x += step
        }
        var y = top
        while (y < bottom) {
            canvas.drawLine(left, y, right, y, gridPaint)
            y += step
        }
    }

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Drawable")
    override fun getOpacity(): Int = PixelFormat.OPAQUE

    companion object {
        /**
         * Builds the reader page background for [bgColorHex].
         *
         * A malformed colour falls back to opaque white rather than throwing: a
         * bad stored value must not take the reader down. The grid line colour is
         * the App theme's `giGridLine`, so it tracks the day/night theme exactly
         * like the browsing pages do.
         *
         * Takes a [View] rather than a `Context` because the colour roles are
         * resolved through `MaterialColors`, which reads attributes off a themed
         * view.
         */
        fun create(view: View, bgColorHex: String): ReaderGridDrawable {
            val bg = try {
                Color.parseColor(if (bgColorHex.startsWith("#")) bgColorHex else "#$bgColorHex")
            } catch (e: IllegalArgumentException) {
                Color.WHITE
            }
            return ReaderGridDrawable(
                bgColor = bg,
                gridColor = MaterialColors.getColor(view, R.attr.giGridLine),
                step = view.resources.getDimension(R.dimen.gi_grid_unit),
                strokeWidth = view.resources.getDimension(R.dimen.gi_grid_line)
            )
        }
    }
}
