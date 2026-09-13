package com.cyh128.hikari_novel.ui.other

import android.content.Context
import android.content.res.Configuration
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Uses the actual list width, including split-screen, rail and list insets. */
class NovelGridLayoutManager(private val context: Context) : GridLayoutManager(context, 3) {
    private val minimumCellWidthDp = 152

    override fun onMeasure(
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State,
        widthSpec: Int,
        heightSpec: Int
    ) {
        val screenWidthDp = context.resources.configuration.screenWidthDp
        val availableWidthPx =
            (View.MeasureSpec.getSize(widthSpec) - paddingLeft - paddingRight).coerceAtLeast(0)
        val availableWidthDp =
            (availableWidthPx / context.resources.displayMetrics.density).toInt()
        val targetSpanCount = when {
            screenWidthDp == Configuration.SCREEN_WIDTH_DP_UNDEFINED || screenWidthDp < 600 -> 3
            availableWidthDp <= 0 -> 3
            else -> (availableWidthDp / minimumCellWidthDp).coerceIn(3, 8)
        }
        if (spanCount != targetSpanCount) spanCount = targetSpanCount
        super.onMeasure(recycler, state, widthSpec, heightSpec)
    }
}
