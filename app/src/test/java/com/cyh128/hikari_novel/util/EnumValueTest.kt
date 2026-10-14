package com.cyh128.hikari_novel.util

import org.junit.Assert.assertEquals
import org.junit.Test

private enum class Sample { Alpha, Beta, Gamma }

/**
 * The persisted ordinals in MMKV outlive the enum they were written against.
 *
 * These reads happen during `Application.onCreate`, before the crash reporter
 * is installed, so an `IndexOutOfBoundsException` here is a launch-time crash
 * loop that the user cannot get out of.
 */
class EnumValueTest {

    @Test
    fun anInRangeIndexResolvesToItsMember() {
        assertEquals(Sample.Beta, enumValue(Sample.entries, 1, Sample.Alpha))
    }

    @Test
    fun anIndexPastTheEndFallsBack() {
        // A stored value from a build that had more members than this one.
        assertEquals(Sample.Alpha, enumValue(Sample.entries, 99, Sample.Alpha))
    }

    @Test
    fun aNegativeIndexFallsBack() {
        assertEquals(Sample.Alpha, enumValue(Sample.entries, -1, Sample.Alpha))
    }

    @Test
    fun anEmptyEntryListFallsBack() {
        assertEquals(Sample.Alpha, enumValue(emptyList<Sample>(), 0, Sample.Alpha))
    }
}
