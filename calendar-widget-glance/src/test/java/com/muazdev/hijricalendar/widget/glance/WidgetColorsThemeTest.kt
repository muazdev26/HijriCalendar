package com.muazdev.hijricalendar.widget.glance

import com.muazdev.hijricalendar.widgetdata.WidgetTheme
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * `WidgetColors.forTheme` selects between the three palettes.
 *
 * The **resources** behind the forced palettes are checked by `ForcedPaletteTest`; this checks the
 * selection itself — that `SYSTEM` is the one palette that follows the launcher, and that the three
 * are genuinely different objects. A `forTheme` that returned `DEFAULT` for all three would satisfy
 * every resource test in the module while silently making the option a no-op, which is exactly the
 * kind of failure the option exists to prevent.
 *
 * Distinctness is by reference: it is the cheapest property that distinguishes the values, and the
 * palettes are immutable vals so nothing can make two of them equal-but-separate later.
 */
class WidgetColorsThemeTest {

    @Test
    fun systemResolvesToTheDefaultPalette() {
        assertSame(
            "SYSTEM must be the configuration-following palette, or night-mode switching stops " +
                "working for every widget stored without an explicit theme",
            WidgetColors.DEFAULT,
            WidgetColors.forTheme(WidgetTheme.SYSTEM),
        )
    }

    @Test
    fun theForcedPalettesAreDistinctFromEachOtherAndFromDefault() {
        val light = WidgetColors.forTheme(WidgetTheme.LIGHT)
        val dark = WidgetColors.forTheme(WidgetTheme.DARK)
        assertNotSame("LIGHT and DARK must not share a palette", light, dark)
        assertNotSame("LIGHT must not be the system palette", light, WidgetColors.DEFAULT)
        assertNotSame("DARK must not be the system palette", dark, WidgetColors.DEFAULT)
    }

    @Test
    fun selectionIsStableAcrossCalls() {
        // A `when` returning a fresh value per call would pass every test above and still be wrong:
        // two renders of the same widget would compare unequal and the cache's keying assumptions
        // (which hold options, not palettes) would not notice.
        assertSame(
            WidgetColors.forTheme(WidgetTheme.DARK),
            WidgetColors.forTheme(WidgetTheme.DARK),
        )
    }
}
