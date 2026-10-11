package com.cyh128.hikari_novel.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the reader colour axes. See docs/plan/reader-settings-round4-plan.md R4-1.
 */
class ReaderColorSwatchesTest {

    // ===== normalize =====

    @Test
    fun normalizeStripsHashAndUppercases() {
        assertEquals("FFFFFF", ReaderColorSwatches.normalize("#ffffff"))
        assertEquals("FFFFFF", ReaderColorSwatches.normalize("  ffffff "))
        assertEquals("2C2F31", ReaderColorSwatches.normalize("#2C2F31"))
    }

    // ===== axis =====

    @Test
    fun axisPutsPresetsFirstAndMarksOnlyCustomsRemovable() {
        val axis = ReaderColorSwatches.axis(
            presets = listOf("FFFFFF", "000000"),
            custom = listOf("123456")
        )

        assertEquals(3, axis.size)
        assertEquals("FFFFFF", axis[0].hex)
        assertEquals("000000", axis[1].hex)
        assertEquals("123456", axis[2].hex)
        assertFalse(axis[0].isCustom)
        assertFalse(axis[1].isCustom)
        assertTrue(axis[2].isCustom)
    }

    @Test
    fun axisNormalizesBothSources() {
        val axis = ReaderColorSwatches.axis(
            presets = listOf("#ffffff"),
            custom = listOf("abcdef")
        )

        assertEquals("FFFFFF", axis[0].hex)
        assertEquals("ABCDEF", axis[1].hex)
    }

    // ===== indexOf =====

    @Test
    fun indexOfIgnoresCaseAndHash() {
        val axis = ReaderColorSwatches.axis(
            presets = listOf("FFFFFF", "2C2F31"),
            custom = emptyList()
        )

        assertEquals(0, ReaderColorSwatches.indexOf(axis, "#ffffff"))
        assertEquals(1, ReaderColorSwatches.indexOf(axis, "2c2f31"))
        assertEquals(-1, ReaderColorSwatches.indexOf(axis, "000000"))
    }

    // ===== linkedTextColorFor =====

    @Test
    fun darkBackgroundForcesWhiteText() {
        assertEquals(
            "FFFFFF",
            ReaderColorSwatches.linkedTextColorFor("2C2F31", "000000")
        )
    }

    @Test
    fun darkBackgroundLeavesWhiteTextAlone() {
        // Already white: no change, so the caller does not re-write the axis.
        assertNull(ReaderColorSwatches.linkedTextColorFor("2C2F31", "FFFFFF"))
    }

    @Test
    fun whiteBackgroundTurnsWhiteTextBlack() {
        assertEquals(
            "000000",
            ReaderColorSwatches.linkedTextColorFor("FFFFFF", "FFFFFF")
        )
    }

    @Test
    fun whiteBackgroundLeavesNonWhiteTextAlone() {
        // A deliberate brown stays brown on white.
        assertNull(ReaderColorSwatches.linkedTextColorFor("FFFFFF", "594429"))
    }

    @Test
    fun otherBackgroundsNeverLink() {
        assertNull(ReaderColorSwatches.linkedTextColorFor("E9D8BC", "000000"))
        assertNull(ReaderColorSwatches.linkedTextColorFor("C5E7CF", "000000"))
        // White text on a beige background is the user's choice, not our call.
        assertNull(ReaderColorSwatches.linkedTextColorFor("E9D8BC", "FFFFFF"))
    }

    // ===== addToAxis =====

    @Test
    fun addAppendsNormalizedValue() {
        val result = ReaderColorSwatches.addToAxis(
            custom = listOf("123456"),
            hex = "#ABCDEF",
            presets = ReaderColorSwatches.BACKGROUNDS
        )

        assertEquals(listOf("123456", "ABCDEF"), result)
    }

    @Test
    fun addRejectsBlank() {
        assertNull(
            ReaderColorSwatches.addToAxis(emptyList(), "  ", ReaderColorSwatches.BACKGROUNDS)
        )
    }

    @Test
    fun addRejectsPresetDuplicates() {
        assertNull(
            ReaderColorSwatches.addToAxis(
                emptyList(),
                "#ffffff",
                ReaderColorSwatches.BACKGROUNDS
            )
        )
    }

    @Test
    fun addRejectsCustomDuplicates() {
        assertNull(
            ReaderColorSwatches.addToAxis(
                listOf("ABCDEF"),
                "abcdef",
                ReaderColorSwatches.BACKGROUNDS
            )
        )
    }

    @Test
    fun addRejectsBeyondLimit() {
        val full = (1..ReaderColorSwatches.MAX_CUSTOM_COLORS).map { "%06X".format(it) }

        assertNull(
            ReaderColorSwatches.addToAxis(full, "ABCDEF", ReaderColorSwatches.BACKGROUNDS)
        )
    }

    @Test
    fun addAcceptsExactlyAtLimitMinusOne() {
        val almost = (1 until ReaderColorSwatches.MAX_CUSTOM_COLORS).map { "%06X".format(it) }

        val result = ReaderColorSwatches.addToAxis(
            almost,
            "ABCDEF",
            ReaderColorSwatches.BACKGROUNDS
        )

        assertEquals(ReaderColorSwatches.MAX_CUSTOM_COLORS, result!!.size)
    }

    // ===== removeFromAxis =====

    @Test
    fun removeDropsMatchingEntryRegardlessOfCase() {
        val result = ReaderColorSwatches.removeFromAxis(
            listOf("ABCDEF", "123456"),
            "#abcdef"
        )

        assertEquals(listOf("123456"), result)
    }

    @Test
    fun removeOfAbsentValueIsNoOp() {
        val result = ReaderColorSwatches.removeFromAxis(listOf("ABCDEF"), "000000")

        assertEquals(listOf("ABCDEF"), result)
    }

    // ===== preset constants =====

    @Test
    fun presetsMatchKoodoValues() {
        assertEquals(
            listOf("FFFFFF", "2C2F31", "E9D8BC", "C5E7CF"),
            ReaderColorSwatches.BACKGROUNDS
        )
        assertEquals(
            listOf("000000", "FFFFFF", "594429", "36503E"),
            ReaderColorSwatches.TEXTS
        )
    }

    @Test
    fun presetsAreAlreadyNormalized() {
        // The linkage rules compare against literal constants, so a preset that
        // was not normalized would silently stop matching.
        (ReaderColorSwatches.BACKGROUNDS + ReaderColorSwatches.TEXTS).forEach {
            assertEquals(it, ReaderColorSwatches.normalize(it))
        }
    }
}
