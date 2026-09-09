package com.cyh128.hikari_novel.reader.domain

/** Pure tap areas used by the vertical-reader gesture adapter. */
enum class VerticalTapArea {
    Previous,
    Center,
    Next
}

/** Pure policy for deciding whether a gesture toggles reader controls. */
object VerticalTouchPolicy {
    fun tapArea(x: Float, width: Int): VerticalTapArea {
        if (width <= 0) return VerticalTapArea.Center
        return when {
            x < width / 3f -> VerticalTapArea.Previous
            x > width * 2f / 3f -> VerticalTapArea.Next
            else -> VerticalTapArea.Center
        }
    }

    fun shouldToggleReaderBar(
        startX: Float,
        width: Int,
        isTap: Boolean,
        startedOnInteractiveChild: Boolean
    ): Boolean =
        isTap &&
            !startedOnInteractiveChild &&
            tapArea(startX, width) == VerticalTapArea.Center
}
