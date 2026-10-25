package com.muazdev.hijricalendar.widget.glance

import com.muazdev.hijricalendar.widgetdata.HijriDayWidgetData
import com.muazdev.hijricalendar.widgetdata.WidgetDateDisplayMode
import com.muazdev.hijricalendar.widgetdata.WidgetDatePrimary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `WidgetOptions.dateDisplayMode`'s three modes, as they land in a cell.
 *
 * The rule lives in [dayCellFigures] rather than in the composition because `DayFigures` is a
 * `@Composable` and cannot be called from a JVM unit test, and a test restating the rule would only
 * be testing itself. These are the assertions that the mode is read at all: every release before the
 * option existed drew two figures in every cell, so a mode that silently did nothing would still
 * render a plausible-looking grid.
 */
class DayCellFiguresTest {

    private val cell = HijriDayWidgetData(
        hijriDay = 13,
        dayText = "13",
        gregorianDay = 30,
        gregorianDayText = "30",
        gregorianEpochDay = 20_000L,
        isCurrentMonth = true,
        isWeekend = false,
    )

    @Test
    fun bothDrawsTheHijriFigureOverTheGregorianOne() {
        val figures = dayCellFigures(cell, WidgetDateDisplayMode.BOTH)
        assertEquals("13", figures.main)
        assertEquals("30", figures.sub)
    }

    @Test
    fun hijriOnlyDropsTheGregorianLine() {
        val figures = dayCellFigures(cell, WidgetDateDisplayMode.HIJRI_ONLY)
        assertEquals("13", figures.main)
        assertNull(figures.sub)
    }

    @Test
    fun gregorianOnlyPromotesTheGregorianFigureRatherThanSubordinatingIt() {
        // The whole point of the mode: the Gregorian day becomes the cell's answer, not a small grey
        // digit left over from hiding the Hijri one. The size and colour follow from being `main`.
        val figures = dayCellFigures(cell, WidgetDateDisplayMode.GREGORIAN_ONLY)
        assertEquals("30", figures.main)
        assertNull(figures.sub)
    }

    @Test
    fun bothWithGregorianPrimarySwapsTheTwoFigures() {
        // `datePrimary` moves the *hero position*, so the two strings exchange places rather than the
        // Gregorian day becoming the small line and the Hijri day staying where it was.
        val figures = dayCellFigures(
            cell,
            WidgetDateDisplayMode.BOTH,
            WidgetDatePrimary.GREGORIAN,
        )
        assertEquals("30", figures.main)
        assertEquals("13", figures.sub)
    }

    @Test
    fun aSingleFigureModeIgnoresThePrimary() {
        // The mode has already answered "which date is primary", so a stored GREGORIAN primary must
        // not resurrect a second line in HIJRI_ONLY or turn GREGORIAN_ONLY's hero back into the Hijri
        // day. A renderer that read the field there would make `dateDisplayMode` behave differently
        // depending on a setting the user cannot see in that mode.
        for (mode in listOf(WidgetDateDisplayMode.HIJRI_ONLY, WidgetDateDisplayMode.GREGORIAN_ONLY)) {
            assertEquals(
                dayCellFigures(cell, mode, WidgetDatePrimary.HIJRI),
                dayCellFigures(cell, mode, WidgetDatePrimary.GREGORIAN),
            )
        }
    }

    @Test
    fun theModeIsReadFromTheCellSoEveryCellAgrees() {
        // A grid is 42 cells rendered by one composition, so a per-cell divergence is impossible by
        // construction — but the *projection* is what supplies the strings, so the assertion worth
        // having is that out-of-month cells resolve the same way.
        val adjacent = cell.copy(isCurrentMonth = false, dayText = "29", gregorianDayText = "28")
        assertEquals(
            DayCellFigures(main = "29", sub = "28"),
            dayCellFigures(adjacent, WidgetDateDisplayMode.BOTH),
        )
    }
}
