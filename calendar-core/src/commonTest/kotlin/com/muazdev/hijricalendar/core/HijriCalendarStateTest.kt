package com.muazdev.hijricalendar.core

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import com.abdulrahman_b.hijrahdatetime.HijrahDate
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HijriCalendarStateTest {

    // ── Initial state ───────────────────────────────────────────────────

    @Test
    fun initialState_currentMonthIsSet() {
        val state = HijriCalendarState(initialMonth = HijrahYearMonth(1447, 9))
        assertEquals(HijrahYearMonth(1447, 9), state.currentMonth)
    }

    @Test
    fun initialState_selectedDateIsNull() {
        val state = HijriCalendarState(initialMonth = HijrahYearMonth(1447, 9))
        assertNull(state.selectedDate)
    }

    @Test
    fun initialState_withSelectedDate() {
        val date = HijrahDate(1447, 9, 15)
        val state = HijriCalendarState(
            initialMonth = HijrahYearMonth(1447, 9),
            initialSelectedDate = date,
        )
        assertNotNull(state.selectedDate)
        assertEquals(15, state.selectedDate!!.day)
    }

    @Test
    fun initialState_firstDayOfWeekDefault() {
        val state = HijriCalendarState(initialMonth = HijrahYearMonth(1447, 9))
        assertEquals(WeekDay.DEFAULT_FIRST_DAY, state.firstDayOfWeek)
    }

    @Test
    fun initialState_firstDayOfWeekSunday() {
        val state = HijriCalendarState(
            initialMonth = HijrahYearMonth(1447, 9),
            firstDayOfWeek = WeekDay.SUNDAY,
        )
        assertEquals(WeekDay.SUNDAY, state.firstDayOfWeek)
    }

    @Test
    fun initialState_adjustmentDaysDefault() {
        val state = HijriCalendarState(initialMonth = HijrahYearMonth(1447, 9))
        assertEquals(0, state.adjustmentDays)
    }

    @Test
    fun initialState_adjustmentDaysCustom() {
        val state = HijriCalendarState(
            initialMonth = HijrahYearMonth(1447, 9),
            adjustmentDays = 1,
        )
        assertEquals(1, state.adjustmentDays)
    }

    // ── Navigation ──────────────────────────────────────────────────────

    @Test
    fun goToNextMonth_advancesMonth() {
        val state = HijriCalendarState(initialMonth = HijrahYearMonth(1447, 9))
        state.goToNextMonth()
        assertEquals(HijrahYearMonth(1447, 10), state.currentMonth)
    }

    @Test
    fun goToPreviousMonth_retreetsMonth() {
        val state = HijriCalendarState(initialMonth = HijrahYearMonth(1447, 9))
        state.goToPreviousMonth()
        assertEquals(HijrahYearMonth(1447, 8), state.currentMonth)
    }

    @Test
    fun goToNextMonth_multipleTimes() {
        val state = HijriCalendarState(initialMonth = HijrahYearMonth(1447, 9))
        state.goToNextMonth()
        state.goToNextMonth()
        state.goToNextMonth()
        assertEquals(HijrahYearMonth(1447, 12), state.currentMonth)
    }

    @Test
    fun goToPreviousMonth_multipleTimes() {
        val state = HijriCalendarState(initialMonth = HijrahYearMonth(1447, 9))
        state.goToPreviousMonth()
        state.goToPreviousMonth()
        state.goToPreviousMonth()
        assertEquals(HijrahYearMonth(1447, 6), state.currentMonth)
    }

    @Test
    fun goToNextMonth_crossesYearBoundary() {
        val state = HijriCalendarState(initialMonth = HijrahYearMonth(1447, 12))
        state.goToNextMonth()
        assertEquals(HijrahYearMonth(1448, 1), state.currentMonth)
    }

    @Test
    fun goToPreviousMonth_crossesYearBoundary() {
        val state = HijriCalendarState(initialMonth = HijrahYearMonth(1447, 1))
        state.goToPreviousMonth()
        assertEquals(HijrahYearMonth(1446, 12), state.currentMonth)
    }

    // ── goToMonth ───────────────────────────────────────────────────────

    @Test
    fun goToMonth_jumpsToTarget() {
        val state = HijriCalendarState(initialMonth = HijrahYearMonth(1447, 9))
        state.goToMonth(HijrahYearMonth(1450, 3))
        assertEquals(HijrahYearMonth(1450, 3), state.currentMonth)
    }

    @Test
    fun goToMonth_sameMonth() {
        val state = HijriCalendarState(initialMonth = HijrahYearMonth(1447, 9))
        state.goToMonth(HijrahYearMonth(1447, 9))
        assertEquals(HijrahYearMonth(1447, 9), state.currentMonth)
    }

    // ── selectDate ──────────────────────────────────────────────────────

    @Test
    fun selectDate_updatesSelectedDate() {
        val state = HijriCalendarState(initialMonth = HijrahYearMonth(1447, 9))
        state.selectDate(HijrahDate(1447, 9, 15))
        assertNotNull(state.selectedDate)
        assertEquals(15, state.selectedDate!!.day)
    }

    @Test
    fun selectDate_sameMonth_doesNotNavigate() {
        val state = HijriCalendarState(initialMonth = HijrahYearMonth(1447, 9))
        state.selectDate(HijrahDate(1447, 9, 20))
        assertEquals(HijrahYearMonth(1447, 9), state.currentMonth)
    }

    @Test
    fun selectDate_differentMonth_navigatesToThatMonth() {
        val state = HijriCalendarState(initialMonth = HijrahYearMonth(1447, 9))
        state.selectDate(HijrahDate(1447, 10, 5))
        assertEquals(HijrahYearMonth(1447, 10), state.currentMonth)
    }

    @Test
    fun selectDate_outsideRange_minDate_doesNotUpdate() {
        val state = HijriCalendarState(
            initialMonth = HijrahYearMonth(1447, 9),
            minDate = HijrahDate(1447, 9, 5),
        )
        state.selectDate(HijrahDate(1447, 9, 1))
        assertNull(state.selectedDate)
    }

    @Test
    fun selectDate_outsideRange_maxDate_doesNotUpdate() {
        val state = HijriCalendarState(
            initialMonth = HijrahYearMonth(1447, 9),
            maxDate = HijrahDate(1447, 9, 25),
        )
        state.selectDate(HijrahDate(1447, 9, 28))
        assertNull(state.selectedDate)
    }

    @Test
    fun selectDate_onMinDate_succeeds() {
        val state = HijriCalendarState(
            initialMonth = HijrahYearMonth(1447, 9),
            minDate = HijrahDate(1447, 9, 5),
        )
        state.selectDate(HijrahDate(1447, 9, 5))
        assertNotNull(state.selectedDate)
        assertEquals(5, state.selectedDate!!.day)
    }

    @Test
    fun selectDate_onMaxDate_succeeds() {
        val state = HijriCalendarState(
            initialMonth = HijrahYearMonth(1447, 9),
            maxDate = HijrahDate(1447, 9, 25),
        )
        state.selectDate(HijrahDate(1447, 9, 25))
        assertNotNull(state.selectedDate)
        assertEquals(25, state.selectedDate!!.day)
    }

    @Test
    fun selectDate_replacesPreviousSelection() {
        val state = HijriCalendarState(initialMonth = HijrahYearMonth(1447, 9))
        state.selectDate(HijrahDate(1447, 9, 10))
        state.selectDate(HijrahDate(1447, 9, 20))
        assertEquals(20, state.selectedDate!!.day)
    }

    // ── Navigation bounds ────────────────────────────────────────────────

    @Test
    fun canGoToPreviousMonth_trueByDefault() {
        val state = HijriCalendarState(initialMonth = HijrahYearMonth(1447, 9))
        assertTrue(state.canGoToPreviousMonth)
    }

    @Test
    fun canGoToNextMonth_trueByDefault() {
        val state = HijriCalendarState(initialMonth = HijrahYearMonth(1447, 9))
        assertTrue(state.canGoToNextMonth)
    }

    @Test
    fun canGoToPreviousMonth_falseWhenMinDateInCurrentMonth() {
        val state = HijriCalendarState(
            initialMonth = HijrahYearMonth(1447, 9),
            minDate = HijrahDate(1447, 9, 5),
        )
        // Shaban 1447 ends before the minDate in Ramadan, so it is not navigable.
        assertFalse(state.canGoToPreviousMonth)
    }

    @Test
    fun canGoToNextMonth_falseWhenMaxDateInCurrentMonth() {
        val state = HijriCalendarState(
            initialMonth = HijrahYearMonth(1447, 9),
            maxDate = HijrahDate(1447, 9, 25),
        )
        // Shawwal 1447 starts after the maxDate in Ramadan, so it is not navigable.
        assertFalse(state.canGoToNextMonth)
    }

    @Test
    fun canGoToPreviousMonth_trueWhenMinDateInPreviousMonth() {
        val state = HijriCalendarState(
            initialMonth = HijrahYearMonth(1447, 9),
            minDate = HijrahDate(1447, 8, 20),
        )
        // Shaban 1447 contains days >= minDate, so it is navigable.
        assertTrue(state.canGoToPreviousMonth)
        state.goToPreviousMonth()
        // Rajab 1447 ends before minDate now, so it is no longer navigable.
        assertFalse(state.canGoToPreviousMonth)
    }

    @Test
    fun canGoToNextMonth_trueWhenMaxDateInNextMonth() {
        val state = HijriCalendarState(
            initialMonth = HijrahYearMonth(1447, 9),
            maxDate = HijrahDate(1447, 10, 10),
        )
        // Shawwal 1447 contains days <= maxDate, so it is navigable.
        assertTrue(state.canGoToNextMonth)
        state.goToNextMonth()
        // Dhu al-Qadah 1447 starts after maxDate now, so it is no longer navigable.
        assertFalse(state.canGoToNextMonth)
    }

    @Test
    fun goToPreviousMonth_atHijriRangeMin_clampsNotThrows() {
        val state = HijriCalendarState(initialMonth = HijrahYearMonth(1300, 1))
        state.goToPreviousMonth()
        // Past the supported range: stays clamped at the earliest month.
        assertEquals(HijrahYearMonth(1300, 1), state.currentMonth)
    }

    @Test
    fun goToNextMonth_atHijriRangeMax_clampsNotThrows() {
        val state = HijriCalendarState(initialMonth = HijrahYearMonth(1600, 12))
        state.goToNextMonth()
        // Past the supported range: stays clamped at the latest month.
        assertEquals(HijrahYearMonth(1600, 12), state.currentMonth)
    }

    // ── goToToday ───────────────────────────────────────────────────────

    @Test
    fun goToToday_setsSelectedDate() {
        val state = HijriCalendarState(initialMonth = HijrahYearMonth(1445, 1))
        state.goToToday()
        assertNotNull(state.selectedDate)
    }

    @Test
    fun goToToday_navigatesToTodayMonth() {
        val state = HijriCalendarState(initialMonth = HijrahYearMonth(1445, 1))
        state.goToToday()
        // currentMonth should be the month of today
        assertNotNull(state.currentMonth)
    }

    // ── calendarMonth ───────────────────────────────────────────────────

    @Test
    fun calendarMonth_reflectsCurrentMonth() {
        val state = HijriCalendarState(initialMonth = HijrahYearMonth(1447, 9))
        val calMonth = state.calendarMonth
        assertEquals(HijrahYearMonth(1447, 9), calMonth.yearMonth)
    }

    @Test
    fun calendarMonth_has42Cells() {
        val state = HijriCalendarState(initialMonth = HijrahYearMonth(1447, 9))
        assertEquals(CalendarMonth.TOTAL_DAYS, state.calendarMonth.days.size)
    }

    @Test
    fun calendarMonth_afterNavigation_reflectsNewMonth() {
        val state = HijriCalendarState(initialMonth = HijrahYearMonth(1447, 9))
        state.goToNextMonth()
        assertEquals(HijrahYearMonth(1447, 10), state.calendarMonth.yearMonth)
    }

    @Test
    fun calendarMonth_selectedDateReflected() {
        val state = HijriCalendarState(initialMonth = HijrahYearMonth(1447, 9))
        state.selectDate(HijrahDate(1447, 9, 15))
        val selectedDays = state.calendarMonth.days.filter { it.isSelected }
        assertEquals(1, selectedDays.size)
        assertEquals(15, selectedDays.first().dayOfMonth)
    }

    @Test
    fun calendarMonth_respectsFirstDayOfWeek() {
        val state = HijriCalendarState(
            initialMonth = HijrahYearMonth(1447, 9),
            firstDayOfWeek = WeekDay.SUNDAY,
        )
        assertEquals(WeekDay.SUNDAY, state.calendarMonth.firstDayOfWeek)
    }

    @Test
    fun calendarMonth_withMinDate() {
        val state = HijriCalendarState(
            initialMonth = HijrahYearMonth(1447, 9),
            minDate = HijrahDate(1447, 9, 10),
        )
        val disabledDays = state.calendarMonth.days.filter { it.isCurrentMonth && it.isDisabled }
        assertTrue(disabledDays.all { it.dayOfMonth < 10 })
    }

    @Test
    fun calendarMonth_withMaxDate() {
        val state = HijriCalendarState(
            initialMonth = HijrahYearMonth(1447, 9),
            maxDate = HijrahDate(1447, 9, 20),
        )
        val disabledDays = state.calendarMonth.days.filter { it.isCurrentMonth && it.isDisabled }
        assertTrue(disabledDays.all { it.dayOfMonth > 20 })
    }

    @Test
    fun calendarMonth_withAdjustmentDays() {
        val state = HijriCalendarState(
            initialMonth = HijrahYearMonth(1447, 9),
            adjustmentDays = 1,
        )
        val calMonth = state.calendarMonth
        assertEquals(1, calMonth.adjustmentDays)
        assertEquals(CalendarMonth.TOTAL_DAYS, calMonth.days.size)
    }

    // ── Min / max date properties ───────────────────────────────────────

    @Test
    fun minDate_propertyIsNullByDefault() {
        val state = HijriCalendarState(initialMonth = HijrahYearMonth(1447, 9))
        assertNull(state.minDate)
    }

    @Test
    fun maxDate_propertyIsNullByDefault() {
        val state = HijriCalendarState(initialMonth = HijrahYearMonth(1447, 9))
        assertNull(state.maxDate)
    }

    @Test
    fun minDate_propertyIsStored() {
        val min = HijrahDate(1447, 9, 5)
        val state = HijriCalendarState(
            initialMonth = HijrahYearMonth(1447, 9),
            minDate = min,
        )
        assertEquals(min, state.minDate)
    }

    @Test
    fun maxDate_propertyIsStored() {
        val max = HijrahDate(1447, 9, 25)
        val state = HijriCalendarState(
            initialMonth = HijrahYearMonth(1447, 9),
            maxDate = max,
        )
        assertEquals(max, state.maxDate)
    }

    // ── rememberSaveable Saver ─────────────────────────────────────────

    private val testSaverScope = object : SaverScope {
        override fun canBeSaved(value: Any): Boolean = true
    }

    private fun <T> Saver<T, List<Int>>.saveForTest(value: T): List<Int>? =
        with(testSaverScope) { save(value) }

    @Test
    fun saver_roundTrip_preservesMonthSelectionAndConfig() {
        val config = HijriCalendarStateConfig(
            firstDayOfWeek = WeekDay.SUNDAY,
            minDate = null,
            maxDate = null,
            adjustmentDays = 0,
            weekendDays = setOf(WeekDay.FRIDAY),
        )
        val saver = hijriCalendarStateSaver(config)
        val state = HijriCalendarState(
            initialMonth = HijrahYearMonth(1447, 9),
            initialSelectedDate = HijrahDate(1447, 9, 15),
            firstDayOfWeek = WeekDay.SUNDAY,
            weekendDays = setOf(WeekDay.FRIDAY),
        )
        state.goToNextMonth()

        val saved = saver.saveForTest(state)
        assertNotNull(saved)
        val restored = saver.restore(saved)

        assertNotNull(restored)
        assertEquals(HijrahYearMonth(1447, 10), restored.currentMonth)
        assertEquals(HijrahDate(1447, 9, 15), restored.selectedDate)
        assertEquals(WeekDay.SUNDAY, restored.firstDayOfWeek)
        assertEquals(setOf(WeekDay.FRIDAY), restored.weekendDays)
    }

    @Test
    fun saver_roundTrip_withAdjustmentDays_preservesAdjustedSelection() {
        val config = HijriCalendarStateConfig(
            firstDayOfWeek = WeekDay.DEFAULT_FIRST_DAY,
            minDate = null,
            maxDate = null,
            adjustmentDays = 1,
            weekendDays = WeekDay.WEEKEND_DAYS,
        )
        val saver = hijriCalendarStateSaver(config)
        val original = HijriCalendarState(
            initialMonth = HijrahYearMonth(1447, 9),
            initialSelectedDate = HijrahDate(1447, 9, 15),
            adjustmentDays = 1,
        )
        assertEquals(16, original.selectedDate!!.day)

        val restored = saver.restore(saver.saveForTest(original)!!)!!

        assertEquals(original.selectedDate, restored.selectedDate)
    }

    @Test
    fun saver_roundTrip_emptySelection() {
        val config = HijriCalendarStateConfig(
            firstDayOfWeek = WeekDay.DEFAULT_FIRST_DAY,
            minDate = null,
            maxDate = null,
            adjustmentDays = 0,
            weekendDays = WeekDay.WEEKEND_DAYS,
        )
        val saver = hijriCalendarStateSaver(config)
        val original = HijriCalendarState(initialMonth = HijrahYearMonth(1447, 9))

        val restored = saver.restore(saver.saveForTest(original)!!)!!

        assertEquals(HijrahYearMonth(1447, 9), restored.currentMonth)
        assertNull(restored.selectedDate)
    }
}
