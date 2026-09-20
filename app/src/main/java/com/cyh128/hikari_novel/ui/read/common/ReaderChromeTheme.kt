package com.cyh128.hikari_novel.ui.read.common

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.cyh128.hikari_novel.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider

/**
 * Re-colours the reader chrome from a [ReaderChromePalette].
 *
 * The GalIsland chrome language is kept intact — a flat surface with a single
 * 1dp hairline separating it from the content, no elevation — but the colours
 * come from the reader palette instead of the App theme. The chrome backgrounds
 * are rebuilt as [LayerDrawable]s mirroring `bg_read_bottom_bar.xml` /
 * `bg_read_status_bar.xml`, because a `layer-list` that mixes a surface colour
 * with a stroke colour cannot be tinted as a whole.
 *
 * See docs/reader-koodo-alignment.md P0-1.
 */
object ReaderChromeTheme {

    private enum class StrokeEdge { Top, Bottom }

    /**
     * Applies [palette] to the three chrome roots.
     *
     * The dock carries the stroke on its top edge and the toolbar on its bottom
     * edge, so the chrome is separated from the page on both sides.
     */
    fun apply(
        context: Context,
        toolbar: MaterialToolbar,
        statusLine: View,
        bottomDock: View,
        palette: ReaderChromePalette
    ) {
        toolbar.background = chromeBackground(context, palette, StrokeEdge.Bottom)
        toolbar.setTitleTextColor(palette.onSurface)
        toolbar.setSubtitleTextColor(palette.onSurfaceVariant)
        toolbar.setNavigationIconTint(palette.onSurface)

        statusLine.background = chromeBackground(context, palette, StrokeEdge.Top)
        bottomDock.background = chromeBackground(context, palette, StrokeEdge.Top)

        tintChromeContent(statusLine, palette)
        tintChromeContent(bottomDock, palette)
    }

    /**
     * Tints the toolbar's action icons.
     *
     * `MaterialToolbar` exposes a tint for the navigation icon but not for menu
     * icons, so they are tinted item by item. Call this after the menu is
     * inflated — the reader Activities do it from `onCreateOptionsMenu`.
     */
    fun tintMenuIcons(menu: Menu?, palette: ReaderChromePalette) {
        if (menu == null) return
        for (index in 0 until menu.size()) {
            menu.getItem(index).icon
                ?.mutate()
                ?.setTintList(ColorStateList.valueOf(palette.onSurface))
        }
    }

    /**
     * Walks the chrome subtree and maps GalIsland's colour roles onto widgets.
     *
     * `MaterialButton` is a `TextView`, so it must be matched first.
     */
    private fun tintChromeContent(view: View, palette: ReaderChromePalette) {
        when (view) {
            is MaterialButton -> {
                // Text buttons carry their label; icon-only buttons keep the
                // quieter GalIsland role, matching Widget.GalIsland.Button.IconButton.
                val content = if (view.text.isNullOrEmpty()) {
                    palette.onSurfaceVariant
                } else {
                    palette.onSurface
                }
                view.setTextColor(content)
                view.iconTint = ColorStateList.valueOf(content)
                view.strokeColor = ColorStateList.valueOf(palette.outline)
            }

            is Slider -> {
                view.trackActiveTintList = ColorStateList.valueOf(palette.onSurface)
                view.trackInactiveTintList = ColorStateList.valueOf(palette.onSurfaceVariant)
                view.thumbTintList = ColorStateList.valueOf(palette.onSurface)
                view.haloTintList = ColorStateList.valueOf(palette.onSurfaceVariant)
            }

            is TextView -> view.setTextColor(palette.onSurfaceVariant)

            is ImageView -> view.imageTintList = ColorStateList.valueOf(palette.onSurfaceVariant)

            is ViewGroup -> for (index in 0 until view.childCount) {
                tintChromeContent(view.getChildAt(index), palette)
            }

            else -> Unit
        }
    }

    /**
     * Surface plus a single hairline, mirroring the XML chrome backgrounds.
     * A `GradientDrawable` cannot stroke one edge only, hence the two layers.
     */
    private fun chromeBackground(
        context: Context,
        palette: ReaderChromePalette,
        strokeEdge: StrokeEdge
    ): Drawable {
        val strokePx = context.resources.getDimensionPixelSize(R.dimen.gi_stroke)
        val surfaceLayer = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(palette.surface)
        }
        val strokeLayer = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(palette.outline)
        }
        return LayerDrawable(arrayOf(surfaceLayer, strokeLayer)).apply {
            setLayerHeight(1, strokePx)
            setLayerGravity(
                1,
                if (strokeEdge == StrokeEdge.Top) Gravity.TOP else Gravity.BOTTOM
            )
        }
    }
}
