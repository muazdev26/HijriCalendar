package com.muazdev.hijricalendar.core

import com.abdulrahman_b.hijrahdatetime.toHijrahDate
import com.abdulrahman_b.hijrahdatetime.toLocalDate
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HijriCalendarStateTest {

    private fun todayUnadjustedHijri(): HijrahDateLike {
        val now = kotlin.time.Clock.System.now()
        val localDate = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
        return HijrahDateLike(localDate)
    }

    /** Small wrapper so tests can assert on both spaces without re-computing conversions. */
    private data class HijrahDateLike(val unadjustedGregorian: LocalDate) {
        val hijri = unadjustedGregorian.toHijrahDate()
    }

    @Test
    fun initialSelectedDateIsNormalizedIntoAdjustedSpace() {
        val today = todayUnadjustedHijri()

        val state = HijriCalendarState(
            initialMonth = HijrahYearMonth(today.hijri.year, today.hijri.month),
            initialSelectedDate = today.hijri,
            adjustmentDays = 1,
        )

        val selected = assertNotNull(state.selectedDate)
        assertEquals(
            today.unadjustedGregorian.plus(1, DateTimeUnit.DAY),
            selected.toLocalDate(),
            "selection must live in adjusted space (+1 Gregorian day)",
        )
    }

    @Test
    fun selectedCellAndTodayCellRepresentTheSameRealWorldDay() {
        val today = todayUnadjustedHijri()

        val state = HijriCalendarState(
            initialMonth = HijrahYearMonth(today.hijri.year, today.hijri.month),
            initialSelectedDate = today.hijri,
            adjustmentDays = 1,
        )

        val month = state.calendarMonth
        val selectedDay = month.days.first { it.isSelected }
        val todayCell = month.days.first { it.isToday }

        assertEquals(
            todayCell.localDate,
            selectedDay.localDate,
            "with an unadjusted initial selection, the filled circle must land on the same " +
                "real-world Gregorian day as the isToday ring",
        )
    }

    @Test
    fun zeroAdjustmentKeepsInitialSelectionUnchanged() {
        val today = todayUnadjustedHijri()

        val state = HijriCalendarState(
            initialMonth = HijrahYearMonth(today.hijri.year, today.hijri.month),
            initialSelectedDate = today.hijri,
            adjustmentDays = 0,
        )

        assertEquals(today.hijri, state.selectedDate)
    }

    @Test
    fun negativeAdjustmentShiftsSelectionBackwards() {
        val today = todayUnadjustedHijri()

        val state = HijriCalendarState(
            initialMonth = HijrahYearMonth(today.hijri.year, today.hijri.month),
            initialSelectedDate = today.hijri,
            adjustmentDays = -2,
        )

        val selected = assertNotNull(state.selectedDate)
        assertEquals(
            today.unadjustedGregorian.plus(-2, DateTimeUnit.DAY),
            selected.toLocalDate(),
        )
    }

    @Test
    fun goToTodayAndNormalizedInitialSelectionAgree() {
        val today = todayUnadjustedHijri()
        val month = HijrahYearMonth(today.hijri.year, today.hijri.month)

        val fromConstructor = HijriCalendarState(
            initialMonth = month,
            initialSelectedDate = today.hijri,
            adjustmentDays = 1,
        )
        val fromGoToToday = HijriCalendarState(initialMonth = month, adjustmentDays = 1).apply {
            goToToday()
        }

        assertEquals(fromGoToToday.selectedDate, fromConstructor.selectedDate)
        assertTrue(
            fromConstructor.calendarMonth.days.none { it.isSelected && !it.isToday },
            "no cell may be selected-but-not-today when both refer to today",
        )
    }
}
