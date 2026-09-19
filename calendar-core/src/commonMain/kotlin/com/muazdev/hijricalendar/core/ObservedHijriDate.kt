package com.muazdev.hijricalendar.core

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * A Hijri date that may represent a day beyond a month's Umm al-Qura calculated length
 * (e.g. a forced 30th in a 29-day month).
 *
 * The [day] field is the raw observed day number; [monthLength] is the effective length
 * of the month (after overrides have been applied). Outside the valid range for
 * [ObservedHijriCalendar], [day] may be clamped to [monthLength].
 */
@Serializable
data class ObservedHijriDate(
    val year: Int,
    val month: Int,
    val day: Int,
    val monthLength: Int,
) : Comparable<ObservedHijriDate> {

    /** The Gregorian day this observed date falls on. */
    val localDate: LocalDate
        get() = ObservedHijriCalendar.observedToGregorian(year, month, day)

    override fun compareTo(other: ObservedHijriDate): Int =
        compareValuesBy(this, other, { it.year }, { it.month }, { it.day })

    override fun toString(): String =
        "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}