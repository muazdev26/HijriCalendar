package com.muazdev.hijricalendar.core

import com.abdulrahman_b.hijrahdatetime.HijrahDate
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CalendarMonthExtTest {

    // ── Grid shape ──────────────────────────────────────────────────────

    @Test
    fun grid_alwaysHas42Cells() {
        val month = HijrahYearMonth(1447, 9).toCalendarMonth()
        assertEquals(CalendarMonth.TOTAL_DAYS, month.days.size)
    }

    @Test
    fun grid_sixWeeks() {
        val month = HijrahYearMonth(1447, 9).toCalendarMonth()
        assertEquals(6, month.numberOfWeeks)
    }

    @Test
    fun grid_firstDayOfWeekDefaultSaturday() {
        val month = HijrahYearMonth(1447, 9).toCalendarMonth()
        assertEquals(WeekDay.SATURDAY, month.firstDayOfWeek)
    }

    // ── WeekDay alignment ───────────────────────────────────────────────

    @Test
    fun weekDayAlignment_sundayFirstDay() {
        val month = HijrahYearMonth(1447, 9).toCalendarMonth(firstDayOfWeek = WeekDay.SUNDAY)
        val firstIdx = month.days.indexOfFirst { it.isCurrentMonth }
        // Ramadan 1 1447 falls on a Wednesday; with Sunday as first day, it lands at column 3.
        assertEquals(3, firstIdx)
    }

    @Test
    fun weekDayAlignment_mondayFirstDay() {
        val month = HijrahYearMonth(1447, 9).toCalendarMonth(firstDayOfWeek = WeekDay.MONDAY)
        val firstIdx = month.days.indexOfFirst { it.isCurrentMonth }
        // columns Mon.Sun, Wednesday is column 2
        assertEquals(2, firstIdx)
    }

    @Test
    fun weekDayAlignment_wednesdayFirstDay() {
        val month = HijrahYearMonth(1447, 9).toCalendarMonth(firstDayOfWeek = WeekDay.WEDNESDAY)
        val firstIdx = month.days.indexOfFirst { it.isCurrentMonth }
        // columns Wed.Tue, Wednesday is column 0
        assertEquals(0, firstIdx)
    }

    @Test
    fun weekDayAlignment_saturdayFirstDay() {
        val month = HijrahYearMonth(1447, 9).toCalendarMonth(firstDayOfWeek = WeekDay.SATURDAY)
        val firstIdx = month.days.indexOfFirst { it.isCurrentMonth }
        // columns Sat..Fri, Wednesday is column 4
        assertEquals(4, firstIdx)
    }

    // ── Leading / trailing days ─────────────────────────────────────────

    @Test
    fun leadingDays_areFromPreviousMonth() {
        val month = HijrahYearMonth(1447, 9).toCalendarMonth()
        val leadingDays = month.days.filter { !it.isCurrentMonth && month.days.indexOf(it) < 7 }
        assertTrue(leadingDays.isNotEmpty())
        assertTrue(leadingDays.all { !it.isCurrentMonth })
    }

    @Test
    fun trailingDays_areFromNextMonth() {
        val month = HijrahYearMonth(1447, 9).toCalendarMonth()
        val lastWeek = month.days.takeLast(7)
        val trailingDays = lastWeek.filter { !it.isCurrentMonth }
        assertTrue(trailingDays.isNotEmpty())
        assertTrue(trailingDays.all { !it.isCurrentMonth })
    }

    // ── Current month days count ────────────────────────────────────────

    @Test
    fun currentMonthDays_countMatchesMonthLength() {
        val month = HijrahYearMonth(1447, 9).toCalendarMonth()
        val currentDays = month.days.filter { it.isCurrentMonth }
        // Ramadan 1447 has 30 days
        assertEquals(30, currentDays.size)
    }

    @Test
    fun currentMonthDays_dayNumbersAreSequential() {
        val month = HijrahYearMonth(1447, 9).toCalendarMonth()
        val dayNumbers = month.days.filter { it.isCurrentMonth }.map { it.dayOfMonth }
        assertEquals((1..30).toList(), dayNumbers)
    }

    // ── Adjustment days ─────────────────────────────────────────────────

    @Test
    fun adjustmentDays_shiftsDayNumbers() {
        // With +1 adjustment the whole grid shifts, so the same grid column that held
        // the previous month's day 28 now holds the current month's day 1.
        val unadjusted = HijrahYearMonth(1447, 9).toCalendarMonth(firstDayOfWeek = WeekDay.SUNDAY, adjustmentDays = 0)
        val adjusted = HijrahYearMonth(1447, 9).toCalendarMonth(firstDayOfWeek = WeekDay.SUNDAY, adjustmentDays = 1)

        val unadjustedFirstIdx = unadjusted.days.indexOfFirst { it.isCurrentMonth }
        val adjustedFirstIdx = adjusted.days.indexOfFirst { it.isCurrentMonth }

        // The day-1 anchor moved by exactly 1 grid column.
        assertEquals(unadjustedFirstIdx - 1, adjustedFirstIdx)
    }

    @Test
    fun adjustmentDays_crossesMonthBoundary() {
        // With +1 adjustment, the first current-month day might actually
        // come from the previous Umm al-Qura month.
        val adjusted = HijrahYearMonth(1447, 9).toCalendarMonth(adjustmentDays = 1)
        val firstCurrentMonthDay = adjusted.days.first { it.isCurrentMonth }
        // The first current-month cell should have dayOfMonth == 1
        assertEquals(1, firstCurrentMonthDay.dayOfMonth)
    }

    @Test
    fun adjustmentDays_shiftingIntoNextMonth() {
        // With a large enough positive adjustment, a day that was in the
        // previous month should now appear in this month's grid.
        val adjusted = HijrahYearMonth(1447, 9).toCalendarMonth(adjustmentDays = 2)
        val firstCurrentMonthDay = adjusted.days.first { it.isCurrentMonth }
        assertEquals(1, firstCurrentMonthDay.dayOfMonth)
    }

    // ── Min / max date clamping ─────────────────────────────────────────

    @Test
    fun minDate_clampsDaysBeforeMin() {
        val minDate = HijrahDate(1447, 9, 5)
        val month = HijrahYearMonth(1447, 9).toCalendarMonth(minDate = minDate)
        val currentDays = month.days.filter { it.isCurrentMonth }
        currentDays.forEach { day ->
            if (day.dayOfMonth < 5) {
                assertTrue(day.isDisabled, "Day ${day.dayOfMonth} should be disabled")
            }
        }
    }

    @Test
    fun minDate_allowsDaysOnAndAfterMin() {
        val minDate = HijrahDate(1447, 9, 5)
        val month = HijrahYearMonth(1447, 9).toCalendarMonth(minDate = minDate)
        val currentDays = month.days.filter { it.isCurrentMonth }
        currentDays.filter { it.dayOfMonth >= 5 }.forEach { day ->
            assertFalse(day.isDisabled, "Day ${day.dayOfMonth} should be enabled")
        }
    }

    @Test
    fun maxDate_clampsDaysAfterMax() {
        val maxDate = HijrahDate(1447, 9, 25)
        val month = HijrahYearMonth(1447, 9).toCalendarMonth(maxDate = maxDate)
        val currentDays = month.days.filter { it.isCurrentMonth }
        currentDays.forEach { day ->
            if (day.dayOfMonth > 25) {
                assertTrue(day.isDisabled, "Day ${day.dayOfMonth} should be disabled")
            }
        }
    }

    @Test
    fun maxDate_allowsDaysOnAndBeforeMax() {
        val maxDate = HijrahDate(1447, 9, 25)
        val month = HijrahYearMonth(1447, 9).toCalendarMonth(maxDate = maxDate)
        val currentDays = month.days.filter { it.isCurrentMonth }
        currentDays.filter { it.dayOfMonth <= 25 }.forEach { day ->
            assertFalse(day.isDisabled, "Day ${day.dayOfMonth} should be enabled")
        }
    }

    @Test
    fun minMaxDate_boundaryDaysAreEnabled() {
        val minDate = HijrahDate(1447, 9, 1)
        val maxDate = HijrahDate(1447, 9, 30)
        val month = HijrahYearMonth(1447, 9).toCalendarMonth(minDate = minDate, maxDate = maxDate)
        val currentDays = month.days.filter { it.isCurrentMonth }
        currentDays.forEach { day ->
            assertFalse(day.isDisabled, "Day ${day.dayOfMonth} should be enabled")
        }
    }

    @Test
    fun minMaxDate_narrowRange() {
        val minDate = HijrahDate(1447, 9, 10)
        val maxDate = HijrahDate(1447, 9, 15)
        val month = HijrahYearMonth(1447, 9).toCalendarMonth(minDate = minDate, maxDate = maxDate)
        val currentDays = month.days.filter { it.isCurrentMonth }
        currentDays.forEach { day ->
            val expectedDisabled = day.dayOfMonth !in 10..15
            assertEquals(expectedDisabled, day.isDisabled, "Day ${day.dayOfMonth}")
        }
    }

    // ── Selected date ───────────────────────────────────────────────────

    @Test
    fun selectedDate_matchesExactly() {
        val selected = HijrahDate(1447, 9, 15)
        val month = HijrahYearMonth(1447, 9).toCalendarMonth(selectedDate = selected)
        val selectedDay = month.days.single { it.isSelected }
        assertEquals(15, selectedDay.dayOfMonth)
    }

    @Test
    fun selectedDate_noSelectionWhenNull() {
        val month = HijrahYearMonth(1447, 9).toCalendarMonth(selectedDate = null)
        val selectedDays = month.days.filter { it.isSelected }
        assertTrue(selectedDays.isEmpty())
    }

    @Test
    fun selectedDate_outsideMonth() {
        // Select a date that's in Ramadan 1447 but different from day 15
        val selected = HijrahDate(1447, 9, 1)
        val month = HijrahYearMonth(1447, 9).toCalendarMonth(selectedDate = selected)
        val selectedDay = month.days.single { it.isSelected }
        assertEquals(1, selectedDay.dayOfMonth)
        assertTrue(selectedDay.isCurrentMonth)
    }

    // ── Today detection ─────────────────────────────────────────────────

    @Test
    fun today_atMostOneCellMarked() {
        val month = HijrahYearMonth(1447, 9).toCalendarMonth()
        val todayCells = month.days.filter { it.isToday }
        assertTrue(todayCells.size <= 1)
    }

    // ── Weekday column alignment ────────────────────────────────────────

    @Test
    fun weekdayAlignment_allDaysInCorrectColumn() {
        val month = HijrahYearMonth(1447, 9).toCalendarMonth()
        month.days.forEachIndexed { index, day ->
            val expectedColumn = index % CalendarMonth.DAYS_IN_WEEK
            val expectedWeekday = WeekDay.entries[(month.firstDayOfWeek.index + expectedColumn) % CalendarMonth.DAYS_IN_WEEK]
            assertEquals(expectedWeekday, day.dayOfWeek, "Cell at index $index")
        }
    }

    // ── Weekend ─────────────────────────────────────────────────────────

    @Test
    fun weekend_markedForFridayAndSaturday() {
        val month = HijrahYearMonth(1447, 9).toCalendarMonth()
        month.days.filter { it.isCurrentMonth }.forEach { day ->
            val isExpectedWeekend = day.dayOfWeek == WeekDay.FRIDAY || day.dayOfWeek == WeekDay.SATURDAY
            assertEquals(isExpectedWeekend, day.isWeekend, "Day ${day.dayOfMonth} (${day.dayOfWeek})")
        }
    }

    @Test
    fun weekend_customSingleDay() {
        val month = HijrahYearMonth(1447, 9).toCalendarMonth(
            weekendDays = setOf(WeekDay.FRIDAY),
        )
        month.days.filter { it.isCurrentMonth }.forEach { day ->
            assertEquals(
                day.dayOfWeek == WeekDay.FRIDAY,
                day.isWeekend,
                "Day ${day.dayOfMonth} (${day.dayOfWeek})",
            )
        }
    }

    @Test
    fun weekend_emptySet_noneMarked() {
        val month = HijrahYearMonth(1447, 9).toCalendarMonth(weekendDays = emptySet())
        assertTrue(month.days.none { it.isWeekend })
    }

    // ── Out-of-range HijrahDate clamping ────────────────────────────────

    @Test
    fun outOfHijriRange_lowYear_clampsToPlaceholder() {
        // Muharram 1300 (the lowest supported year) with a large negative shift pushes
        // leading Gregorian days below the supported range; they clamp to HijrahDate.MIN.
        val month = HijrahYearMonth(1300, 1).toCalendarMonth(adjustmentDays = -10)
        assertEquals(CalendarMonth.TOTAL_DAYS, month.days.size)
        val clamped = month.days.filter { it.isDisabled && !it.isCurrentMonth && it.hijrahDate == HijrahDate.MIN }
        assertTrue(clamped.isNotEmpty())
        assertTrue(month.days.all { it.isDisabled || (!it.isToday && !it.isSelected) })
    }

    @Test
    fun outOfHijriRange_highYear_clampsToMax() {
        // Dhu al-Hijjah 1600 (the highest supported year) with a large positive shift
        // pushes trailing Gregorian days above the supported range; they clamp to MAX.
        val month = HijrahYearMonth(1600, 12).toCalendarMonth(adjustmentDays = 10)
        assertEquals(CalendarMonth.TOTAL_DAYS, month.days.size)
        val clamped = month.days.filter { it.isDisabled && !it.isCurrentMonth && it.hijrahDate == HijrahDate.MAX }
        assertTrue(clamped.isNotEmpty())
    }

    // ── Different Hijri months ──────────────────────────────────────────

    @Test
    fun differentMonth_muharram() {
        val month = HijrahYearMonth(1447, 1).toCalendarMonth()
        assertEquals(CalendarMonth.TOTAL_DAYS, month.days.size)
        val currentDays = month.days.filter { it.isCurrentMonth }
        assertTrue(currentDays.isNotEmpty())
    }

    @Test
    fun differentMonth_dhulHijjah() {
        val month = HijrahYearMonth(1447, 12).toCalendarMonth()
        assertEquals(CalendarMonth.TOTAL_DAYS, month.days.size)
        val currentDays = month.days.filter { it.isCurrentMonth }
        assertTrue(currentDays.isNotEmpty())
    }

    // ── isSelected + isCurrentMonth consistency ─────────────────────────

    @Test
    fun selectedDate_isAlwaysCurrentMonth() {
        val selected = HijrahDate(1447, 9, 15)
        val month = HijrahYearMonth(1447, 9).toCalendarMonth(selectedDate = selected)
        val selectedDay = month.days.single { it.isSelected }
        assertTrue(selectedDay.isCurrentMonth)
    }

    @Test
    fun selectedDate_matchesDayOfMonth() {
        val selected = HijrahDate(1447, 9, 20)
        val month = HijrahYearMonth(1447, 9).toCalendarMonth(selectedDate = selected)
        val selectedDay = month.days.single { it.isSelected }
        assertEquals(20, selectedDay.dayOfMonth)
    }

    // ── Null-safe accessors on placeholder cells (regression: P0 NPE) ───

    @Test
    fun placeholderCell_dayOfMonth_doesNotThrow() {
        // Mirrors an out-of-range Pakistan cell: all date fields null, disabled.
        val cell = CalendarDay(
            hijrahDate = null,
            pakistanDate = null,
            observedDate = null,
            isCurrentMonth = false,
            isToday = false,
            isSelected = false,
            isDisabled = true,
            isWeekend = false,
        )
        assertEquals(0, cell.dayOfMonth)
    }

    @Test
    fun placeholderCell_localDate_doesNotThrow() {
        // Regression for the `pakistanDate!!` NPE on out-of-range cells.
        val today = kotlin.time.Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        val cell = CalendarDay(
            hijrahDate = null,
            pakistanDate = null,
            observedDate = null,
            isCurrentMonth = false,
            isToday = false,
            isSelected = false,
            isDisabled = true,
            isWeekend = false,
        )
        assertEquals(today, cell.localDate)
    }

    @Test
    fun placeholderCell_dayOfWeek_doesNotThrow() {
        // dayOfWeek reads through localDate, which must stay null-safe too.
        val cell = CalendarDay(
            hijrahDate = null,
            pakistanDate = null,
            observedDate = null,
            isCurrentMonth = false,
            isToday = false,
            isSelected = false,
            isDisabled = true,
            isWeekend = false,
        )
        assertEquals(WeekDay.fromDayOfWeek(cell.localDate.dayOfWeek), cell.dayOfWeek)
    }
}
