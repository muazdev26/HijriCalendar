package com.muazdev.hijricalendar.widgetdata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * FD-04: the grid cell's two text sizes, and the one number that must not move.
 *
 * The ticket was "the date is hard to read — it has padding but the text size is small as per the
 * cell". The cause was a pair of fixed sizes: **13sp** for the Hijri figure and **8sp** for the
 * Gregorian day underneath, at every widget size. So resizing the widget larger bought more padding
 * around the same 8sp glyph.
 *
 * The Gregorian line is the important half of this file. It was 8sp — below the legibility floor for a
 * secondary line — and Glance `Text` **clips rather than reflows**, so shrinking it further on a small
 * widget would cost the bottom line entirely: a clipped `14` reads as `1`, which is a *wrong date*
 * rather than a missing one. Hence a floor rather than a ratio, and hence the fit assertion.
 */
class GridTypographyTest {

    @Test
    fun theHijriFigureGrowsWithTheCell() {
        val small = GridTypography.hijriSizeSp(GridTypography.MINIMUM_CELL_SIZE_DP)
        val large = GridTypography.hijriSizeSp(80f)

        assertTrue(
            small < large,
            "a bigger cell must get a bigger figure; got $small at the minimum and $large at 80dp",
        )
        assertTrue(
            small >= GridTypography.HIJRI_FLOOR_SP,
            "the figure must never fall below its floor, which is the whole point of scaling it up",
        )
        assertTrue(
            large <= GridTypography.HIJRI_CEILING_SP,
            "and never above its ceiling — the month row has to fit underneath it",
        )
    }

    @Test
    fun neitherSizeEverShrinksAsTheCellGrows() {
        var previousHijri = 0f
        var previousGregorian = 0f
        for (cell in 20..120) {
            val hijri = GridTypography.hijriSizeSp(cell.toFloat())
            val gregorian = GridTypography.gregorianSizeSp(cell.toFloat())
            assertTrue(
                hijri >= previousHijri,
                "at ${cell}dp the Hijri figure dropped to $hijri from $previousHijri",
            )
            assertTrue(
                gregorian >= previousGregorian,
                "at ${cell}dp the Gregorian figure dropped to $gregorian from $previousGregorian",
            )
            previousHijri = hijri
            previousGregorian = gregorian
        }
    }

    /**
     * The Gregorian line never renders below 9sp — the value the fixed 8sp failed to be.
     *
     * Asserted across the whole cell range rather than at one size, because the failure mode being
     * guarded against is a *proportional* rule taking it lower on small widgets. That is the direction
     * in which it was already illegible.
     */
    @Test
    fun theGregorianFigureIsNeverBelowItsFloor() {
        for (cell in 1..200) {
            val size = GridTypography.gregorianSizeSp(cell.toFloat())
            assertTrue(
                size >= GridTypography.GREGORIAN_FLOOR_SP,
                "at ${cell}dp the Gregorian figure fell to ${size}sp, below the " +
                    "${GridTypography.GREGORIAN_FLOOR_SP}sp floor — 8sp is what this ticket is about",
            )
            assertTrue(
                size <= GridTypography.GREGORIAN_CEILING_SP,
                "at ${cell}dp the Gregorian figure passed its ceiling",
            )
        }
    }

    /**
     * Both lines fit the smallest supported cell.
     *
     * The assertion that matters most, and the reason [GridTypography.fitsInCellHeight] exists. Glance
     * clips the bottom line when a cell runs out of room, and a clipped Gregorian digit is a wrong
     * date. If this fails at any size the numbers must come down, not the fit be assumed.
     */
    @Test
    fun bothLinesFitTheSmallestSupportedCell() {
        val smallest = GridTypography.MINIMUM_CELL_SIZE_DP
        val lineSp = GridTypography.hijriSizeSp(smallest) + GridTypography.gregorianSizeSp(smallest)
        val occupied = lineSp * GridTypography.LINE_HEIGHT_RATIO

        assertTrue(
            occupied <= smallest,
            "the two lines need ~${occupied.toInt()}dp of a ${smallest.toInt()}dp cell, which would " +
                "clip the Gregorian day — a clipped 14 reads as 1, which is a wrong date",
        )
        assertTrue(
            GridTypography.fitsInCellHeight(smallest),
            "the rule should agree with itself about whether it fits",
        )
    }

    /** And every cell between the minimum and a large grid fits too. */
    @Test
    fun bothLinesFitAcrossTheWholeCellRange() {
        for (cell in GridTypography.MINIMUM_CELL_SIZE_DP.toInt()..90) {
            assertTrue(
                GridTypography.fitsInCellHeight(cell.toFloat()),
                "the two lines do not fit a ${cell}dp cell",
            )
        }
    }

    /**
     * The sizes are calibrated at the reference cell.
     *
     * Pins the constant so a change to it is deliberate: the calibration is what makes the numbers
     * *larger* than the 13sp/8sp they replace at a typical widget size, and silently moving the
     * reference would undo that without any test noticing.
     */
    @Test
    fun theReferenceCellProducesTheExpectedNumbers() {
        assertTrue(
            GridTypography.hijriSizeSp(GridTypography.REFERENCE_CELL_SIZE_DP) == 13f,
            "the Hijri figure at the reference cell should be 13sp — the size it replaces",
        )
        assertTrue(
            GridTypography.gregorianSizeSp(GridTypography.REFERENCE_CELL_SIZE_DP) == 9f,
            "and the Gregorian figure 9sp — one above the 8sp it replaces",
        )
    }

    /**
     * A widget the user resized larger genuinely reads better.
     *
     * The user-visible claim, stated as an assertion so it cannot quietly stop being true: a small
     * widget gets bigger text than the fixed sizes it used to have, and a large one gets more again.
     */
    @Test
    fun aResizedWidgetGivesLargerTextThanItUsedTo() {
        val compact = GridTypography.hijriSizeSp(26f)
        val roomy = GridTypography.hijriSizeSp(56f)

        assertTrue(compact >= 13f, "a 26dp cell should beat the old fixed 13sp, got $compact")
        assertTrue(
            roomy > 14f,
            "a 56dp cell should be comfortably larger than the old fixed 13sp, got $roomy",
        )
        assertTrue(
            roomy > compact,
            "and resizing the widget must actually buy something: $roomy is not larger than $compact",
        )
    }

    /**
     * The divider option is on by default, everywhere.
     *
     * Purely presentational and additive, but the default is a deliberate product choice: the grid is
     * easier to scan with a hairline between cells. Asserted on all three default carriers so one of
     * them cannot drift.
     */
    @Test
    fun theCellBordersDefaultToOnEverywhere() {
        assertTrue(WidgetOptions().showCellBorders, "the data-class default must be true")
        assertTrue(
            WidgetOptions.DEFAULTS.showCellBorders,
            "DEFAULTS is what a fresh install and the family mirror resolve to",
        )
        assertTrue(
            createWidgetOptions().showCellBorders,
            "the native factory must agree too",
        )
    }

    /** And it round-trips, so a widget that has been told to show dividers remembers. */
    @Test
    fun theBordersFlagRoundTripsThroughTheCodec() {
        for (show in listOf(true, false)) {
            val text = WidgetOptionsJson.encode(createWidgetOptions(showCellBorders = show))
            assertTrue(
                "\"showCellBorders\":$show" in text,
                "encodeDefaults must write the flag explicitly",
            )
            assertEquals(show, assertNotNull(WidgetOptionsJson.decodeOrNull(text)).showCellBorders)
        }
    }

    /** A widget stored before the field existed decodes with it on, the current default. */
    @Test
    fun aPreFieldBlobDecodesWithBordersOn() {
        val legacy = """
            {"adjustmentDays":0,"numeralStyle":"WESTERN","weekStart":"MONDAY","pinnedYear":null,
             "pinnedMonth":null,"source":"CALCULATION","language":"URDU","monthNameLanguage":"URDU",
             "monthLengthOverrides":{},"showAdjacentDays":true}
        """.trimIndent()

        val decoded = assertNotNull(WidgetOptionsJson.decodeOrNull(legacy), "the legacy blob must decode")
        assertTrue(decoded.showCellBorders)
        assertTrue(decoded.showAdjacentDays, "an earlier ticket's field must survive too")
    }
}
