package com.muazdev.hijricalendar.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UrduCalendarNamesTest {

    @Test
    fun hijriMonths_hasTwelveNames() {
        assertEquals(12, UrduCalendarNames.hijriMonths.size)
        assertEquals("محرم", UrduCalendarNames.hijriMonths.first())
        assertEquals("ذی الحجہ", UrduCalendarNames.hijriMonths.last())
    }

    @Test
    fun gregorianMonths_hasTwelveNames() {
        assertEquals(12, UrduCalendarNames.gregorianMonths.size)
        assertEquals("جنوری", UrduCalendarNames.gregorianMonths.first())
        assertEquals("دسمبر", UrduCalendarNames.gregorianMonths.last())
    }

    @Test
    fun weekdays_coversAllWeekDayValues() {
        assertEquals(WeekDay.entries.size, UrduCalendarNames.weekdays.size)
        assertTrue(WeekDay.entries.all { it in UrduCalendarNames.weekdays })
        assertTrue(UrduCalendarNames.weekdays.values.all { it.isNotEmpty() })
    }

    @Test
    fun weekdayShortNames_matchWeekDayEnumOrder() {
        assertEquals(WeekDay.entries.map { UrduCalendarNames.weekdays.getValue(it) }, UrduCalendarNames.weekdayShortNames)
        // Saturday-first order, matching the enum.
        assertEquals("ہفتہ", UrduCalendarNames.weekdayShortNames.first())
        assertEquals("جمعہ", UrduCalendarNames.weekdayShortNames.last())
    }
}