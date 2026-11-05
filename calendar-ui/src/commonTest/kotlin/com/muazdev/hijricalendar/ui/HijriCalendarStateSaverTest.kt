package com.muazdev.hijricalendar.ui

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import com.abdulrahman_b.hijrahdatetime.HijrahDate
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import com.muazdev.hijricalendar.core.HijriCalendarState
import com.muazdev.hijricalendar.core.HijriMonthOverrides
import com.muazdev.hijricalendar.core.ObservedHijriCalendar
import com.muazdev.hijricalendar.core.ObservedHijriDate
import com.muazdev.hijricalendar.core.WeekDay
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The `rememberSaveable` saver's round trips.
 *
 * **Why these moved here** (review-remediation 04). The saver, `HijriCalendarStateConfig` and the
 * two `remember*` entry points are Compose-facing and now live in `calendar-ui`; a test in
 * `calendar-core` cannot see `internal` declarations in another module, and `calendar-core` no
 * longer even depends on `androidx.compose.runtime.saveable`. The assertions are unchanged from the
 * two core test classes they came from — the saver is the same function, in a different module.
 *
 * The layout they pin (`current month, month, pakistan flag, selection tag, selection…`) is a
 * persisted format: a `rememberSaveable` bundle outlives the process and is read back by the next
 * build, so a reordering restores silently wrong state on a user's device instead of failing here.
 */
class HijriCalendarStateSaverTest {

    @AfterTest
    fun clearOverrides() {
        // `HijriMonthOverrides` is process-global by design, so a test that sets a length must put
        // it back or the next test in this module sees a 30-day Sha'ban.
        HijriMonthOverrides.clearAll()
    }

    private val testSaverScope = object : SaverScope {
        override fun canBeSaved(value: Any): Boolean = true
    }

    private fun <T> Saver<T, List<Int>>.saveForTest(value: T): List<Int>? =
        with(testSaverScope) { save(value) }

    private fun observedDate(
        year: Int,
        month: Int,
        day: Int,
        monthLength: Int = ObservedHijriCalendar.observedLength(year, month),
    ): ObservedHijriDate = ObservedHijriDate(
        year = year,
        month = month,
        day = day,
        monthLength = monthLength,
        localDate = ObservedHijriCalendar.observedToGregorian(
            year,
            month,
            day,
            HijriMonthOverrides.current,
        ),
    )

    private fun observedConfig() = HijriCalendarStateConfig(
        firstDayOfWeek = WeekDay.DEFAULT_FIRST_DAY,
        minDate = null,
        maxDate = null,
        adjustmentDays = 0,
        weekendDays = WeekDay.WEEKEND_DAYS,
    )

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

    @Test
    fun saver_roundTrip_preservesObservedSelection() {
        HijriMonthOverrides.setMonthLength(1448, 3, 30)
        val saver = hijriCalendarStateSaver(observedConfig())
        val original = HijriCalendarState(
            initialMonth = HijrahYearMonth(1448, 3),
            initialSelectedObservedDate = observedDate(1448, 3, 30, 30),
        )

        val restored = saver.restore(saver.saveForTest(original)!!)!!

        assertEquals(observedDate(1448, 3, 30, 30), restored.selectedObservedDate)
        assertNull(restored.selectedDate)
    }

    @Test
    fun saver_roundTrip_observedSelectionSurvivesWithoutOverrides() {
        // The observed date encodes its own effective month length, so it restores even if
        // the global overrides were cleared before the process restarted.
        val saver = hijriCalendarStateSaver(observedConfig())
        val original = HijriCalendarState(
            initialMonth = HijrahYearMonth(1448, 3),
            initialSelectedObservedDate = observedDate(1448, 3, 30, 30),
        )

        HijriMonthOverrides.clearAll()
        val restored = saver.restore(saver.saveForTest(original)!!)!!

        assertEquals(observedDate(1448, 3, 30, 30), restored.selectedObservedDate)
    }
}
