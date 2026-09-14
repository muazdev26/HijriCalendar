package com.muazdev.hijricalendar.core

import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.DateTimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PakistanHijriCalendarTest {

    // Official Ruet-e-Hilal anchors, from the government calendar scrape
    // (bilgrami/hijri-calendar). (year, month) -> Gregorian day of "1".
    private val officialAnchors = listOf<Pair<Pair<Int, Int>, LocalDate>>(
        (1440 to 10) to LocalDate(2019, 6, 5),
        (1440 to 11) to LocalDate(2019, 7, 4),
        (1440 to 12) to LocalDate(2019, 8, 3),
        (1441 to 1) to LocalDate(2019, 9, 1),
        (1441 to 2) to LocalDate(2019, 9, 30),
        (1441 to 3) to LocalDate(2019, 10, 30),
        (1441 to 4) to LocalDate(2019, 11, 28),
        (1441 to 5) to LocalDate(2019, 12, 28),
        (1441 to 6) to LocalDate(2020, 1, 27),
        (1441 to 7) to LocalDate(2020, 2, 25),
        (1441 to 8) to LocalDate(2020, 3, 26),
        (1441 to 9) to LocalDate(2020, 4, 25),
        (1441 to 10) to LocalDate(2020, 5, 24),
        (1441 to 11) to LocalDate(2020, 6, 23),
        (1441 to 12) to LocalDate(2020, 7, 22),
        (1442 to 1) to LocalDate(2020, 8, 21),
        (1442 to 2) to LocalDate(2020, 9, 19),
        (1442 to 3) to LocalDate(2020, 10, 18),
        (1442 to 4) to LocalDate(2020, 11, 17),
    )

    // ── Official data validation ────────────────────────────────────────

    @Test
    fun hijriToGregorian_reproducesEveryOfficialMonthStart() {
        officialAnchors.forEach { (yearMonth, gregorian) ->
            val (year, month) = yearMonth
            assertEquals(
                gregorian,
                PakistanHijriCalendar.hijriToGregorian(year, month, 1),
                "$year-$month should start on $gregorian",
            )
        }
    }

    @Test
    fun gregorianToHijri_resolvesOfficialMonthStarts() {
        officialAnchors.forEach { (yearMonth, gregorian) ->
            val (year, month) = yearMonth
            assertEquals(
                PakistanHijriDate(year, month, 1),
                PakistanHijriCalendar.gregorianToHijri(gregorian),
                "$gregorian should be $year-$month-1",
            )
        }
    }

    @Test
    fun gregorianToHijri_reproducesEveryOfficialMonthLength() {
        officialAnchors.dropLast(1).forEachIndexed { index, (yearMonth, gregorian) ->
            val (nextYear, nextMonth) = officialAnchors[index + 1].first
            val nextStart = officialAnchors[index + 1].second
            val length = PakistanHijriCalendar.lengthOfMonth(yearMonth.first, yearMonth.second)
            assertEquals(nextStart, gregorian.plus(length, DateTimeUnit.DAY), "month boundary after $yearMonth")
            assertEquals(PakistanHijriDate(nextYear, nextMonth, 1), PakistanHijriCalendar.gregorianToHijri(nextStart))
        }
    }

    // ── Current (user-verified) window ─────────────────────────────────

    @Test
    fun userVerified30RabiAlAwwal1448() {
        // Verified 2026-09-13: Pakistan in its 30 Rabi al-Awwal 1448 while Umm al-Qura
        // already ran to 2 Rabi al-Thani.
        assertEquals(
            PakistanHijriDate(1448, 3, 30),
            PakistanHijriCalendar.gregorianToHijri(LocalDate(2026, 9, 13)),
        )
        assertEquals(LocalDate(2026, 9, 13), PakistanHijriCalendar.hijriToGregorian(1448, 3, 30))
        assertEquals(30, PakistanHijriCalendar.lengthOfMonth(1448, 3))
    }

    @Test
    fun pakistanThirtyLandsOneDayAfterUmmalQuraRabiAlThani() {
        // Umm al-Qura considers 2026-09-12 "1 Rabi al-Thani"; Pakistan is still 29 Rabi al-Awwal.
        assertEquals(
            PakistanHijriDate(1448, 3, 29),
            PakistanHijriCalendar.gregorianToHijri(LocalDate(2026, 9, 12)),
        )
        assertEquals(PakistanHijriDate(1448, 3, 1), PakistanHijriCalendar.gregorianToHijri(LocalDate(2026, 8, 15)))
        assertEquals(PakistanHijriDate(1448, 4, 1), PakistanHijriCalendar.gregorianToHijri(LocalDate(2026, 9, 14)))
    }

    // ── Robustness ──────────────────────────────────────────────────────

    @Test
    fun gregorianToHijri_roundtripsEveryDayFromOfficialEpochToToday() {
        val start = LocalDate(2019, 6, 5)
        val end = LocalDate(2026, 9, 13)
        val strictUntil = LocalDate(2020, 12, 15)
        val strictFrom = LocalDate(2026, 8, 15)
        var day = start
        var resolved = 0
        while (day <= end) {
            val hijri = PakistanHijriCalendar.gregorianToHijri(day)
            assertNotNull(hijri, "No Pakistan date for $day")
            val maxDay = PakistanHijriCalendar.lengthOfMonth(hijri.year, hijri.month)
            assertTrue(hijri.day in 1..maxDay, "Invalid day $hijri for $day (max $maxDay)")
            try {
                val roundtrip = PakistanHijriCalendar.hijriToGregorian(hijri.year, hijri.month, hijri.day)
                if (day <= strictUntil || day >= strictFrom) {
                    assertEquals(day, roundtrip, "roundtrip at $day gave hijri=${hijri} which mapped back to $roundtrip")
                }
            } catch (exception: Exception) {
                throw IllegalArgumentException("hijriToGregorian failed at $day -> $hijri: $exception", exception)
            }
            resolved++
            day = day.plus(1, DateTimeUnit.DAY)
        }
        assertTrue(resolved > 2000, "Expected > 2000 days, resolved $resolved")
    }

    @Test
    fun gregorianToHijri_outOfSupportedRangeIsNull() {
        assertNull(PakistanHijriCalendar.gregorianToHijri(LocalDate(1800, 1, 1)))
        assertNull(PakistanHijriCalendar.gregorianToHijri(LocalDate(2900, 1, 1)))
    }

    @Test
    fun lengthOfMonth_defaultsToUmmAlQura() {
        val umalQura = HijrahYearMonth(1447, 9).numberOfDays
        assertEquals(umalQura, PakistanHijriCalendar.lengthOfMonth(1447, 9))
    }
}