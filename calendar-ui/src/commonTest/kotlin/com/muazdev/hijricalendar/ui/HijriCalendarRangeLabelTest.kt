package com.muazdev.hijricalendar.ui

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class HijriCalendarRangeLabelTest {

    private val labels = HijriCalendarDefaults.labels()

    // ── sameMonthRangeLabel ─────────────────────────────────────────────

    @Test
    fun sameMonthLabel_rendersMonthAndYear() {
        assertEquals(
            "September 2026",
            sameMonthRangeLabel(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30), labels),
        )
    }

    @Test
    fun sameYearLabel_rendersMonthRange() {
        assertEquals(
            "September - October 2026",
            sameMonthRangeLabel(LocalDate(2026, 9, 1), LocalDate(2026, 10, 1), labels),
        )
    }

    @Test
    fun crossYearLabel_rendersBothYears() {
        assertEquals(
            "December 2026 - January 2027",
            sameMonthRangeLabel(LocalDate(2026, 12, 1), LocalDate(2027, 1, 1), labels),
        )
    }

    @Test
    fun singleDayRange_sameMonth() {
        assertEquals(
            "June 2026",
            sameMonthRangeLabel(LocalDate(2026, 6, 15), LocalDate(2026, 6, 15), labels),
        )
    }

    // ── Gregorian month naming through default labels ───────────────────

    @Test
    fun defaultGregorianNames_coverAllMonths() {
        // Guards the DefaultGregorianMonthNames list against off-by-one (1-based month -> 0-based index).
        val january = labels.gregorianMonthName(1)
        val december = labels.gregorianMonthName(12)
        assertEquals("January", january)
        assertEquals("December", december)
    }
}