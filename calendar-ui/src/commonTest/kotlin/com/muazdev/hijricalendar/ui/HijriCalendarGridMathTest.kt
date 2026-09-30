package com.muazdev.hijricalendar.ui

import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

class HijriCalendarGridMathTest {

    // ── monthOffset ─────────────────────────────────────────────────────

    @Test
    fun monthOffset_sameMonthIsZero() {
        assertEquals(0, monthOffset(HijrahYearMonth(1447, 9), HijrahYearMonth(1447, 9)))
    }

    @Test
    fun monthOffset_nextMonthIsOne() {
        assertEquals(1, monthOffset(HijrahYearMonth(1447, 10), HijrahYearMonth(1447, 9)))
    }

    @Test
    fun monthOffset_previousMonthIsMinusOne() {
        assertEquals(-1, monthOffset(HijrahYearMonth(1447, 8), HijrahYearMonth(1447, 9)))
    }

    @Test
    fun monthOffset_crossesYearBoundary() {
        assertEquals(1, monthOffset(HijrahYearMonth(1448, 1), HijrahYearMonth(1447, 12)))
        assertEquals(-1, monthOffset(HijrahYearMonth(1446, 12), HijrahYearMonth(1447, 1)))
    }

    @Test
    fun monthOffset_spansMultipleMonths() {
        assertEquals(4, monthOffset(HijrahYearMonth(1448, 1), HijrahYearMonth(1447, 9)))
        assertEquals(-15, monthOffset(HijrahYearMonth(1447, 9), HijrahYearMonth(1448, 12)))
    }

    // ── plusPageOffset ──────────────────────────────────────────────────

    @Test
    fun plusPageOffset_zeroKeepsMonth() {
        assertEquals(
            HijrahYearMonth(1447, 9),
            HijrahYearMonth(1447, 9).plusPageOffset(0),
        )
    }

    @Test
    fun plusPageOffset_positiveAdvancesMonths() {
        assertEquals(
            HijrahYearMonth(1447, 10),
            HijrahYearMonth(1447, 9).plusPageOffset(1),
        )
        assertEquals(
            HijrahYearMonth(1448, 1),
            HijrahYearMonth(1447, 12).plusPageOffset(1),
        )
    }

    @Test
    fun plusPageOffset_negativeGoesBack() {
        assertEquals(
            HijrahYearMonth(1447, 8),
            HijrahYearMonth(1447, 9).plusPageOffset(-1),
        )
        assertEquals(
            HijrahYearMonth(1446, 12),
            HijrahYearMonth(1447, 1).plusPageOffset(-1),
        )
    }

    @Test
    fun plusPageOffset_spansMultipleMonths() {
        assertEquals(
            HijrahYearMonth(1448, 3),
            HijrahYearMonth(1447, 9).plusPageOffset(6),
        )
        assertEquals(
            HijrahYearMonth(1447, 3),
            HijrahYearMonth(1447, 9).plusPageOffset(-6),
        )
    }

    // ── Pager constants ─────────────────────────────────────────────────
    // Replaced the former `pagerWindow_hasOddPageCountCentered`, which asserted
    // PAGER_CENTER_PAGE == 500 and PAGER_PAGE_COUNT % 2 == 1 — a restatement of a `const val`
    // declaration, in a test named as though it verified a property of the window. Pinning the
    // magic number is part of why the magic number survived. Both constants are gone: the window
    // is now derived from the caller's bounds and the Hijri table's own edges. Real coverage of
    // that, including the old 500-month crash threshold, is in PageWindowTest.
}