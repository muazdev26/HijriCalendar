package com.muazdev.hijricalendar.core

import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import kotlinx.datetime.LocalDate
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

    private fun grid(year: Int, month: Int): CalendarMonth =
        HijrahYearMonth(year, month).toCalendarMonth(pakistan = true)

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
}