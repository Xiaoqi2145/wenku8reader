package com.cyh128.hikari_novel.ui.read.common

/** Pure geometry helpers for the reader chrome. */
object ReaderChromeGeometry {
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
}
