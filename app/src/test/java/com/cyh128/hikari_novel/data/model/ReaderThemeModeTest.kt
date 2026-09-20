package com.cyh128.hikari_novel.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The reader page is deliberately two-state (Day / Night). `System` survives only
 * as the legacy persisted value and must fold into a concrete state exactly once.
 *
 * See docs/reader-koodo-alignment.md P0-1.
 */
class ReaderThemeModeTest {

    @Test
    fun legacySystemFoldsIntoTheMatchingAppThemeState() {
        assertEquals(ReaderThemeMode.Night, ReaderThemeMode.System.converge(systemIsDark = true))
        assertEquals(ReaderThemeMode.Day, ReaderThemeMode.System.converge(systemIsDark = false))
    }

    @Test
    fun concreteModesAreUnaffectedByConvergence() {
        assertEquals(ReaderThemeMode.Day, ReaderThemeMode.Day.converge(systemIsDark = true))
        assertEquals(ReaderThemeMode.Night, ReaderThemeMode.Night.converge(systemIsDark = false))
    }

    @Test
    fun parsesPersistedValuesAndTreatsUnknownAsLegacy() {
        assertEquals(ReaderThemeMode.Day, ReaderThemeMode.fromString("Day"))
        assertEquals(ReaderThemeMode.Night, ReaderThemeMode.fromString("Night"))
        assertEquals(ReaderThemeMode.System, ReaderThemeMode.fromString("System"))
        assertEquals(ReaderThemeMode.System, ReaderThemeMode.fromString(null))
        assertEquals(ReaderThemeMode.System, ReaderThemeMode.fromString("garbage"))
    }
}
