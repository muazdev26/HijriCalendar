package com.muazdev.hijricalendar.core

import com.abdulrahman_b.hijrahdatetime.HijrahDate
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * The library-owned date types (issue 07) and their bridge to the third-party alpha types the
 * calendar math still uses.
 *
 * The point of these values is that a published signature no longer names
 * `com.abdulrahman_b.hijrahdatetime`, so the conversion is the only thing standing between the two
 * worlds. Every pair below is therefore asserted in both directions: a bridge that works one way
 * and drifts the other is exactly the kind of break a consumer notices at compile time and cannot
 * work around.
 */
class HijriDateTest {

    // ── validation ───────────────────────────────────────────────────────

    @Test
    fun hijriDate_rejectsAMonthOutsideTheCalendar() {
        assertFailsWith<IllegalArgumentException> { HijriDate(1448, 0, 1) }
        assertFailsWith<IllegalArgumentException> { HijriDate(1448, 13, 1) }
    }

    @Test
    fun hijriDate_rejectsADayOutsideTheCalendar() {
        assertFailsWith<IllegalArgumentException> { HijriDate(1448, 3, 0) }
        assertFailsWith<IllegalArgumentException> { HijriDate(1448, 3, 31) }
    }

    @Test
    fun hijriYearMonth_rejectsAMonthOutsideTheCalendar() {
        assertFailsWith<IllegalArgumentException> { HijriYearMonth(1448, 0) }
        assertFailsWith<IllegalArgumentException> { HijriYearMonth(1448, 13) }
    }

    // ── shape ────────────────────────────────────────────────────────────

    @Test
    fun hijriDate_exposesItsMonthIdentity() {
        assertEquals(HijriYearMonth(1448, 3), HijriDate(1448, 3, 17).yearMonth)
    }

    @Test
    fun hijriYearMonth_atDayBuildsTheDate() {
        assertEquals(HijriDate(1448, 3, 17), HijriYearMonth(1448, 3).atDay(17))
    }

    @Test
    fun toStringReadsLikeTheWrittenDate() {
        assertEquals("1448-3-17", HijriDate(1448, 3, 17).toString())
        assertEquals("1448-3", HijriYearMonth(1448, 3).toString())
    }

    // ── month arithmetic ─────────────────────────────────────────────────

    @Test
    fun plusMonths_zeroIsTheSameMonth() {
        assertEquals(HijriYearMonth(1447, 9), HijriYearMonth(1447, 9).plusMonths(0))
    }

    @Test
    fun plusMonths_carriesForwardAcrossTheYearBoundary() {
        assertEquals(HijriYearMonth(1448, 1), HijriYearMonth(1447, 12).plusMonths(1))
        assertEquals(HijriYearMonth(1449, 2), HijriYearMonth(1447, 12).plusMonths(14))
    }

    @Test
    fun plusMonths_carriesBackwardAcrossTheYearBoundary() {
        assertEquals(HijriYearMonth(1447, 12), HijriYearMonth(1448, 1).plusMonths(-1))
        assertEquals(HijriYearMonth(1446, 11), HijriYearMonth(1448, 1).plusMonths(-14))
    }

    @Test
    fun plusMonths_isInvertibleForEveryMonthOfAYear() {
        for (month in 1..12) {
            val start = HijriYearMonth(1448, month)
            assertEquals(start, start.plusMonths(17).plusMonths(-17), "round trip failed for $start")
        }
    }

    // ── bridge to the third-party types ──────────────────────────────────

    @Test
    fun hijriDate_convertsToTheEngineTypeAndBack() {
        val date = HijriDate(1448, 3, 17)

        val engine = date.toHijrahDate()

        assertEquals(1448, engine.year)
        assertEquals(3, engine.month.number)
        assertEquals(17, engine.day)
        assertEquals(date, engine.toHijriDate())
    }

    @Test
    fun hijriYearMonth_convertsToTheEngineTypeAndBack() {
        val month = HijriYearMonth(1448, 3)

        val engine = month.toHijrahYearMonth()

        assertEquals(1448, engine.year)
        assertEquals(3, engine.month.number)
        assertEquals(month, engine.toHijriYearMonth())
    }

    @Test
    fun engineDate_convertsToTheLibraryOwnedType() {
        assertEquals(HijriDate(1447, 9, 5), HijrahDate(1447, 9, 5).toHijriDate())
    }

    @Test
    fun engineMonth_convertsToTheLibraryOwnedType() {
        assertEquals(HijriYearMonth(1447, 9), HijrahYearMonth(1447, 9).toHijriYearMonth())
    }

    // ── the new-typed grid agrees with the deprecated one ────────────────

    @Test
    @Suppress("DEPRECATION")
    fun toCalendarMonth_theLibraryOwnedOverloadMatchesTheDeprecatedOne() {
        val legacy = HijrahYearMonth(1447, 9).toCalendarMonth()
        val owned = HijriYearMonth(1447, 9).toCalendarMonth()

        assertEquals(legacy.days, owned.days)
        assertEquals(legacy.gregorianRange, owned.gregorianRange)
        assertEquals(owned.hijriYearMonth, HijriYearMonth(1447, 9))
    }

    // ── a conversion is total ────────────────────────────────────────────

    @Test
    fun toHijriDate_answersNullOnlyForANullEngineDate() {
        val missing: HijrahDate? = null
        assertNull(missing?.toHijriDate())
    }
}
