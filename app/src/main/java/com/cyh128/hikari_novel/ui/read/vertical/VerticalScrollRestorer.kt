package com.cyh128.hikari_novel.ui.read.vertical

import android.view.ViewTreeObserver
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import com.cyh128.hikari_novel.data.model.ReaderAnchor
import com.cyh128.hikari_novel.reader.domain.VerticalScrollTargetCalculator

/**
 * Restores an anchor after TextView layout is ready and retries when a layout
 * pass is still pending. Character offsets naturally survive font/line-spacing
 * changes better than persisted scrollY values.
 */
class VerticalScrollRestorer(
    private val scrollView: NestedScrollView,
    private val textView: TextView,
    private val controller: VerticalReaderController,
    private val onRestored: () -> Unit = {}
) {
    private val targetCalculator = VerticalScrollTargetCalculator(controller.blockMapper)
    private var retainedAnchor: ReaderAnchor? = null
    private var pendingAnchor: ReaderAnchor? = null
    private var layoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    /**
     * Restore [anchor] now when possible, otherwise wait for the next layout.
     * Returns true only when the scroll was applied synchronously.
     */
    fun restore(anchor: ReaderAnchor): Boolean {
        if (!controller.contains(anchor.cid)) {
            retainedAnchor = null
            pendingAnchor = null
            removeLayoutListener()
            return false
        }
        retainedAnchor = anchor
        pendingAnchor = anchor
        if (tryRestore()) return true

        installLayoutRetry()
        textView.post { tryRestore() }
        return false
    }

    /** Reapply an anchor after font/line-spacing changes trigger a new layout. */
    fun restoreAfterLayout(anchor: ReaderAnchor): Boolean {
        if (!controller.contains(anchor.cid)) {
            clear()
            return false
        }
        retainedAnchor = anchor
        pendingAnchor = anchor
        installLayoutRetry()
        textView.requestLayout()
        return false
    }

    /** Reapply the retained anchor after a text reflow. */
    fun reapply(): Boolean {
        val anchor = retainedAnchor ?: return false
        return restoreAfterLayout(anchor)
    }

    /** Legacy fallback used only when no anchor record exists yet. */
    fun restoreLegacy(scrollY: Int) {
        retainedAnchor = null
        pendingAnchor = null
        removeLayoutListener()
        textView.post {
            scrollView.scrollTo(0, scrollY.coerceAtLeast(0))
            onRestored()
        }
    }

    fun clear() {
        retainedAnchor = null
        pendingAnchor = null
        removeLayoutListener()
    }

    private fun tryRestore(): Boolean {
        val anchor = pendingAnchor ?: return false
        val offset = targetCalculator.target(anchor)?.globalOffset ?: return false
        val layout = textView.layout ?: return false
        if (layout.lineCount == 0) return false

        val safeOffset = offset.coerceIn(0, textView.text.length)
        val line = layout.getLineForOffset(safeOffset).coerceIn(0, layout.lineCount - 1)
        val targetY = (textView.top + layout.getLineTop(line)).coerceAtLeast(0)
        scrollView.scrollTo(0, targetY)
        pendingAnchor = null
        removeLayoutListener()
        onRestored()
        return true
    }

    private fun installLayoutRetry() {
        if (layoutListener != null) return
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            if (tryRestore()) removeLayoutListener()
        }
        layoutListener = listener
        textView.viewTreeObserver.addOnGlobalLayoutListener(listener)
    }

    private fun removeLayoutListener() {
        val listener = layoutListener ?: return
        if (textView.viewTreeObserver.isAlive) {
            textView.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
        layoutListener = null
    }
}
