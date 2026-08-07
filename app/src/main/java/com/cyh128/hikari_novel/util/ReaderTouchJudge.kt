package com.cyh128.hikari_novel.util

import android.content.Context
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.max

enum class ReaderTapArea {
    Previous,
    Center,
    Next
}

class ReaderTouchJudge(context: Context) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val density = context.resources.displayMetrics.density
    private val tapSlop = max(touchSlop, 10f * density)
    private val swipeSlop = max(touchSlop * 4f, 56f * density)

    fun isTap(deltaX: Float, deltaY: Float): Boolean =
        abs(deltaX) <= tapSlop && abs(deltaY) <= tapSlop

    fun isHorizontalSwipe(deltaX: Float, deltaY: Float): Boolean =
        abs(deltaX) >= swipeSlop && abs(deltaX) > abs(deltaY) * 1.25f

    fun tapArea(x: Float, width: Int): ReaderTapArea =
        when {
            x < width / 3f -> ReaderTapArea.Previous
            x > width * 2f / 3f -> ReaderTapArea.Next
            else -> ReaderTapArea.Center
        }
}
