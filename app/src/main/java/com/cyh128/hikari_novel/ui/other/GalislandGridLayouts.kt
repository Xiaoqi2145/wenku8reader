package com.cyh128.hikari_novel.ui.other

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import com.cyh128.hikari_novel.R
import com.google.android.material.color.MaterialColors

/** Shared grid renderer for GalIsland page shells that keep their native layout parent. */
private class GalislandGridRenderer(view: View) {
    private val step = view.resources.getDimension(R.dimen.gi_grid_unit)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(view, R.attr.giGridLine)
        strokeWidth = view.resources.getDimension(R.dimen.gi_grid_line)
        style = Paint.Style.STROKE
    }

    fun draw(canvas: Canvas, width: Int, height: Int) {
        if (step <= 0f) return
        var x = 0f
        while (x < width) {
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
            x += step
        }
        var y = 0f
        while (y < height) {
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
            y += step
        }
    }
}

/** Grid-backed ConstraintLayout for page roots that use constraint attributes. */
class GalislandGridConstraintLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ConstraintLayout(context, attrs) {
    private val grid = GalislandGridRenderer(this)

    init {
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        grid.draw(canvas, width, height)
    }
}

/** Grid-backed LinearLayout for splash, login and status page roots. */
class GalislandGridLinearLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    private val grid = GalislandGridRenderer(this)

    init {
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        grid.draw(canvas, width, height)
    }
}
