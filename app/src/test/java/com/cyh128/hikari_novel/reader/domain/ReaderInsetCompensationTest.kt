package com.cyh128.hikari_novel.reader.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers the inset compensation rule for the reader menu toggle.
 *
 * The case worth pinning down is the supersede rule: a second inset change can
 * arrive before the first compensation has been drawn, and it must build on the
 * pending target. Building on the raw scroll offset instead compensated the first
 * change twice and shifted the text by a whole toolbar height.
 */
class ReaderInsetCompensationTest {

    private val toolbarHeight = 168

    @Test
    fun compensatesByTheInsetDelta() {
        assertEquals(
            1_200,
            ReaderInsetCompensation.target(
                currentScrollY = 1_032,
                pendingTarget = null,
                topDelta = toolbarHeight
            )
        )
    }

    @Test
    fun supersedesAPendingCompensationInsteadOfQueueingIt() {
        // Menu hidden then shown again before the first scroll was drawn: the
        // pending target (1200) is what the user currently sees, so the new
        // compensation must build on it, not on the stale scrollY (1032).
        assertEquals(
            1_032,
            ReaderInsetCompensation.target(
                currentScrollY = 1_032,
                pendingTarget = 1_200,
                topDelta = -toolbarHeight
            )
        )
    }

    @Test
    fun repeatedTogglesAccumulateOnThePendingTarget() {
        val first = ReaderInsetCompensation.target(500, null, toolbarHeight)
        assertEquals(500 + toolbarHeight, first)
        val second = ReaderInsetCompensation.target(500, first, toolbarHeight)
        assertEquals(500 + toolbarHeight * 2, second)
    }

    @Test
    fun nothingIsOwedWhenTheInsetDidNotChange() {
        assertNull(ReaderInsetCompensation.target(1_032, null, topDelta = 0))
    }

    @Test
    fun nothingIsOwedAtTheTopOfTheContent() {
        // At scrollY 0 the text should simply start below the chrome; scrolling to
        // a positive offset would push the first line out of view.
        assertNull(ReaderInsetCompensation.target(0, null, toolbarHeight))
    }

    @Test
    fun neverReturnsANegativeOffset() {
        assertEquals(
            0,
            ReaderInsetCompensation.target(
                currentScrollY = 40,
                pendingTarget = null,
                topDelta = -toolbarHeight
            )
        )
    }
}
