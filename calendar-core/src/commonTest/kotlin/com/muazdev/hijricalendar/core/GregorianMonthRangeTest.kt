package com.muazdev.hijricalendar.core

import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import com.abdulrahman_b.hijrahdatetime.toLocalDate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A Hijri month does not occupy the same real-world days in every calendar space. Before
 * [resolveGregorianMonthRange] existed, `CalendarMonth.gregorianFirstDay` / `gregorianLastDay`
 * always answered for the Umm al-Qura calculation, so they were silently wrong whenever
 * overrides were set or Pakistan mode was on — with no exception, just a header that disagreed
 * with the grid beneath it.
 *
 * These tests pin the space each answer belongs to. They are the regression guard for that class
 * of quiet wrongness.
 */
class GregorianMonthRangeTest {

    @AfterTest
    fun tearDown() = HijriMonthOverrides.clearAll()

    // ── the bug this replaces ──────────────────────────────────────────────

    @Test
    fun calendarMonthGregorianDaysComeFromThePakistanTableNotTheCalculation() {
        // 1440-11 is a 30-day month in the FIXES table, anchored at 2019-07-04. Reading the
        // extent off `HijrahYearMonth` (what CalendarMonth used to do unconditionally) cannot
        // reproduce a 30-day length that the calculation does not independently agree with.
        val month = HijrahYearMonth(1440, 11)
        val grid = month.toCalendarMonth(pakistan = true)

        assertEquals(LocalDate(2019, 7, 4), grid.gregorianFirstDay, "must be the Ruet-e-Hilal anchor")
        assertEquals(30, grid.resolvedGregorianRange.lengthInDays, "must be the FIXES length")
        assertEquals(
            LocalDate(2019, 8, 2),
            grid.gregorianLastDay,
            "a 30-day month from 2019-07-04 ends 2019-08-02",
        )
    }

    @Test
    fun thePakistanTableDisagreesWithTheCalculationSomewhereInItsRange() {
        // Guards the fixture above: proves the two spaces really do differ, so the assertion that
        // the table is being consulted is not vacuous.
        val disagreements = (PakistanHijriCalendar.MIN_YEAR..PakistanHijriCalendar.MAX_YEAR)
            .flatMap { year -> (1..12).map { month -> year to month } }
            .count { (year, month) ->
                val table = resolveGregorianMonthRange(year, month, pakistan = true)
                val calculated = resolveGregorianMonthRange(year, month, pakistan = false)
                table != calculated
            }

        assertTrue(
            disagreements > 50,
            "expected the Ruet-e-Hilal table to diverge from the calculation, got $disagreements months",
        )
    }

    @Test
    fun calendarMonthGregorianDaysFollowOverridesInObservedMode() {
        // Data-independent: force the month to whichever length the calculation does *not*
        // report, so this can never silently become a no-op if UAQ's table changes.
        val year = 1448
        val month = 3
        val calculatedLength = ObservedHijriCalendar.defaultLength(year, month)
        val forced = if (calculatedLength == 29) 30 else 29

        val uaqLast = HijrahYearMonth(year, month).lastDay.toLocalDate()
        HijriMonthOverrides.setMonthLength(year, month, forced)

        val observed = HijrahYearMonth(year, month).toCalendarMonth()

        assertEquals(forced.toLong(), observed.resolvedGregorianRange.lengthInDays.toLong())
        assertEquals(
            uaqLast.plus((forced - calculatedLength).toLong(), DateTimeUnit.DAY),
            observed.gregorianLastDay,
            "forcing $forced over a calculated $calculatedLength must move the end by " +
                "${forced - calculatedLength} day(s)",
        )
        assertNotEquals(uaqLast, observed.gregorianLastDay, "fixture is stale: the override was a no-op")
    }

    // ── the resolver ───────────────────────────────────────────────────────

    @Test
    fun plainSpaceIsTheUmmAlQuraExtent() {
        val month = HijrahYearMonth(1447, 9)
        val range = resolveGregorianMonthRange(1447, 9)

        assertEquals(month.firstDay.toLocalDate(), range.first)
        assertEquals(month.lastDay.toLocalDate(), range.last)
    }

    @Test
    fun pakistanSpaceComesFromTheFixesTable() {
        val range = resolveGregorianMonthRange(1441, 6, pakistan = true)
        assertEquals(LocalDate(2020, 1, 27), range.first)
        // 1441-6 is a 29-day month in the table.
        assertEquals(29, range.lengthInDays)
    }

    @Test
    fun observedSpaceIsUsedOnlyWhenOverridesExist() {
        val withoutOverrides = resolveGregorianMonthRange(1447, 9)
        HijriMonthOverrides.setMonthLength(1447, 9, withoutOverrides.lengthInDays - 1)
        val withOverrides = resolveGregorianMonthRange(1447, 9)

        assertEquals(withoutOverrides.lengthInDays - 1, withOverrides.lengthInDays)
        assertEquals(withoutOverrides.first, withOverrides.first, "an override moves the end, not the start")
    }

    @Test
    fun adjustmentDaysShiftsBothEndsEarlier() {
        val plain = resolveGregorianMonthRange(1447, 9, adjustmentDays = 0)
        val shifted = resolveGregorianMonthRange(1447, 9, adjustmentDays = 2)

        assertEquals(plain.first.minus(2, DateTimeUnit.DAY), shifted.first)
        assertEquals(plain.last.minus(2, DateTimeUnit.DAY), shifted.last)
        assertEquals(plain.lengthInDays, shifted.lengthInDays, "a shift must not change the length")
    }

    @Test
    fun adjustmentDaysAppliesInPakistanSpaceToo() {
        val plain = resolveGregorianMonthRange(1441, 6, pakistan = true)
        val shifted = resolveGregorianMonthRange(1441, 6, pakistan = true, adjustmentDays = 1)

        assertEquals(plain.first.minus(1, DateTimeUnit.DAY), shifted.first)
        assertEquals(plain.lengthInDays, shifted.lengthInDays)
    }

    // ── CalendarMonth wiring ───────────────────────────────────────────────

    @Test
    fun gridGeneratedRangeMatchesTheResolverForThatSpace() {
        // The point of storing the range on CalendarMonth: a consumer reading the grid's own
        // extent must get the same answer as asking the resolver directly.
        for (pakistan in listOf(false, true)) {
            val month = HijrahYearMonth(1441, 6)
            val grid = month.toCalendarMonth(pakistan = pakistan)
            val direct = resolveGregorianMonthRange(1441, 6, pakistan = pakistan)

            assertEquals(direct.first, grid.gregorianFirstDay, "pakistan=$pakistan")
            assertEquals(direct.last, grid.gregorianLastDay, "pakistan=$pakistan")
        }
    }

    @Test
    fun handConstructedMonthFallsBackToTheUmmAlQuraSpace() {
        // A preview builds a CalendarMonth directly. The fallback must not crash and must be the
        // plain calculation, which is the only correct answer without a mode.
        val month = CalendarMonth(
            yearMonth = HijrahYearMonth(1447, 9),
            days = persistentListOf<CalendarDay>(),
            firstDayOfWeek = WeekDay.SATURDAY,
        )

        assertNull(month.gregorianRange)
        assertEquals(HijrahYearMonth(1447, 9).firstDay.toLocalDate(), month.gregorianFirstDay)
    }

    @Test
    fun lengthInDaysMatchesTheMonthLengthOfItsSpace() {
        val observed = resolveGregorianMonthRange(1447, 9)
        assertEquals(
            observed.lengthInDays.toLong(),
            observed.last.toEpochDays() - observed.first.toEpochDays() + 1,
        )

        val pakistan = resolveGregorianMonthRange(1441, 6, pakistan = true)
        assertEquals(
            PakistanHijriCalendar.lengthOfMonth(1441, 6).toLong(),
            pakistan.last.toEpochDays() - pakistan.first.toEpochDays() + 1,
        )
    }

    @Test
    fun everyMonthInThePakistanTableResolvesWithoutThrowing() {
        // Sweeps the whole supported range so a year/month pair that trips a `require` in
        // lengthOfMonth is caught here rather than by a consumer.
        var months = 0
        for (year in PakistanHijriCalendar.MIN_YEAR..PakistanHijriCalendar.MAX_YEAR) {
            for (month in 1..12) {
                val range = resolveGregorianMonthRange(year, month, pakistan = true)
                assertTrue(range.lengthInDays in 29..30, "$year-$month resolved to ${range.lengthInDays} days")
                months++
            }
        }
        assertEquals(12 * (PakistanHijriCalendar.MAX_YEAR - PakistanHijriCalendar.MIN_YEAR + 1), months)
    }
}
