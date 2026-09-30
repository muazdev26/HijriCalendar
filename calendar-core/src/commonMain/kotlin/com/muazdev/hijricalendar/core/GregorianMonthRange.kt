package com.muazdev.hijricalendar.core

import androidx.compose.runtime.Immutable
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import com.abdulrahman_b.hijrahdatetime.toLocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * The Gregorian extent of one Hijri month: the real-world day its 1st falls on and the
 * real-world day its last day falls on.
 *
 * Both ends are already shifted for the calendar's [CalendarMonth.adjustmentDays], so a caller
 * displaying an adjusted calendar can use these directly. This is a *value* describing real
 * days, not a range to be interpreted further.
 */
@Immutable
public data class GregorianMonthRange(
    /** The Gregorian day the month's first day falls on. */
    val first: LocalDate,
    /** The Gregorian day the month's last day falls on. */
    val last: LocalDate,
) {
    /** Number of Gregorian days the month spans, always >= 1. */
    val lengthInDays: Int get() = last.toEpochDays().minus(first.toEpochDays()).toInt() + 1
}

/**
 * Resolves the Gregorian extent of Hijri [year]/[month] **in the active calendar space**.
 *
 * This is the single answer to "which real-world days does this month cover?", and it is the
 * only place the three calendar spaces are branched on for that question. It exists because the
 * answer genuinely differs per space and, before it existed, every caller re-derived it by hand:
 *
 * | Active space | First day | Last day |
 * |---|---|---|
 * | Umm al-Qura (default) | `HijrahYearMonth.firstDay` | `HijrahYearMonth.lastDay` |
 * | Observed (user overrides set) | `ObservedHijriCalendar.observedToGregorian(…, 1)` | `…, observedLength(…)` |
 * | Pakistan (Ruet-e-Hilal) | `PakistanHijriCalendar.hijriToGregorian(…, 1)` | `first + lengthOfMonth - 1` |
 *
 * The precedence is the same one grid generation uses ([toCalendarMonth]): Pakistan wins, then
 * observed overrides, then the plain calculation. Note that the *observed* space only differs
 * from the calculation when overrides exist — with an empty override table the two coincide, so
 * the `observed` branch is entered on `overrides.all().isNotEmpty()`.
 *
 * [adjustmentDays] is applied to both ends, matching how the grid is shifted on the Gregorian
 * timeline, so a positive value (dates observed ahead of the calculation) moves the range
 * *earlier*.
 *
 * [CalendarMonth] resolves its own range through this function at grid-generation time, so a
 * caller holding a [CalendarMonth] should prefer `calendarMonth.gregorianFirstDay` /
 * `gregorianLastDay` — they are guaranteed to describe the same space the grid was built in.
 * Reach for this function directly when you have only a year and month (a header that must not
 * force the 42-cell grid to be built, a settings preview, a month picker).
 *
 * @param year Hijri year.
 * @param month Hijri month, 1-12.
 * @param pakistan true to resolve in Pakistan (Ruet-e-Hilal) space rather than Umm al-Qura.
 * @param adjustmentDays moon-sighting shift already applied to the grid; subtracted from both ends.
 * @throws IllegalArgumentException if [year] is outside the Pakistan calendar's supported
 *   range and [pakistan] is true.
 */
public fun resolveGregorianMonthRange(
    year: Int,
    month: Int,
    pakistan: Boolean = false,
    adjustmentDays: Int = 0,
    overrides: HijriMonthLengths = HijriMonthOverrides.current,
): GregorianMonthRange {
    val first: LocalDate
    val last: LocalDate
    when {
        pakistan -> {
            first = PakistanHijriCalendar.hijriToGregorian(year, month, 1, overrides)
            last = first.plus(
                PakistanHijriCalendar.lengthOfMonth(year, month, overrides) - 1,
                DateTimeUnit.DAY,
            )
        }

        overrides.all().isNotEmpty() -> {
            first = ObservedHijriCalendar.observedToGregorian(year, month, 1, overrides)
            last = ObservedHijriCalendar.observedToGregorian(
                year,
                month,
                ObservedHijriCalendar.observedLength(year, month, overrides),
                overrides,
            )
        }

        else -> {
            val yearMonth = HijrahYearMonth(year, month)
            first = yearMonth.firstDay.toLocalDate()
            last = yearMonth.lastDay.toLocalDate()
        }
    }
    return GregorianMonthRange(
        first = first.minus(adjustmentDays, DateTimeUnit.DAY),
        last = last.minus(adjustmentDays, DateTimeUnit.DAY),
    )
}
