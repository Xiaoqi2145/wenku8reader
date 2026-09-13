package com.cyh128.hikari_novel.reader.domain

import com.cyh128.hikari_novel.ui.read.common.ReaderChromeGeometry
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderChromeGeometryTest {
    @Test
    fun hideTranslationUsesOneParentCoordinateSpace() {
        assertEquals(
            100f,
            ReaderChromeGeometry.hideTranslation(
                parentHeight = 1000,
                contentBottomInset = 0,
                barTop = 900
            )
        )
    }

    @Test
    fun hideTranslationAccountsForAnExplicitContentInset() {
        assertEquals(
            76f,
            ReaderChromeGeometry.hideTranslation(
                parentHeight = 1000,
                contentBottomInset = 24,
                barTop = 900
            )
        )
    }

    @Test
    fun hideTranslationMovesTheWholeDockBelowTheParentBounds() {
        // The dock is translated as a whole (background included), so its top
        // must land at or below the parent bottom edge to leave no footprint.
        val parentHeight = 1000
        val dockTop = 780
        val translation = ReaderChromeGeometry.hideTranslation(
            parentHeight = parentHeight,
            contentBottomInset = 0,
            barTop = dockTop
        )
        assert(dockTop + translation >= parentHeight)
    }

    @Test
    fun contentHeightNeverBecomesNegative() {
        assertEquals(0, ReaderChromeGeometry.contentHeight(400, 600))
        assertEquals(376, ReaderChromeGeometry.contentHeight(400, 24))
    }
}
