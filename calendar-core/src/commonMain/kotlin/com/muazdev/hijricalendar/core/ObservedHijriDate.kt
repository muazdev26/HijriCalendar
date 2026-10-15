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
public data class ObservedHijriDate(
    val year: Int,
    val month: Int,
    val day: Int,
    val monthLength: Int,
    /**
     * The Gregorian day this observed date falls on, resolved against the override table that
     * produced it.
     *
     * Stored rather than derived because the derivation needs a [HijriMonthLengths] table, and
     * this is a value type that travels without one. Deriving it on demand from the
     * process-global [HijriMonthOverrides.current] would make a date produced under a *scoped*
     * table report the Gregorian day of a different calendar — silently wrong by the cumulative
     * drift of the two tables, which grows the further from the override you are.
     *
     * There is deliberately **no default**: a caller must name the Gregorian day, so it cannot
     * silently fall back to the process-global table. To build a value under a scoped table, go
     * through [ObservedHijriCalendar.observedDateAt] or [ObservedHijriCalendar.observedToGregorian],
     * both of which take the table.
     */
    val localDate: LocalDate,
) : Comparable<ObservedHijriDate> {

    override fun compareTo(other: ObservedHijriDate): Int =
        compareValuesBy(this, other, { it.year }, { it.month }, { it.day })

    override fun toString(): String =
        "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}
