package com.muazdev.hijricalendar.widget.glance

import com.muazdev.hijricalendar.widgetdata.HijriDayWidgetData
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The redesigned day cell's tone precedence, as one testable rule.
 *
 * Before the redesign a cell's appearance was decided by a scatter of booleans at the call site — a
 * fill for `isToday || isEvent`, a corner badge for the selection, and a figure colour chosen from a
 * separate `when` against the same inputs. That is how "the big number belongs to the wrong calendar"
 * and "today's mark hides a selection" happen: the fill, the figure and the sub-figure can each read a
 * different flag.
 *
 * [dayCellTone] collapses those into one ordered `when`, and this file pins the *order*, which is the
 * design and not an implementation detail:
 *
 *  selected > today > out-of-month > observance > weekend > regular
 *
 * The rule lives in a function rather than in `DayCell` because `DayCell` is a `@Composable` and cannot
 * be called from a JVM unit test — the same reason [DayCellFiguresTest] and [GridCellWeightsTest] exist.
 */
class DayCellToneTest {

    /** A plain in-month, non-weekend, observance-free cell. `copy` is used to vary one thing at a time. */
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
    fun aPlainInMonthDayIsRegular() {
        assertEquals(DayCellTone.REGULAR, dayCellTone(cell, isToday = false, isSelected = false))
    }

    @Test
    fun todayIsItsOwnToneRatherThanAFill() {
        // The redesign made today an accent *figure*, not a filled block, so it is a tone of its own
        // rather than sharing the fill the selection uses. Today's cell only reaches here once another
        // day is the selection — with nothing selected, today *is* the selection (`isSelectedCell`).
        assertEquals(DayCellTone.TODAY, dayCellTone(cell, isToday = true, isSelected = false))
    }

    @Test
    fun aSelectionOutranksTodaySoATappedTodayShowsOnlyTheSelection() {
        // Exactly the in-app calendar's behaviour: tapping today hides today's mark, because the filled
        // block is the stronger statement. If today won here, a selected-today cell would show neither
        // clearly.
        assertEquals(DayCellTone.SELECTED, dayCellTone(cell, isToday = true, isSelected = true))
    }

    @Test
    fun aSelectionOutranksAnObservance() {
        // A selected Eid must read as "you chose this", not as two competing marks in one cell.
        assertEquals(
            DayCellTone.SELECTED,
            dayCellTone(cell.copy(hasEvent = true), isToday = false, isSelected = true),
        )
    }

    @Test
    fun aSelectionOutranksAnOutOfMonthCellThatShouldNotBeSelectable() {
        // Belt and braces: the renderer only selects an in-month cell, but the tone must not depend on
        // that having been got right — the selected branch is unconditional.
        assertEquals(
            DayCellTone.SELECTED,
            dayCellTone(cell.copy(isCurrentMonth = false), isToday = false, isSelected = true),
        )
    }

    @Test
    fun todayOutranksOutOfMonthAndObservance() {
        // Today is drawn on whichever month is on screen, including a neighbouring month's cell that
        // happens to land on it — so today must win over both the dimming and the wash.
        assertEquals(
            DayCellTone.TODAY,
            dayCellTone(cell.copy(isCurrentMonth = false, hasEvent = true), isToday = true, isSelected = false),
        )
    }

    @Test
    fun anOutOfMonthCellIsDimmedEvenWhenItCarriesAnObservance() {
        // A neighbouring month's Eid is not washed as the current month's would be: it is not the month
        // the user is looking at, and washing 42 cells' worth of observances would make the fill noise.
        assertEquals(
            DayCellTone.OUT_OF_MONTH,
            dayCellTone(cell.copy(isCurrentMonth = false, hasEvent = true), isToday = false, isSelected = false),
        )
    }

    @Test
    fun anObservanceOutranksAWeekend() {
        // An event landing on a Friday must still read as an event, not be hidden behind the weekend
        // colour.
        assertEquals(
            DayCellTone.EVENT,
            dayCellTone(cell.copy(hasEvent = true, isWeekend = true), isToday = false, isSelected = false),
        )
    }

    @Test
    fun aWeekendWithNoObservanceIsAWeekend() {
        assertEquals(
            DayCellTone.WEEKEND,
            dayCellTone(cell.copy(isWeekend = true), isToday = false, isSelected = false),
        )
    }

    /**
     * The two strongest tones are reachable *only* from their own flag.
     *
     * Not a restatement of the `when` — it is the invariant a wrong precedence breaks. Put `today`
     * above `selected` and a tapped-today cell stops producing [DayCellTone.SELECTED]; put
     * `out-of-month` above `today` and today's own cell stops producing [DayCellTone.TODAY]. Sweeping
     * every flag combination states both without re-implementing the branch order.
     */
    @Test
    fun selectedAndTodayAreEachReachableOnlyFromTheirOwnFlag() {
        // The three cell-shape flags, all eight combinations, listed rather than nested so the test
        // body stays two loops deep.
        val shapes = listOf(
            Triple(true, false, false),
            Triple(true, true, false),
            Triple(true, false, true),
            Triple(true, true, true),
            Triple(false, false, false),
            Triple(false, true, false),
            Triple(false, false, true),
            Triple(false, true, true),
        )
        val todayAndSelected = listOf(true to true, true to false, false to true, false to false)

        for ((inMonth, weekend, event) in shapes) {
            for ((isToday, isSelected) in todayAndSelected) {
                val tone = dayCellTone(
                    cell.copy(
                        isCurrentMonth = inMonth,
                        isWeekend = weekend,
                        hasEvent = event,
                    ),
                    isToday = isToday,
                    isSelected = isSelected,
                )
                val context = "currentMonth=$inMonth weekend=$weekend " +
                    "event=$event today=$isToday selected=$isSelected"
                assertEquals(
                    "SELECTED must track isSelected exactly ($context)",
                    isSelected,
                    tone == DayCellTone.SELECTED,
                )
                if (!isSelected) {
                    assertEquals(
                        "TODAY must track isToday when nothing is selected ($context)",
                        isToday,
                        tone == DayCellTone.TODAY,
                    )
                }
            }
        }
    }
}
