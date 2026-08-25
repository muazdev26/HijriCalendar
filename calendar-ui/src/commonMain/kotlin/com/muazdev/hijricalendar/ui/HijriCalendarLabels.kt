package com.muazdev.hijricalendar.ui

import androidx.compose.runtime.Immutable
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import com.muazdev.hijricalendar.core.WeekDay

private val DefaultHijriMonthNames = listOf(
    "Muharram", "Safar", "Rabi al-Awwal", "Rabi al-Thani",
    "Jumada al-Ula", "Jumada al-Akhirah", "Rajab", "Shaban",
    "Ramadan", "Shawwal", "Dhu al-Qadah", "Dhu al-Hijjah",
)

private val DefaultGregorianMonthNames = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

/**
 * User-visible text of [HijriCalendar]. All fields have defaults reproducing the
 * built-in English output, so passing nothing keeps current behavior. Supply your own
 * lambdas/strings to localize (e.g. Urdu, Arabic).
 */
@Immutable
data class HijriCalendarLabels(
    /** Returns the display name of the given Hijri month (`month` is 1-12). */
    val hijriMonthName: (year: Int, month: Int) -> String = { _, month ->
        DefaultHijriMonthNames[month - 1]
    },
    /** Returns the display name of the given Gregorian month (`monthNumber` is 1-12). */
    val gregorianMonthName: (monthNumber: Int) -> String = { month ->
        DefaultGregorianMonthNames[month - 1]
    },
    /** Returns the short column label for a weekday header cell. */
    val weekdayShortName: (weekDay: WeekDay) -> String = { it.shortName },
    val previousMonthContentDescription: String = "Previous month",
    val nextMonthContentDescription: String = "Next month",
)
