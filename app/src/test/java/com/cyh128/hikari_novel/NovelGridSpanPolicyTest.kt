package com.cyh128.hikari_novel

import com.cyh128.hikari_novel.ui.other.NovelGridSpanPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class NovelGridSpanPolicyTest {
    @Test
    fun phonesUseTheGalIslandTwoColumnGrid() {
        assertEquals(2, NovelGridSpanPolicy.spanCount(320, isTablet = false))
        assertEquals(2, NovelGridSpanPolicy.spanCount(372, isTablet = false))
    }

    @Test
    fun tabletColumnsGrowFromThreeWithoutOvercrowdingCards() {
        assertEquals(3, NovelGridSpanPolicy.spanCount(600, isTablet = true))
        assertEquals(4, NovelGridSpanPolicy.spanCount(800, isTablet = true))
        assertEquals(6, NovelGridSpanPolicy.spanCount(1097, isTablet = true))
    }

    @Test
    fun tabletColumnsAreClampedAtBothEnds() {
        assertEquals(2, NovelGridSpanPolicy.spanCount(320, isTablet = true))
        assertEquals(8, NovelGridSpanPolicy.spanCount(2400, isTablet = true))
        assertEquals(2, NovelGridSpanPolicy.spanCount(0, isTablet = true))
    }

    @Test
    fun itemGutterIsIncludedInTheMinimumCellCalculation() {
        assertEquals(
            3,
            NovelGridSpanPolicy.spanCount(
                availableWidthDp = 492,
                isTablet = true,
                minCellWidthDp = 152,
                itemGutterDp = 6
            )
        )
        assertEquals(
            4,
            NovelGridSpanPolicy.spanCount(
                availableWidthDp = 656,
                isTablet = true,
                minCellWidthDp = 152,
                itemGutterDp = 6
            )
        )
    }
}
