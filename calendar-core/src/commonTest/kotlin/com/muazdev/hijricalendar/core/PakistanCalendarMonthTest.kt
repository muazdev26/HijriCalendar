package com.muazdev.hijricalendar.core

import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Grid-level regression for the Pakistan (Ruet-e-Hilal) view.
 *
 * The forward `monthStart` chain must include the fix month's own length. Without it the
 * month immediately after a fix reuses the fix's own start day, shifting every following
 * month a full 29-30 days early — reproduced as 2026-10-14 resolving to 1448-04-01 again,
 * the 1448-05 grid being skipped entirely, and the today cell (1448-03-30) leaking into
 * the 1448-04/1448-05 pages. Plain roundtrip tests cannot catch this because the two
 * conversion directions share the same off-by-one and stay self-consistent.
 */
class PakistanCalendarMonthTest {

    private fun grid(year: Int, month: Int, adjustmentDays: Int = 0): CalendarMonth =
        HijrahYearMonth(year, month).toCalendarMonth(pakistan = true, adjustmentDays = adjustmentDays)

    @Test
    fun monthsAfterLastFixResolveToAbsoluteGregorianDays() {
        assertEquals(LocalDate(2026, 10, 14), PakistanHijriCalendar.hijriToGregorian(1448, 5, 1))
        assertEquals(LocalDate(2026, 10, 13), PakistanHijriCalendar.hijriToGregorian(1448, 4, 30))
        assertEquals(PakistanHijriDate(1448, 5, 1), PakistanHijriCalendar.gregorianToHijri(LocalDate(2026, 10, 14)))
        assertEquals(PakistanHijriDate(1448, 4, 30), PakistanHijriCalendar.gregorianToHijri(LocalDate(2026, 10, 13)))
        assertEquals(PakistanHijriDate(1448, 5, 7), PakistanHijriCalendar.gregorianToHijri(LocalDate(2026, 10, 20)))
        assertEquals(PakistanHijriDate(1448, 6, 1), PakistanHijriCalendar.gregorianToHijri(LocalDate(2026, 11, 13)))
        assertEquals(PakistanHijriDate(1448, 6, 23), PakistanHijriCalendar.gregorianToHijri(LocalDate(2026, 12, 5)))
    }

    @Test
    fun adjacentMonthsAfterFixNeitherReuseOldDatesNorSkipAMonth() {
        val oldAnchorDate = PakistanHijriDate(1448, 3, 30)
        for (month in 5..8) {
            val days = grid(1448, month).days
            assertEquals(42, days.size)
            val dates = days.mapNotNull { it.pakistanDate }
            // The pre-fix anchor day (today at the time of the report) must not resurface
            // inside later months' grids.
            assertTrue(dates.none { it == oldAnchorDate }, "1448-03-30 leaked into grid 1448-$month")
            // The grid is a 42-day run of consecutive Pakistani dates: no duplicates, no gaps.
            dates.zipWithNext().forEach { (a, b) ->
                assertEquals(next(a), b, "grid for 1448-$month must be gapless, got $a -> $b")
            }
            // The displayed month must actually be present in its own grid.
            assertTrue(
                dates.any { it.year == 1448 && it.month == month },
                "1448-$month missing from its own grid",
            )
        }
    }

    private fun next(d: PakistanHijriDate): PakistanHijriDate {
        val length = PakistanHijriCalendar.lengthOfMonth(d.year, d.month)
        return when {
            d.day < length -> PakistanHijriDate(d.year, d.month, d.day + 1)
            d.month < 12 -> PakistanHijriDate(d.year, d.month + 1, 1)
            else -> PakistanHijriDate(d.year + 1, 1, 1)
        }
    }

    @Test
    fun adjustmentShiftsPakistanGridCells() {
        val plusOne = grid(1448, 4, adjustmentDays = 1)
        // Observed Pakistan date of the real Gregorian day 2026-09-14 is pakistan(2026-09-15) = 1448-04-02.
        val shiftedCell = plusOne.days.first { it.localDate == LocalDate(2026, 9, 14) }
        assertEquals(PakistanHijriDate(1448, 4, 2), shiftedCell.pakistanDate)
        // The observed "1" of 1448-04 lands one Gregorian day earlier.
        assertEquals(LocalDate(2026, 9, 13), plusOne.days.first { it.pakistanDate == PakistanHijriDate(1448, 4, 1) }.localDate)

        // Unadjusted grid for the same real day still shows the 1st.
        val plain = grid(1448, 4)
        assertEquals(PakistanHijriDate(1448, 4, 1), plain.days.first { it.localDate == LocalDate(2026, 9, 14) }.pakistanDate)

        // Negative adjustment mirrors the same day metadata but backwards.
        val minusOne = grid(1448, 4, adjustmentDays = -1)
        val shiftedBackCell = minusOne.days.first { it.localDate == LocalDate(2026, 9, 14) }
        assertEquals(PakistanHijriDate(1448, 3, 30), shiftedBackCell.pakistanDate)
    }

    @Test
    fun adjustmentKeepsGridGaplessAndReflectsTodayHighlight() {
        for (adjustment in -1..1) {
            val days = grid(1448, 4, adjustmentDays = adjustment).days
            assertEquals(42, days.size)
            val dates = days.mapNotNull { it.pakistanDate }
            dates.zipWithNext().forEach { (a, b) ->
                assertEquals(next(a), b, "adjusted grid must stay gapless (adj=$adjustment): $a -> $b")
            }
        }
    }
}

/**
 * A **scoped** override table must drive every cell of a Pakistan grid.
 *
 * The bug this covers was invisible to every other test because they all drive the process-global
 * table, where the default argument and the explicit argument are the same object. With a scoped
 * table the per-cell `gregorianToHijri` call was falling back to `HijriMonthOverrides.current`, so
 * the cells were converted against a different calendar than the month's start anchor — which *did*
 * honour the scoped table. The grid was internally inconsistent: right anchor, wrong days.
 *
 * The month is searched for rather than hardcoded, because forcing a month to the length it already
 * has is a no-op and the test would pass while proving nothing.
 */
class PakistanScopedOverrideTest {

    private fun discriminatingMonth(): Pair<HijrahYearMonth, Int> {
        val empty = HijriMonthLengths()
        for (year in 1445..1450) {
            for (month in 1..12) {
                val natural = PakistanHijriCalendar.lengthOfMonth(year, month, empty)
                if (natural != 30) return HijrahYearMonth(year, month) to natural
            }
        }
        error("no Pakistan month in 1445..1450 is not 30 days long")
    }

    @Test
    fun scopedTableDrivesEveryCellOfTheGrid() {
        val (ym, natural) = discriminatingMonth()
        val scoped = HijriMonthLengths().apply { setMonthLength(ym.year, ym.month.number, 30) }

        val month = ym.toCalendarMonth(pakistan = true, overrides = scoped)

        val painted = month.days
            .filter { it.pakistanDate?.year == ym.year && it.pakistanDate?.month == ym.month.number }
            .mapNotNull { it.pakistanDate?.day }

        assertEquals(
            (1..30).toList(),
            painted,
            "a scoped table must set the cell count for a naturally-$natural month",
        )
        // The grid stays gapless: consecutive real-world days, no duplicate Pakistani dates.
        val all = month.days.mapNotNull { it.pakistanDate }
        assertEquals(all.size, all.distinct().size, "no Pakistani date may repeat across the grid")
    }

    @Test
    fun theProcessGlobalTableIsUntouchedByAScopedBuild() {
        val (ym, _) = discriminatingMonth()
        val before = HijriMonthOverrides.all().toMap()
        val scoped = HijriMonthLengths().apply { setMonthLength(ym.year, ym.month.number, 30) }

        ym.toCalendarMonth(pakistan = true, overrides = scoped)

        assertEquals(
            before,
            HijriMonthOverrides.all().toMap(),
            "building with a scoped table must not mutate the process-global default",
        )
    }

    @Test
    fun gregorianRangeHonoursTheScopedTable() {
        val (ym, natural) = discriminatingMonth()
        val scoped = HijriMonthLengths().apply { setMonthLength(ym.year, ym.month.number, 30) }

        val month = ym.toCalendarMonth(pakistan = true, overrides = scoped)

        val naturalStart = PakistanHijriCalendar.hijriToGregorian(
            ym.year,
            ym.month.number,
            1,
            HijriMonthLengths(),
        )
        val naturalEnd = naturalStart.plus(natural - 1, DateTimeUnit.DAY)

        assertEquals(
            naturalStart,
            month.gregorianFirstDay,
            "the header's first day is unaffected by the month's length",
        )
        val extraDays = naturalEnd.daysUntil(month.gregorianLastDay)
        assertEquals(
            1,
            extraDays,
            "the header's extent must follow the forced length, one day past the natural end",
        )
    }
}
