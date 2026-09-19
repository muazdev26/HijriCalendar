package com.muazdev.hijricalendar.core

import com.abdulrahman_b.hijrahdatetime.toLocalDate
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import kotlinx.datetime.LocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.datetime.minus
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regression tests for manual month-end length overrides (29/30 days).
 *
 * These are intentionally global-state driven: the overrides live in the process-wide
 * [HijriMonthOverrides] singleton (library-wide by design), so every test is [AfterTest]
 * cleaned back to the pristine calculated calendar.
 */
class MonthLengthOverridesTest {

    @AfterTest
    fun tearDown() {
        HijriMonthOverrides.clearAll()
    }

    // ── HijriMonthOverrides state machine ───────────────────────────────

    @Test
    fun set_get_clear_roundTrips() {
        HijriMonthOverrides.setMonthLength(1448, 3, 30)
        assertEquals(30, HijriMonthOverrides.monthLength(1448, 3))
        HijriMonthOverrides.clearMonthLength(1448, 3)
        assertNull(HijriMonthOverrides.monthLength(1448, 3))
    }

    @Test
    fun set_overwritesExisting() {
        HijriMonthOverrides.setMonthLength(1448, 3, 30)
        HijriMonthOverrides.setMonthLength(1448, 3, 29)
        assertEquals(29, HijriMonthOverrides.monthLength(1448, 3))
    }

    @Test
    fun clearAll_removesEveryOverride() {
        HijriMonthOverrides.setMonthLength(1448, 3, 30)
        HijriMonthOverrides.setMonthLength(1448, 4, 29)
        HijriMonthOverrides.clearAll()
        assertTrue(HijriMonthOverrides.all().isEmpty())
    }

    @Test
    fun set_rejectsLengthsOutside2930() {
        assertFailsWith<IllegalArgumentException> { HijriMonthOverrides.setMonthLength(1448, 3, 28) }
        assertFailsWith<IllegalArgumentException> { HijriMonthOverrides.setMonthLength(1448, 3, 31) }
        assertFailsWith<IllegalArgumentException> { HijriMonthOverrides.setMonthLength(1448, 3, 0) }
        assertTrue(HijriMonthOverrides.all().isEmpty())
    }

    @Test
    fun replaceAll_validatesAndReplaces() {
        assertFailsWith<IllegalArgumentException> { HijriMonthOverrides.replaceAll(mapOf((1448 to 3) to 28)) }
        HijriMonthOverrides.replaceAll(mapOf((1448 to 3) to 30, (1448 to 4) to 29))
        assertEquals(mapOf((1448 to 3) to 30, (1448 to 4) to 29), HijriMonthOverrides.all())
    }

    @Test
    fun revision_bumpsOnEveryEffectiveChange() {
        val before = HijriMonthOverrides.currentRevision
        HijriMonthOverrides.setMonthLength(1448, 3, 30)
        assertTrue(HijriMonthOverrides.currentRevision > before)
        val afterSet = HijriMonthOverrides.currentRevision
        // Same value set is a no-op and must not bump the revision.
        HijriMonthOverrides.setMonthLength(1448, 3, 30)
        assertEquals(afterSet, HijriMonthOverrides.currentRevision)
        HijriMonthOverrides.clearAll()
        assertTrue(HijriMonthOverrides.currentRevision > afterSet)
    }

    // ── Observed (override-shifted Umm al-Qura) resolver ────────────────

    @Test
    fun observed_calculationUsedWhenNoOverride() {
        val expecting = HijrahYearMonth(1448, 3).numberOfDays
        assertEquals(expecting, ObservedHijriCalendar.observedLength(1448, 3))
        assertEquals(expecting, ObservedHijriCalendar.defaultLength(1448, 3))
    }

    @Test
    fun observed_forcedMonthExtendsAndNextMonthShifts() {
        HijriMonthOverrides.setMonthLength(1448, 3, 30)
        assertEquals(30, ObservedHijriCalendar.observedLength(1448, 3))

        // The forced 30th is a real Gregorian day, not a clamped day 30 of a 29-day month.
        val forceDay29 = ObservedHijriCalendar.observedToGregorian(1448, 3, 29)
        val forceDay30 = ObservedHijriCalendar.observedToGregorian(1448, 3, 30)
        assertEquals(forceDay29.plus(1, DateTimeUnit.DAY), forceDay30)

        // Wrapping into the next month continues from the extended month end.
        val nextStart = ObservedHijriCalendar.observedToGregorian(1448, 4, 1)
        assertEquals(forceDay30.plus(1, DateTimeUnit.DAY), nextStart)
    }

    @Test
    fun observed_clearingRestoresCalculation() {
        HijriMonthOverrides.setMonthLength(1448, 3, 30)
        val before = ObservedHijriCalendar.observedToGregorian(1448, 4, 1)
        HijriMonthOverrides.clearMonthLength(1448, 3)
        val after = ObservedHijriCalendar.observedToGregorian(1448, 4, 1)
        assertNotEquals(before, after)
        assertEquals(HijrahYearMonth(1448, 4).firstDay.toLocalDate(), after)
    }

    @Test
    fun observed_beforeDateUnchangedButAfterDateShifts() {
        HijriMonthOverrides.setMonthLength(1448, 3, 30)
        // Months before the forced month keep their calculated start.
        assertEquals(
            HijrahYearMonth(1448, 2).firstDay.toLocalDate(),
            ObservedHijriCalendar.observedToGregorian(1448, 2, 1),
        )
    }

    @Test
    fun observed_dateAt_roundTripsEveryDayOfForcedMonthAndAfter() {
        HijriMonthOverrides.setMonthLength(1448, 3, 30)
        val first = ObservedHijriCalendar.observedToGregorian(1448, 3, 1).toEpochDays()
        val last = ObservedHijriCalendar.observedToGregorian(1448, 5, 1).toEpochDays()
        for (epoch in first..last) {
            val observed = ObservedHijriCalendar.observedDateAt(epoch)!!
            val expectedStart = ObservedHijriCalendar.observedToGregorian(observed.year, observed.month, 1).toEpochDays()
            assertEquals(epoch - expectedStart + 1, observed.day.toLong())
            val back = ObservedHijriCalendar.observedToGregorian(observed.year, observed.month, observed.day).toEpochDays()
            assertEquals(epoch, back)
        }
    }

    @Test
    fun observed_dateAt_survivesExtremeCumulativeDrift() {
        // Force ~800 consecutive months to 30 days. Default UAQ months are mostly 29 days, so
        // the cumulative drift grows to ~800 days — far beyond the old 24-step walk cap. The
        // direct proleptic search must still resolve the exact containing month.
        val monthCount = 800
        val startProleptic = 1440 * 12
        for (i in 0 until monthCount) {
            val p = startProleptic + i
            HijriMonthOverrides.setMonthLength(p / 12, p % 12 + 1, 30)
        }
        assertEquals(monthCount, HijriMonthOverrides.all().size)

        // Probe early, mid, and deep into the drifting timeline.
        val probes = listOf(
            ObservedHijriCalendar.observedToGregorian(1440, 6, 1).toEpochDays(),
            ObservedHijriCalendar.observedToGregorian(1445, 1, 10).toEpochDays(),
            ObservedHijriCalendar.observedToGregorian(1449, 11, 25).toEpochDays(),
        )
        for (epoch in probes) {
            val observed = ObservedHijriCalendar.observedDateAt(epoch)!!
            assertTrue(
                observed.day in 1..observed.monthLength,
                "day ${observed.day} must stay inside month of ${observed.monthLength} days",
            )
            // Exact round-trip: the resolved observed date must map back to the same Gregorian day.
            val back = ObservedHijriCalendar.observedToGregorian(
                observed.year, observed.month, observed.day,
            ).toEpochDays()
            assertEquals(epoch, back)
        }
    }

    @Test
    fun observed_grid_appliesOverrideAndStaysGapless() {
        HijriMonthOverrides.setMonthLength(1448, 3, 30)
        val grid = HijrahYearMonth(1448, 3).toCalendarMonth()
        val observedDates = grid.days.mapNotNull { it.observedDate }

        // The forced 30th appears as a real, enabled cell.
        assertTrue(observedDates.any { it.day == 30 && it.month == 3 && it.year == 1448 })
        // Every cell resolves to a consecutive observed date.
        observedDates.zipWithNext().forEach { (a, b) ->
            assertEquals(nextObserved(a), b, "observed grid must stay gapless: $a -> $b")
        }
    }

    @Test
    fun observed_grid_revertsToPlainUmmalQuraWhenCleared() {
        HijriMonthOverrides.setMonthLength(1448, 3, 30)
        HijriMonthOverrides.clearAll()
        val grid = HijrahYearMonth(1448, 3).toCalendarMonth()
        assertTrue(grid.days.all { it.observedDate == null })
        assertTrue(grid.days.all { it.hijrahDate != null })
    }

    @Test
    fun observed_today_resolvesInForcedCalendar() {
        HijriMonthOverrides.setMonthLength(1448, 3, 30)
        val today = ObservedHijriCalendar.today(0)
        assertNotNull(today)
        assertTrue(today.day in 1..ObservedHijriCalendar.observedLength(today.year, today.month))
    }

    // ── Pakistan (Ruet-e-Hilal) override precedence ─────────────────────

    @Test
    fun pakistan_overrideWinsOverFixes() {
        // 1440-10 is officially 29 days (fix). Force 30 and the FIXES table must lose.
        assertEquals(29, PakistanHijriCalendar.defaultLengthOfMonth(1440, 10))
        HijriMonthOverrides.setMonthLength(1440, 10, 30)
        assertEquals(30, PakistanHijriCalendar.lengthOfMonth(1440, 10))
        assertEquals(29, PakistanHijriCalendar.defaultLengthOfMonth(1440, 10))
    }

    @Test
    fun pakistan_forcedMonthMovesNextStartUntilFixReanchors() {
        // 1448-05 onward has no fix: forcing 1448-05 to the opposite of its calculation
        // shifts the 1448-06 start by the same delta off the precomputed table.
        val defaultLength = PakistanHijriCalendar.defaultLengthOfMonth(1448, 5)
        val forced = if (defaultLength == 30) 29 else 30
        val unforced6Start = PakistanHijriCalendar.hijriToGregorian(1448, 6, 1)

        HijriMonthOverrides.setMonthLength(1448, 5, forced)

        assertEquals(forced, PakistanHijriCalendar.lengthOfMonth(1448, 5))
        // The 5th's own start is untouched (fix-less chain starts from the previous fix).
        assertEquals(LocalDate(2026, 10, 14), PakistanHijriCalendar.hijriToGregorian(1448, 5, 1))
        // The 6th's start slides by (forced - defaultLength).
        val expectedShift = (forced - defaultLength).toLong()
        assertEquals(
            unforced6Start.toEpochDays() + expectedShift,
            PakistanHijriCalendar.hijriToGregorian(1448, 6, 1).toEpochDays(),
        )
    }

    @Test
    fun pakistan_clearingRestoresCalculatedLength() {
        HijriMonthOverrides.setMonthLength(1448, 5, 30)
        HijriMonthOverrides.clearMonthLength(1448, 5)
        assertEquals(
            PakistanHijriCalendar.defaultLengthOfMonth(1448, 5),
            PakistanHijriCalendar.lengthOfMonth(1448, 5),
        )
    }

    @Test
    fun pakistan_grid_survivesOverrideMutation() {
        HijriMonthOverrides.setMonthLength(1448, 5, 30)
        val grid = HijrahYearMonth(1448, 5).toCalendarMonth(pakistan = true)
        assertTrue(grid.days.mapNotNull { it.pakistanDate }.any { it.day == 30 })
    }

    // ── HijriCalendarState observed mode ────────────────────────────────

    @Test
    fun state_selectDay_routesToObservedWhenOverridePresent() {
        HijriMonthOverrides.setMonthLength(1448, 3, 30)
        val state = HijriCalendarState(initialMonth = HijrahYearMonth(1448, 3))
        val day = state.calendarMonth.days.first { it.observedDate?.day == 30 && it.observedDate?.month == 3 }

        state.selectDay(day)

        assertEquals(ObservedHijriDate(1448, 3, 30, 30), state.selectedObservedDate)
        assertNull(state.selectedDate)
        assertEquals(HijrahYearMonth(1448, 3), state.currentMonth)
    }

    @Test
    fun state_goToToday_resolvesInObservedCalendar() {
        HijriMonthOverrides.setMonthLength(1448, 3, 30)
        val state = HijriCalendarState(initialMonth = HijrahYearMonth(1447, 1))
        state.goToToday()
        assertNotNull(state.selectedObservedDate)
    }

    @Test
    fun state_setMonthLength_viaStateIsVisibleInGrid() {
        val state = HijriCalendarState(initialMonth = HijrahYearMonth(1448, 3))
        assertFalse(state.calendarMonth.days.any { it.observedDate != null })

        state.setMonthLength(1448, 3, 30)

        assertTrue(state.calendarMonth.days.any { it.observedDate?.day == 30 })
        assertEquals(30, state.monthLengthOf(1448, 3))

        state.clearMonthLength(1448, 3)
        assertFalse(state.calendarMonth.days.any { it.observedDate != null })
        assertNull(state.monthLengthOf(1448, 3))
    }

    @Test
    fun state_setPakistanDates_carriesObservedSelectionByGregorianDay() {
        HijriMonthOverrides.setMonthLength(1448, 3, 30)
        val state = HijriCalendarState(
            initialMonth = HijrahYearMonth(1448, 3),
            initialSelectedObservedDate = ObservedHijriDate(1448, 3, 30, 30),
        )

        state.setPakistanDates(true)

        // The observed 30th falls on the Pakistan calendar's "real-world same day".
        assertEquals(
            ObservedHijriDate(1448, 3, 30, 30).localDate,
            state.selectedPakistanDate?.localDate,
        )
        assertTrue(state.pakistanDates)

        state.setPakistanDates(false)
        assertEquals(ObservedHijriDate(1448, 3, 30, 30).localDate, state.selectedObservedDate?.localDate)
    }

    @Test
    fun state_setAdjustmentDays_keepsObservedSelectionOnSameGregorianDay() {
        HijriMonthOverrides.setMonthLength(1448, 3, 30)
        val state = HijriCalendarState(
            initialMonth = HijrahYearMonth(1448, 3),
            initialSelectedObservedDate = ObservedHijriDate(1448, 3, 30, 30),
        )
        val before = state.selectedObservedDate!!.localDate

        state.setAdjustmentDays(1)

        assertEquals(1, state.adjustmentDays)
        assertEquals(before, state.selectedObservedDate!!.localDate.minus(1, DateTimeUnit.DAY))
    }

    // ── rememberSaveable saver with observed selection ──────────────────

    private val testSaverScope = object : SaverScope {
        override fun canBeSaved(value: Any): Boolean = true
    }

    private fun <T> Saver<T, List<Int>>.saveForTest(value: T): List<Int>? =
        with(testSaverScope) { save(value) }

    private fun observedConfig() = HijriCalendarStateConfig(
        firstDayOfWeek = WeekDay.DEFAULT_FIRST_DAY,
        minDate = null,
        maxDate = null,
        adjustmentDays = 0,
        weekendDays = WeekDay.WEEKEND_DAYS,
    )

    @Test
    fun saver_roundTrip_preservesObservedSelection() {
        HijriMonthOverrides.setMonthLength(1448, 3, 30)
        val saver = hijriCalendarStateSaver(observedConfig())
        val original = HijriCalendarState(
            initialMonth = HijrahYearMonth(1448, 3),
            initialSelectedObservedDate = ObservedHijriDate(1448, 3, 30, 30),
        )

        val restored = saver.restore(saver.saveForTest(original)!!)!!

        assertEquals(ObservedHijriDate(1448, 3, 30, 30), restored.selectedObservedDate)
        assertNull(restored.selectedDate)
    }

    @Test
    fun saver_roundTrip_observedSelectionSurvivesWithoutOverrides() {
        // The observed date encodes its own effective month length, so it restores even if
        // the global overrides were cleared before the process restarted.
        val saver = hijriCalendarStateSaver(observedConfig())
        val original = HijriCalendarState(
            initialMonth = HijrahYearMonth(1448, 3),
            initialSelectedObservedDate = ObservedHijriDate(1448, 3, 30, 30),
        )

        HijriMonthOverrides.clearAll()
        val restored = saver.restore(saver.saveForTest(original)!!)!!

        assertEquals(ObservedHijriDate(1448, 3, 30, 30), restored.selectedObservedDate)
    }

    private fun nextObserved(date: ObservedHijriDate): ObservedHijriDate {
        val length = ObservedHijriCalendar.observedLength(date.year, date.month)
        return when {
            date.day < length -> ObservedHijriDate(date.year, date.month, date.day + 1, length)
            date.month < 12 -> ObservedHijriDate(date.year, date.month + 1, 1, ObservedHijriCalendar.observedLength(date.year, date.month + 1))
            else -> ObservedHijriDate(date.year + 1, 1, 1, ObservedHijriCalendar.observedLength(date.year + 1, 1))
        }
    }
}