package com.cyh128.hikari_novel.reader.domain

import kotlin.math.abs

/**
 * Classifies one touch gesture from DOWN to UP.
 *
 * A tap cannot be decided from the net displacement alone. A scroll that ends
 * near where it started, a finger held still before lifting, and a tap meant to
 * stop a fling all look identical by that measure — and in the vertical reader
 * a misread tap used to jump a chapter and take the persisted position with it.
 *
 * The tracker therefore remembers whether the finger ever travelled further
 * than [movementSlop] during the gesture, and reports a tap only when it never
 * did. It also owns the start point, so callers do not have to keep their own
 * copy of the gesture state (which is how the two copies drifted apart before).
 *
 * Pure Kotlin on purpose: this is the part of the gesture decision that has to
 * be unit-tested. Geometry and slop values still come from `ReaderTouchJudge`,
 * which is the single source for them.
 */
class ReaderGestureTracker(
    private val tapSlop: Float,
    private val movementSlop: Float = tapSlop
) {
    /** Where the gesture started; tap zones are resolved against this, not the lift point. */
    var startX: Float = 0f
        private set
    var startY: Float = 0f
        private set

    /** True once the finger has travelled beyond [movementSlop]; latched until the next DOWN. */
    var moved: Boolean = false
        private set

    /** True once a second finger joined; a multi-touch gesture is never a tap. */
    var multiTouch: Boolean = false
        private set

    fun onDown(x: Float, y: Float) {
        startX = x
        startY = y
        moved = false
        multiTouch = false
    }

    /**
     * Records the furthest travel, not the latest position: once the gesture has
     * been a drag it can never become a tap again, however it ends.
     */
    fun onMove(x: Float, y: Float) {
        if (moved) return
        if (abs(x - startX) > movementSlop || abs(y - startY) > movementSlop) moved = true
    }

    fun onPointerDown() {
        multiTouch = true
    }

    /** [x] / [y] are the lift coordinates. */
    fun isTap(x: Float, y: Float): Boolean =
        !moved && !multiTouch &&
            abs(x - startX) <= tapSlop && abs(y - startY) <= tapSlop

    fun cancel() {
        moved = false
        multiTouch = false
    }
}
