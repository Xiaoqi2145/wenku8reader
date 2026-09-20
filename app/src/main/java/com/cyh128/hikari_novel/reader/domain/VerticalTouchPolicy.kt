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

    /**
     * Classifies a finished gesture into the action it should trigger.
     *
     * Returns null when the gesture was not a tap, or when it started on an
     * interactive child (an image, or the prev/next buttons) that owns the touch.
     *
     * Touch-screen readers cannot hover, so Koodo's four-way hover panels become
     * a three-zone tap split: left third = previous chapter, right third = next
     * chapter, centre = toggle the chrome. See docs/reader-koodo-alignment.md P2-5.
     */
    fun tapAction(
        startX: Float,
        width: Int,
        isTap: Boolean,
        startedOnInteractiveChild: Boolean
    ): VerticalTapArea? =
        if (isTap && !startedOnInteractiveChild) tapArea(startX, width) else null

    fun shouldToggleReaderBar(
        startX: Float,
        width: Int,
        isTap: Boolean,
        startedOnInteractiveChild: Boolean
    ): Boolean =
        tapAction(startX, width, isTap, startedOnInteractiveChild) == VerticalTapArea.Center
}
