package com.muazdev.hijricalendar.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins [WeekDay]'s ordinal mapping.
 *
 * This exists because the ordinal is a **persisted** format, not an implementation detail: it is
 * written into Android Glance preferences and the iOS app group as
 * `WidgetOptions.firstDayOfWeekIndex`. `coerceIn` catches a value that is out of range, but
 * nothing can catch "ordinal 3 now names a different day" — it just silently changes the first
 * day of week on every already-placed widget.
 *
 * If this test fails, the enum was reordered or an entry was inserted. That is a schema change:
 * decode existing values through the old order before shipping, exactly as
 * `WidgetOptionsJson.decodeLegacyOrdinalJson` does for its own enum.
 */
class WeekDayOrdinalTest {

    @Test
    fun thePersistedOrdinalsAreWhatShippedWidgetsAlreadyContain() {
        assertEquals(
            listOf(
                WeekDay.SATURDAY,
                WeekDay.SUNDAY,
                WeekDay.MONDAY,
                WeekDay.TUESDAY,
                WeekDay.WEDNESDAY,
                WeekDay.THURSDAY,
                WeekDay.FRIDAY,
            ),
            WeekDay.entries,
        )

        assertEquals(0, WeekDay.SATURDAY.index)
        assertEquals(1, WeekDay.SUNDAY.index)
        assertEquals(2, WeekDay.MONDAY.index)
        assertEquals(3, WeekDay.TUESDAY.index)
        assertEquals(4, WeekDay.WEDNESDAY.index)
        assertEquals(5, WeekDay.THURSDAY.index)
        assertEquals(6, WeekDay.FRIDAY.index)
    }

    @Test
    fun fromIndexRoundTripsEveryDay() {
        WeekDay.entries.forEach { day ->
            assertEquals(day, WeekDay.fromIndex(day.index))
        }
    }

    @Test
    fun fromIndexRejectsOutOfRangeInsteadOfThrowing() {
        assertNull(WeekDay.fromIndex(-1))
        assertNull(WeekDay.fromIndex(7))
        assertNull(WeekDay.fromIndex(99))
    }

    @Test
    fun theOrderIsSaturdayFirstNotMondayFirst() {
        // kotlinx.datetime.DayOfWeek is Monday-first, so ordinals must never be crossed over.
        assertEquals(0, WeekDay.entries.first().index)
        assertEquals(WeekDay.SATURDAY, WeekDay.fromDayOfWeek(kotlinx.datetime.DayOfWeek.SATURDAY))
        assertEquals(WeekDay.MONDAY, WeekDay.fromDayOfWeek(kotlinx.datetime.DayOfWeek.MONDAY))
    }
}
