package com.muazdev.hijricalendar.ui

import androidx.compose.runtime.Immutable
import com.muazdev.hijricalendar.core.CalendarDay
import com.muazdev.hijricalendar.core.CalendarNames
import com.muazdev.hijricalendar.core.WeekDay

/**
 * User-visible text of [HijriCalendar]. All fields have defaults reproducing the
 * built-in English output, so passing nothing keeps current behavior. Supply your own
 * lambdas/strings to localize (e.g. Urdu, Arabic).
 *
 * The defaults come from [CalendarNames], which is the same source `calendar-widget-data` uses,
 * so an in-app header and a placed widget cannot show different month names.
 *
 * **Construct this once and hold it.** [HijriCalendar] uses `labels` as a `remember` key at three
 * sites, and the four function fields compare by reference, so a `HijriCalendarLabels(...)` built
 * inline inside a composable is a new object on every recomposition and discards all three caches.
 * Pass a `val` held by the caller, or `remember` it.
 *
 * Related: a label lambda that *captures* changing state keeps a stable identity while its capture
 * changes, so this object can compare equal and still render a stale month name. Read the capture
 * through state inside the composable that uses the label, or supply a table-driven implementation
 * instead of a lambda.
 */
@Immutable
public data class HijriCalendarLabels(
    /** Returns the display name of the given Hijri month (`month` is 1-12). */
    val hijriMonthName: (year: Int, month: Int) -> String = { _, month ->
        CalendarNames.englishHijriMonths[month - 1]
    },
    /** Returns the display name of the given Gregorian month (`monthNumber` is 1-12). */
    val gregorianMonthName: (monthNumber: Int) -> String = { monthNumber ->
        CalendarNames.englishGregorianMonths[monthNumber - 1]
    },
    /** Returns the short column label for a weekday header cell. */
    val weekdayShortName: (weekDay: WeekDay) -> String = { it.shortName },
    val previousMonthContentDescription: String = "Previous month",
    val nextMonthContentDescription: String = "Next month",
    /**
     * Returns the accessibility content description for a single day cell. The default
     * reproduces the original built-in English output; pass a [CalendarDay]-aware lambda
     * to localize or to disambiguate leading/trailing days from adjacent months.
     */
    val dayContentDescription: (CalendarDay) -> String = { day ->
        val suffix = if (day.isDisabled) ", disabled" else ""
        "Day ${day.dayOfMonth}$suffix"
    },
)
