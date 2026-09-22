package com.cyh128.hikari_novel.ui.read.vertical

import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import com.cyh128.hikari_novel.reader.domain.VerticalTouchPolicy
import com.cyh128.hikari_novel.util.ReaderTouchJudge

/**
 * Handles only vertical-reader gesture classification. It never mutates
 * ViewModel/session state; the Fragment supplies the callbacks.
 *
 * The reader scrolls continuously, so the one gesture recognised here is a tap
 * that toggles the chrome. Chapter switching is deliberately *not* wired to the
 * screen edges: the left/right thirds used to jump a chapter outright, which
 * mixed paging semantics into scroll mode and could move the reading position
 * on a misread tap. See [VerticalTouchPolicy].
 */
class VerticalReaderTouchHandler(
    private val interactiveChildren: List<View>,
    private val touchJudge: ReaderTouchJudge,
    private val onToggleChrome: () -> Unit
) {
    private val interactiveRect = Rect()
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var ignoreCurrentGesture = false
    private var touchStartedOnInteractiveChild = false

    /** Return false so NestedScrollView keeps receiving scroll gestures. */
    fun onTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                ignoreCurrentGesture = false
                touchStartedOnInteractiveChild = isInteractiveChildTouch(event)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                ignoreCurrentGesture = true
            }

            MotionEvent.ACTION_UP -> {
                val deltaX = event.x - touchStartX
                val deltaY = event.y - touchStartY
                if (!ignoreCurrentGesture &&
                    VerticalTouchPolicy.shouldToggleReaderBar(
                        isTap = touchJudge.isTap(deltaX, deltaY),
                        startedOnInteractiveChild = touchStartedOnInteractiveChild
                    )
                ) {
                    onToggleChrome()
                }
                reset()
            }

            MotionEvent.ACTION_CANCEL -> reset()
        }
        return false
    }

    fun cancel() = reset()

    private fun isInteractiveChildTouch(event: MotionEvent): Boolean =
        interactiveChildren.any { child ->
            child.getGlobalVisibleRect(interactiveRect) &&
                interactiveRect.contains(event.rawX.toInt(), event.rawY.toInt())
        }

    private fun reset() {
        ignoreCurrentGesture = false
        touchStartedOnInteractiveChild = false
    }
}
