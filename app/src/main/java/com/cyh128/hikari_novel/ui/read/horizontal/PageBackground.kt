package com.cyh128.hikari_novel.ui.read.horizontal

import android.graphics.Canvas
import android.graphics.drawable.Drawable

/**
 * Draws a horizontal page's background across the whole canvas.
 *
 * The three page views ([PageText] / [PageImage] / [PageLoading]) each painted a
 * flat colour with `canvas.drawColor`. They now share this entry point so the
 * reader page background (reading colour + GalIsland grid) is applied in exactly
 * one place, and so a page that has no background yet still paints its colour.
 *
 * [fallbackColor] keeps the old flat behaviour for any caller that has not been
 * handed a background — the grid is additive, never load-bearing.
 */
internal fun drawPageBackground(canvas: Canvas, background: Drawable?, fallbackColor: Int) {
    if (background == null) {
        canvas.drawColor(fallbackColor)
        return
    }
    background.setBounds(0, 0, canvas.width, canvas.height)
    background.draw(canvas)
}
