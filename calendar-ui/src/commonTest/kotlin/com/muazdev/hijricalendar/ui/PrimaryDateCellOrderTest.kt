package com.muazdev.hijricalendar.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `PrimaryDate` decides which of the two dates is the top line under `BOTH`.
 *
 * The grid's two-line cell is the only place the option can be observed, and it is a composable
 * painting 42 nodes — so the decision is a pure function and this is its test. The failure this
 * guards against is subtle rather than loud: a reversed `if` still paints both dates in a 48dp cell,
 * and a reader who does not already know which line they asked for has nothing to compare against.
 */
class PrimaryDateCellOrderTest {

    private val hijri = "10"
    private val gregorian = "27"

    @Test
    fun hijriPrimary_putsTheHijriDateOnTop() {
        assertEquals(
            hijri to gregorian,
            cellDateLines(PrimaryDate.HIJRI, hijriText = hijri, gregorianText = gregorian),
            "the historical order: Hijri figure, then the Gregorian digit beneath it",
        )
    }

    @Test
    fun gregorianPrimary_swapsTheTwoLines() {
        assertEquals(
            gregorian to hijri,
            cellDateLines(PrimaryDate.GREGORIAN, hijriText = hijri, gregorianText = gregorian),
            "the option must actually move the Gregorian date to the primary line",
        )
    }

    @Test
    fun neitherLineIsDroppedEitherWay() {
        for (primary in PrimaryDate.entries) {
            val lines = cellDateLines(primary, hijriText = hijri, gregorianText = gregorian)
            assertEquals(
                setOf(hijri, gregorian),
                setOf(lines.first, lines.second),
                "$primary must not lose one of the two dates",
            )
        }
    }
}
