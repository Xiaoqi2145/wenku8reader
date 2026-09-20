package com.cyh128.hikari_novel.ui.read.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the reader chrome palette maths.
 *
 * The palette is what makes the reader chrome follow the reading colours instead
 * of the App theme, so the two things worth pinning down are: the surface/primary
 * roles are the reading colours verbatim, and the secondary roles always land
 * between the background and the text (contrast must hold for any pair the user
 * can pick). See docs/reader-koodo-alignment.md P0-1.
 */
class ReaderChromePaletteTest {

    @Test
    fun surfaceAndPrimaryContentAreTheReadingColours() {
        val palette = ReaderChromePalette.from("#ffffff", "000000")
        assertEquals(0xFFFFFFFF.toInt(), palette.surface)
        assertEquals(0xFF000000.toInt(), palette.onSurface)
    }

    @Test
    fun secondaryColoursBlendTowardsTheTextColour() {
        val palette = ReaderChromePalette.from("ffffff", "000000")
        // 0.62 towards black on white -> 0x61 grey.
        assertEquals(0xFF616161.toInt(), palette.onSurfaceVariant)
        // 0.24 towards black -> 0xC2 grey, a hairline tone.
        assertEquals(0xFFC2C2C2.toInt(), palette.outline)
    }

    @Test
    fun darkPairsProduceLightSecondaryColours() {
        val palette = ReaderChromePalette.from("2C2F31", "ffffff")
        assertTrue(red(palette.onSurfaceVariant) > red(palette.surface))
        assertTrue(red(palette.onSurfaceVariant) < red(palette.onSurface))
        assertTrue(red(palette.outline) > red(palette.surface))
        assertTrue(red(palette.outline) < red(palette.onSurfaceVariant))
    }

    @Test
    fun expandsThreeDigitHex() {
        assertEquals(0xFFFFFFFF.toInt(), ReaderChromePalette.parseHex("fff"))
        assertEquals(0xFFFFFFFF.toInt(), ReaderChromePalette.parseHex("#FFF"))
    }

    @Test
    fun malformedHexFallsBackToBlackInsteadOfThrowing() {
        assertEquals(0xFF000000.toInt(), ReaderChromePalette.parseHex(""))
        assertEquals(0xFF000000.toInt(), ReaderChromePalette.parseHex("zzzzzz"))
        assertEquals(0xFF000000.toInt(), ReaderChromePalette.parseHex("#12345"))
    }

    @Test
    fun blendResultIsAlwaysOpaque() {
        val blended = ReaderChromePalette.blend(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0.5f)
        assertEquals(0xFF, (blended ushr 24) and 0xFF)
    }

    private fun red(color: Int): Int = (color shr 16) and 0xFF
}
