package com.cyh128.hikari_novel.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the update-check rules that the release feed forced us to handle.
 *
 * The upstream repository's newest tag is `bye` (its "stopped maintaining"
 * notice), and the installed version name is `3.9.10 DEBUG` in debug builds.
 * String equality on either side produced an update prompt on every launch.
 */
class VersionTagTest {

    @Test
    fun plainVersionParses() {
        assertEquals(listOf(3, 9, 10), VersionTag.parse("3.9.10"))
    }

    @Test
    fun aVPrefixIsAccepted() {
        assertEquals(listOf(3, 9, 10), VersionTag.parse("v3.9.10"))
    }

    @Test
    fun aDebugSuffixIsIgnored() {
        // Debug builds report "3.9.10 DEBUG"; the suffix must not hide the version.
        assertEquals(listOf(3, 9, 10), VersionTag.parse("3.9.10 DEBUG"))
    }

    @Test
    fun aNonVersionTagIsRejected() {
        // The upstream "stopped maintaining" release; it must never be read as a
        // newer version.
        assertNull(VersionTag.parse("bye"))
        assertNull(VersionTag.parse("latest"))
        assertNull(VersionTag.parse(null))
        assertNull(VersionTag.parse(""))
    }

    @Test
    fun versionsCompareNumericallyNotAsText() {
        // "3.9.9" > "3.9.10" as strings, which is the wrong answer.
        assertTrue(VersionTag.compare(listOf(3, 9, 10), listOf(3, 9, 9)) > 0)
        assertTrue(VersionTag.compare(listOf(3, 9, 9), listOf(3, 9, 10)) < 0)
    }

    @Test
    fun missingTrailingComponentsCountAsZero() {
        assertEquals(0, VersionTag.compare(listOf(3, 10), listOf(3, 10, 0)))
    }

    @Test
    fun anOlderTagIsNotAnUpdate() {
        assertFalse(VersionTag.anyNewerThan("3.9.10", listOf("3.9.9", "3.9.8")))
    }

    @Test
    fun theInstalledVersionIsNotAnUpdate() {
        assertFalse(VersionTag.anyNewerThan("3.9.10", listOf("3.9.10")))
    }

    @Test
    fun aNewerTagIsAnUpdate() {
        assertTrue(VersionTag.anyNewerThan("3.9.10", listOf("3.9.11")))
        assertTrue(VersionTag.anyNewerThan("3.9.10", listOf("3.10")))
    }

    @Test
    fun nonVersionTagsInTheFeedAreIgnored() {
        // This is the actual upstream feed shape: a "bye" notice plus real
        // releases. Only the real ones may trigger the prompt.
        assertFalse(VersionTag.anyNewerThan("3.9.10", listOf("bye", "3.9.10", "3.9.9")))
        assertTrue(VersionTag.anyNewerThan("3.9.10", listOf("bye", "3.9.11")))
    }

    @Test
    fun anUnparseableInstalledVersionNeverPrompts() {
        // Better to stay quiet than to nag a user we cannot compare against.
        assertFalse(VersionTag.anyNewerThan(null, listOf("9.9.9")))
        assertFalse(VersionTag.anyNewerThan("", listOf("9.9.9")))
    }
}
