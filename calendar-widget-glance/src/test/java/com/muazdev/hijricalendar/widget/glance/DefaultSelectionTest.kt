package com.muazdev.hijricalendar.widget.glance

import com.muazdev.hijricalendar.widgetdata.HijriDayWidgetData
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Today is the **default selection** on the grid widget.
 *
 * The block used to appear only after a tap, so a freshly placed widget marked today with nothing but
 * an accent figure — and a widget whose selection had been navigated away from marked nothing at all.
 * [isSelectedCell] instead falls back to today whenever the widget has no block of its own to draw, so
 * an untouched widget answers "what is today?" the same way a tap answers "which day did I choose?".
 *
 * The rule lives in a function, assertable here, for the same reason [dayCellTone] does: the composable
 * that reads it cannot be called from a JVM unit test, and a test that re-derived the rule would only
 * be testing itself.
 */
class DefaultSelectionTest {

    private companion object {
        const val YEAR = 1448
        const val MONTH = 1
        const val TODAY_EPOCH_DAY = 20_000L
    }

    /** Today, in the month the widget is showing. */
    private val today = HijriDayWidgetData(
        hijriDay = 19,
        dayText = "19",
        gregorianDay = 1,
        gregorianDayText = "1",
        gregorianEpochDay = TODAY_EPOCH_DAY,
        isCurrentMonth = true,
        isWeekend = false,
    )

    /** An ordinary in-month day, two weeks before today. */
    private val ordinaryDay = today.copy(
        hijriDay = 5,
        dayText = "5",
        gregorianDay = 18,
        gregorianDayText = "18",
        gregorianEpochDay = TODAY_EPOCH_DAY - 14,
    )

    private fun selectedCell(
        cell: HijriDayWidgetData,
        selectedDay: HijriDaySelection?,
        monthYear: Int = YEAR,
        monthNumber: Int = MONTH,
    ) = isSelectedCell(
        cell = cell,
        monthYear = monthYear,
        monthNumber = monthNumber,
        todayEpochDay = TODAY_EPOCH_DAY,
        selectedDay = selectedDay,
    )

    @Test
    fun anUntouchedWidgetPutsTheBlockOnTodayAndNothingElse() {
        assertTrue(
            "today's cell should carry the selection block while the widget has no selection of its own",
            selectedCell(today, selectedDay = null),
        )
        assertFalse(
            "only today's cell should be filled — a second filled cell would be a second 'today'",
            selectedCell(ordinaryDay, selectedDay = null),
        )
    }

    @Test
    fun aTapMovesTheBlockOffTodayOntoTheTappedDay() {
        val selection = HijriDaySelection(year = YEAR, month = MONTH, day = ordinaryDay.hijriDay)

        assertTrue("the tapped day should carry the block", selectedCell(ordinaryDay, selection))
        assertFalse(
            "today should fall back to its accent figure once another day is chosen",
            selectedCell(today, selection),
        )
    }

    @Test
    fun aSelectionInAMonthTheWidgetIsNotShowingLeavesTodayAsTheDefault() {
        // Navigating to another month must not leave the grid unmarked: the widget has no block of its
        // own to draw *here*, so today's cell takes it back.
        val elsewhere = HijriDaySelection(year = YEAR, month = MONTH + 1, day = 5)

        assertTrue(
            "today's cell is the default again in a month the stored selection does not belong to",
            selectedCell(today, elsewhere),
        )
        assertFalse(
            "the stored selection's day number must not mark a cell in this month",
            selectedCell(ordinaryDay, elsewhere),
        )
    }

    @Test
    fun aNeighbouringMonthsCellIsNeverTheChosenDay() {
        // Matched by day number alone, a selection from another month would mark the same-numbered cell
        // of a neighbouring month shown in the leading or trailing week.
        val selection = HijriDaySelection(year = YEAR, month = MONTH, day = 5)

        assertFalse(
            "a neighbouring month's cell must never take the block for a same-numbered selection",
            selectedCell(ordinaryDay.copy(isCurrentMonth = false), selection),
        )
    }

    @Test
    fun aNeighbouringMonthsCellIsNotTodaysDefaultEither() {
        // The same guard applies to the default: today's own date shown as a dimmed neighbouring day
        // keeps its dimming rather than gaining a full accent block.
        assertFalse(
            "today's date as a neighbouring month's cell should stay dimmed, not filled",
            selectedCell(today.copy(isCurrentMonth = false), selectedDay = null),
        )
    }
}
