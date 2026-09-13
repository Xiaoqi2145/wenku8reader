package com.cyh128.hikari_novel.ui.other

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.cyh128.hikari_novel.R
import com.google.android.material.color.MaterialColors

/**
 * Subtle GalIsland grid surface (template .window-shell): a 40dp grid drawn on
 * the app background. Used as the shared root of browsing pages and secondary
 * pages alike; cards and toolbars sit on top as opaque "paper".
 */
class LibrarySurfaceLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : CoordinatorLayout(context, attrs) {
    private val step = resources.getDimension(R.dimen.gi_grid_unit)
    private val gridPaint = Paint().apply {
        color = MaterialColors.getColor(this@LibrarySurfaceLayout, R.attr.giGridLine)
        strokeWidth = resources.getDimension(R.dimen.gi_grid_line)
    }

    init { setWillNotDraw(false) }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        var x = 0f
        while (x < width) { canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint); x += step }
        var y = 0f
        while (y < height) { canvas.drawLine(0f, y, width.toFloat(), y, gridPaint); y += step }
    }
}
