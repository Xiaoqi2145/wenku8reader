package com.cyh128.hikari_novel.ui.other

/** Pure responsive span policy shared by all novel grids. */
object NovelGridSpanPolicy {
    private const val PHONE_SPAN_COUNT = 2
    private const val TABLET_MIN_SPAN_COUNT = 2
    private const val TABLET_MAX_SPAN_COUNT = 8
    private const val ITEM_GUTTER_DP = 6
    private const val DEFAULT_MIN_CELL_WIDTH_DP = 152

    fun spanCount(
        availableWidthDp: Int,
        isTablet: Boolean,
        minCellWidthDp: Int = DEFAULT_MIN_CELL_WIDTH_DP,
        itemGutterDp: Int = ITEM_GUTTER_DP
    ): Int {
        if (!isTablet || availableWidthDp <= 0) return PHONE_SPAN_COUNT
        val cellWithGutters = (minCellWidthDp + itemGutterDp * 2).coerceAtLeast(1)
        val count = (availableWidthDp + itemGutterDp) / cellWithGutters
        return count.coerceIn(TABLET_MIN_SPAN_COUNT, TABLET_MAX_SPAN_COUNT)
    }
}
