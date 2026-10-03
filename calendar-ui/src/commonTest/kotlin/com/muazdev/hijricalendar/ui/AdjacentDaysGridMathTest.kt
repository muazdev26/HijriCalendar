package com.muazdev.hijricalendar.ui

import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import com.muazdev.hijricalendar.core.CalendarMonth
import com.muazdev.hijricalendar.core.HijriCalendarState
import com.muazdev.hijricalendar.core.toCalendarMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * FD-02, on the `calendar-ui` side: how many rows the grid draws, and what it does to a
 * neighbouring month's day.
 *
 * The arithmetic here is a **second copy** of `HijriMonthWidgetData.weeksToRender` in
 * `calendar-widget-data`, because `calendar-ui` cannot depend on the widget module. Two copies is
 * the cost of that layering, and this file is the only thing stopping them from quietly diverging:
 * it asserts the same row counts, over the same range of months, that `AdjacentDaysTest` asserts
 * for the widget side.
 *
 * What each test is actually protecting:
 *
 * - `gridWeeks` — the **row count**. A filter that dropped cells without trimming rows would keep
 *   the sixth row as dead space and pass every "are the right days shown" check.
 * - [HijriCalendarDayCell]'s `visible` — the **column alignment**. A month can begin mid-week, so
 *   blanking the leading cells wrongly (by removing them) slides the 1st sideways.
 */
class AdjacentDaysGridMathTest {

    /**
     * The row count is the number of weeks the month spans, not a constant six.
     *
     * A Hijri month is 29 or 30 days, so once the neighbours are out of the way most months need
     * five rows. Before this, every month took six and a quarter of every grid was padding.
     */
    @Test
    fun hidingAdjacentDaysCollapsesTheGridToTheWeeksTheMonthSpans() {
        var sawFiveRowMonth = false
        var sawSixRowMonth = false

        months().forEach { (year, month) ->
            val days = HijrahYearMonth(year, month).toCalendarMonth().days
            val length = days.count { it.isCurrentMonth }
            val leadingCells = CalendarMonth.DAYS_IN_WEEK -
                days.take(CalendarMonth.DAYS_IN_WEEK).count { it.isCurrentMonth }
            val expectedRows = (leadingCells + length + 6) / 7

            val weeks = gridWeeks(days, showAdjacentDays = false)

            assertEquals(
                expectedRows,
                weeks.size,
                "$year-$month starts $leadingCells columns in and runs $length days, so it spans " +
                    "$expectedRows weeks",
            )
            assertEquals(
                length,
                weeks.sumOf { week -> week.count { it.isCurrentMonth } },
                "$year-$month must keep every one of its own days",
            )
            weeks.forEach { week ->
                assertEquals(
                    CalendarMonth.DAYS_IN_WEEK,
                    week.size,
                    "$year-$month produced a partial week",
                )
            }
            when {
                expectedRows < CalendarMonth.WEEKS_IN_MONTH -> sawFiveRowMonth = true
                expectedRows == CalendarMonth.WEEKS_IN_MONTH -> sawSixRowMonth = true
            }
        }

        assertTrue(
            sawFiveRowMonth,
            "no month needed fewer than six rows, so the row arithmetic is not doing anything",
        )
        assertTrue(
            sawSixRowMonth,
            "no month needed six rows either, which means the row count is not a function of the " +
                "month at all",
        )
    }

    /** With the neighbours shown, every month is the full padded grid — the "unchanged" half. */
    @Test
    fun showingAdjacentDaysKeepsTheFullPaddedGrid() {
        months().forEach { (year, month) ->
            val days = HijrahYearMonth(year, month).toCalendarMonth().days
            assertEquals(
                CalendarMonth.WEEKS_IN_MONTH,
                gridWeeks(days, showAdjacentDays = true).size,
                "$year-$month",
            )
            assertTrue(
                days.any { !it.isCurrentMonth },
                "$year-$month has no out-of-month days, so this assertion is vacuous",
            )
        }
    }

    /**
     * The 1st does not move.
     *
     * The regression this file exists for: dropping the leading cells instead of blanking them slides
     * the 1st into column zero and puts every day of the month under the wrong weekday heading. A
     * grid that still looks like a calendar, systematically off by `leadingCells` columns.
     */
    @Test
    fun theFirstOfTheMonthStaysInItsOwnWeekdayColumn() {
        var sawPaddedFirstWeek = false

        months().forEach { (year, month) ->
            val days = HijrahYearMonth(year, month).toCalendarMonth().days
            val shown = gridWeeks(days, showAdjacentDays = true)
            val hidden = gridWeeks(days, showAdjacentDays = false)

            val columnShown = shown[0].indexOfFirst { it.isCurrentMonth }
            val columnHidden = hidden[0].indexOfFirst { it.isCurrentMonth }

            assertEquals(
                columnShown,
                columnHidden,
                "$year-$month: hiding the neighbours moved the 1st out of its weekday column",
            )
            if (columnShown > 0) sawPaddedFirstWeek = true
        }

        assertTrue(
            sawPaddedFirstWeek,
            "no month began with padding before the 1st, so the alignment case was never exercised",
        )
    }

    /** Every rendered row contains at least one of the month's own days. */
    @Test
    fun noRenderedRowIsEntirelyBlank() {
        months().forEach { (year, month) ->
            val days = HijrahYearMonth(year, month).toCalendarMonth().days
            assertTrue(
                gridWeeks(days, showAdjacentDays = false).all { week ->
                    week.any { it.isCurrentMonth }
                },
                "$year-$month rendered a whole blank row",
            )
        }
    }

    /**
     * The default is off, and it is a plain `val` a caller sets once.
     *
     * Asserted through the state because that is where the flag lives: the grid reads
     * `state.showAdjacentDays`, so a state built without naming it must render the collapsed grid.
     */
    @Test
    fun adjacentDaysAreHiddenByDefaultOnTheState() {
        assertFalse(HijriCalendarState(HijrahYearMonth(1447, 9)).showAdjacentDays)
        assertFalse(
            HijriCalendarState(HijrahYearMonth(1447, 9), showAdjacentDays = false).showAdjacentDays,
        )
    }

    /**
     * Toggling at runtime does not disturb the selection or the month.
     *
     * The flag is presentational and is stored as snapshot state precisely so a host can offer it as
     * a settings row; this is the test that says it does not accidentally do anything else. Note
     * `selectDate`'s navigation is untouched: selecting a date in another month still moves the
     * grid there even though the neighbours are hidden.
     */
    @Test
    fun togglingTheFlagLeavesTheMonthAndSelectionAlone() {
        val state = HijriCalendarState(
            initialMonth = HijrahYearMonth(1447, 9),
            initialSelectedDate = HijrahDate1447_9_5(),
        )
        val month = state.currentMonth
        val selected = state.selectedDate

        state.setShowAdjacentDays(true)
        assertTrue(state.showAdjacentDays)
        state.setShowAdjacentDays(false)
        assertFalse(state.showAdjacentDays)

        assertEquals(month, state.currentMonth, "toggling must not move the grid")
        assertEquals(selected, state.selectedDate, "toggling must not clear the selection")
    }

    /**
     * A programmatic selection outside the visible month still navigates.
     *
     * The distinction the flag's KDoc insists on: hidden days are not disabled days, and guarding
     * `selectDate` against the flag would break a host that selects a date programmatically — the
     * whole reason the flag is rendering-only.
     */
    @Test
    fun selectingADateInAnotherMonthStillMovesTheGridWhenNeighboursAreHidden() {
        val state = HijriCalendarState(
            initialMonth = HijrahYearMonth(1447, 9),
            showAdjacentDays = false,
        )

        state.selectDate(HijrahDate1447_11_1())

        assertEquals(
            HijrahYearMonth(1447, 11),
            state.currentMonth,
            "a programmatic selection must navigate even when the neighbouring days are hidden",
        )
    }

    private fun HijrahDate1447_9_5() =
        com.abdulrahman_b.hijrahdatetime.HijrahDate(1447, 9, 5)

    private fun HijrahDate1447_11_1() =
        com.abdulrahman_b.hijrahdatetime.HijrahDate(1447, 11, 1)

    private fun months(): List<Pair<Int, Int>> =
        (1440..1450).flatMap { year -> (1..12).map { month -> year to month } }
}
