package com.cyh128.hikari_novel.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers slider row formatting and clamping.
 * See docs/reader-settings-round4-plan.md R4-3.
 */
class ReaderSliderValueTest {

    // ===== format =====

    @Test
    fun formatDropsDecimalForWholeNumbers() {
        assertEquals("1", ReaderSliderValue.format(1f))
        assertEquals("16", ReaderSliderValue.format(16f))
        assertEquals("100", ReaderSliderValue.format(100f))
        assertEquals("0", ReaderSliderValue.format(0f))
    }

    @Test
    fun formatKeepsOneDecimalForFractions() {
        assertEquals("1.5", ReaderSliderValue.format(1.5f))
        assertEquals("0.1", ReaderSliderValue.format(0.1f))
        assertEquals("4.2", ReaderSliderValue.format(4.2f))
    }

    @Test
    fun formatRoundsFloatingPointNoiseAway() {
        // 0.1f + 0.2f style drift must not surface as "1.7000001".
        assertEquals("1.7", ReaderSliderValue.format(1.7000001f))
        assertEquals("2", ReaderSliderValue.format(1.9999999f))
    }

    @Test
    fun formatUsesDotAsDecimalSeparator() {
        // Locale-independent: the value is a number, not prose.
        assertEquals("1.5", ReaderSliderValue.format(1.5f))
    }

    // ===== labels =====

    @Test
    fun labelsFormatTheirBounds() {
        assertEquals("0", ReaderSliderValue.minLabel(0f))
        assertEquals("50", ReaderSliderValue.maxLabel(50f))
        assertEquals("0.5", ReaderSliderValue.minLabel(0.5f))
        assertEquals("4", ReaderSliderValue.maxLabel(4f))
    }

    // ===== parseAndClamp =====

    @Test
    fun parseClampsAboveRange() {
        assertEquals(50f, ReaderSliderValue.parseAndClamp("999", 1f, 50f)!!, 0.0001f)
    }

    @Test
    fun parseClampsBelowRange() {
        assertEquals(0f, ReaderSliderValue.parseAndClamp("-12", 0f, 40f)!!, 0.0001f)
    }

    @Test
    fun parseKeepsInRangeValue() {
        assertEquals(23f, ReaderSliderValue.parseAndClamp("23", 1f, 50f)!!, 0.0001f)
    }

    @Test
    fun parseAcceptsFractionalInput() {
        assertEquals(1.5f, ReaderSliderValue.parseAndClamp("1.5", 1f, 5f)!!, 0.0001f)
    }

    @Test
    fun parseTrimsSurroundingSpace() {
        assertEquals(7f, ReaderSliderValue.parseAndClamp("  7  ", 0f, 20f)!!, 0.0001f)
    }

    @Test
    fun parseReturnsNullForNonNumeric() {
        assertNull(ReaderSliderValue.parseAndClamp("", 1f, 50f))
        assertNull(ReaderSliderValue.parseAndClamp("abc", 1f, 50f))
        assertNull(ReaderSliderValue.parseAndClamp("1.2.3", 1f, 50f))
    }

    @Test
    fun parseToleratesReversedBounds() {
        // Defensive: a caller that swapped the two must still clamp, not throw.
        assertEquals(5f, ReaderSliderValue.parseAndClamp("9", 5f, 1f)!!, 0.0001f)
    }

    // ===== snapToStep =====

    @Test
    fun snapRoundsToNearestStep() {
        assertEquals(1.5f, ReaderSliderValue.snapToStep(1.53f, 1f, 0.1f), 0.0001f)
        assertEquals(1.6f, ReaderSliderValue.snapToStep(1.56f, 1f, 0.1f), 0.0001f)
    }

    @Test
    fun snapKeepsValueAlreadyOnGrid() {
        assertEquals(2.5f, ReaderSliderValue.snapToStep(2.5f, 1f, 0.5f), 0.0001f)
    }

    @Test
    fun snapRoundsUpPastMidpoint() {
        assertEquals(1.1f, ReaderSliderValue.snapToStep(1.08f, 1f, 0.1f), 0.0001f)
    }

    @Test
    fun snapMeasuresFromValueFromNotZero() {
        // Measured from valueFrom 1 with step 0.3, 1.2 is two thirds of the way
        // to 1.3 and must round up. Measured from 0 it would sit exactly on the
        // grid and stay at 1.2 — so this pins the origin to valueFrom.
        assertEquals(1.3f, ReaderSliderValue.snapToStep(1.2f, 1f, 0.3f), 0.0001f)
    }

    @Test
    fun snapIsNoOpForNonPositiveStep() {
        assertEquals(1.23f, ReaderSliderValue.snapToStep(1.23f, 0f, 0f), 0.0001f)
    }

    @Test
    fun snapKeepsBoundsReachable() {
        // The endpoints must survive snapping, or the slider could never be set
        // to its own minimum/maximum.
        assertEquals(1f, ReaderSliderValue.snapToStep(1f, 1f, 0.1f), 0.0001f)
        assertEquals(5f, ReaderSliderValue.snapToStep(5f, 1f, 0.1f), 0.0001f)
        assertEquals(50f, ReaderSliderValue.snapToStep(50f, 1f, 1f), 0.0001f)
    }
}
