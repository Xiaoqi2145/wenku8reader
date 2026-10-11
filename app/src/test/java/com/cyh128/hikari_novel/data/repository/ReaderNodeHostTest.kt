package com.cyh128.hikari_novel.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one-shot repair of a node host that was forced onto the blocked host.
 *
 * The removed `getWenku8Node()` rewrite persisted `www.wenku8.cc` into every
 * install that had ever used `.net`, so the repair has to recognise exactly that
 * value and leave everything else alone. Pinned here so the rule cannot drift
 * into rewriting a host the user chose on purpose.
 */
class ReaderNodeHostTest {

    @Test
    fun theForcedHostIsRepaired() {
        assertEquals(ReaderNodeHost.WORKING, ReaderNodeHost.migrate(ReaderNodeHost.BLOCKED))
    }

    @Test
    fun theWorkingHostIsLeftAlone() {
        assertEquals(ReaderNodeHost.WORKING, ReaderNodeHost.migrate(ReaderNodeHost.WORKING))
    }

    @Test
    fun anUnknownHostIsNotRewritten() {
        // A host typed in by hand is the user's own choice; the migration only
        // recognises the one value the old rewrite produced.
        assertEquals("www.example.invalid", ReaderNodeHost.migrate("www.example.invalid"))
    }

    @Test
    fun aMissingHostStaysMissingForTheDefaultToHandle() {
        assertEquals("", ReaderNodeHost.migrate(null))
        assertEquals("", ReaderNodeHost.migrate(""))
    }

    @Test
    fun theDefaultHostIsTheOneThatServesRequests() {
        assertTrue(ReaderNodeHost.isWorking(ReaderNodeHost.WORKING))
        assertFalse(ReaderNodeHost.isWorking(ReaderNodeHost.BLOCKED))
        assertFalse(ReaderNodeHost.isWorking(null))
    }
}
