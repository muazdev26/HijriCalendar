package com.muazdev.hijricalendar.widget.glance

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What each tone draws *behind* its figures.
 *
 * The rule the user asked for, in one place: **today is a ring, the chosen day is a block, and the two
 * are never the same mark.** That is the in-app calendar's split between `showTodayBorder` and
 * `isSelected`, and it is the behaviour the widget mirrors — so it is asserted rather than left to a
 * composable that a JVM test cannot call.
 *
 * The interesting assertions are the two that could silently regress: TODAY must not be a block (a
 * second filled cell reads as a second selection), and SELECTED must not be a ring (the chosen day is
 * the stronger statement).
 */
class CellDecorationTest {

    @Test
    fun theChosenDayIsABlock() {
        assertEquals(
            "the chosen day draws the accent block — today is this until the user taps another day",
            CellDecoration.SELECTION_BLOCK,
            cellDecoration(DayCellTone.SELECTED),
        )
    }

    @Test
    fun todayIsARingNotABlock() {
        // The whole point: once the block has moved to the tapped day, today keeps a ring rather than
        // competing with it as a second filled cell.
        assertEquals(
            "today draws the accent ring, not the block — a second filled cell would read as a second " +
                "'chosen' day",
            CellDecoration.TODAY_RING,
            cellDecoration(DayCellTone.TODAY),
        )
    }

    @Test
    fun anObservanceIsAWash() {
        assertEquals(
            "an observance draws its container wash, below the selection block",
            CellDecoration.EVENT_WASH,
            cellDecoration(DayCellTone.EVENT),
        )
    }

    @Test
    fun anOrdinaryCellIsBare() {
        // Nothing behind the figures: a dimmed neighbour, a weekend day and a plain day are all
        // distinguished by their figure colours alone.
        listOf(
            DayCellTone.OUT_OF_MONTH to "a neighbouring month's day",
            DayCellTone.WEEKEND to "a weekend day",
            DayCellTone.REGULAR to "an ordinary day",
        ).forEach { (tone, what) ->
            assertEquals("$what draws nothing behind its figures", CellDecoration.NONE, cellDecoration(tone))
        }
    }

    @Test
    fun todayAndTheChosenDayAreNeverTheSameMark() {
        // The invariant the ring exists for: whichever of the two a cell is, it is exactly one of them.
        val today = cellDecoration(DayCellTone.TODAY)
        val chosen = cellDecoration(DayCellTone.SELECTED)
        org.junit.Assert.assertNotEquals(
            "today and the chosen day must draw different marks — the ring is what keeps today " +
                "findable once the block has moved",
            today,
            chosen,
        )
    }
}
