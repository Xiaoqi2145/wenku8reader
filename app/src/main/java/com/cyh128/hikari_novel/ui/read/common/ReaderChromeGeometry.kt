package com.cyh128.hikari_novel.ui.read.common

/** Pure geometry helpers for the reader chrome. */
object ReaderChromeGeometry {
    data class ContentInsets(val top: Int, val bottom: Int)

    /**
     * Returns the translation needed to move a bottom bar completely below its
     * parent content bounds. All values are in the same parent coordinate space.
     */
    fun hideTranslation(parentHeight: Int, contentBottomInset: Int, barTop: Int): Float {
        return (parentHeight - contentBottomInset - barTop).coerceAtLeast(0).toFloat()
    }

    /** The content area available above a system bottom inset. */
    fun contentHeight(parentHeight: Int, systemBottomInset: Int): Int {
        return (parentHeight - systemBottomInset).coerceAtLeast(0)
    }

    /**
     * Returns the content-safe insets for a reader viewport. The chrome is an
     * overlay, so the reader must reserve the measured toolbar/dock bounds
     * while the controls are visible and only the always-on status line while
     * they are hidden.
     */
    fun contentInsets(
        parentHeight: Int,
        toolbarBottom: Int,
        bottomDockTop: Int,
        statusLineTop: Int,
        chromeShown: Boolean
    ): ContentInsets {
        val height = parentHeight.coerceAtLeast(0)
        val top = if (chromeShown) toolbarBottom else 0
        val bottomBoundary = if (chromeShown) bottomDockTop else statusLineTop
        return ContentInsets(
            top = top.coerceIn(0, height),
            bottom = (height - bottomBoundary).coerceIn(0, height)
        )
    }
}
