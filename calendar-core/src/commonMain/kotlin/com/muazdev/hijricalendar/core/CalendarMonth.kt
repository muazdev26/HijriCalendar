package com.muazdev.hijricalendar.core

import androidx.compose.runtime.Immutable
import com.abdulrahman_b.hijrahdatetime.HijrahDate
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import kotlinx.collections.immutable.ImmutableList
import kotlinx.datetime.LocalDate

/**
 * A rendered 6-week grid for one Hijri month.
 *
 * @param yearMonth The Umm al-Qura month identity. This is always the *calculated* month — the
 *   Pakistan and observed spaces reuse it as their month label, so `yearMonth` alone never
 *   implies which space the cells are in.
 * @param days The 42 cells, `TOTAL_DAYS` in length, in reading order from the first day of week.
 * @param firstDayOfWeek The weekday the grid's first column starts on.
 * @param adjustmentDays Moon-sighting shift applied to the whole grid on the Gregorian timeline.
 * @param gregorianRange The month's real-world extent, resolved **in the same space the cells
 *   were generated in**. Set by [toCalendarMonth]; when null (a hand-constructed instance, e.g. a
 *   preview) it is resolved on demand in the plain Umm al-Qura space. Do not read it directly —
 *   use [gregorianFirstDay] / [gregorianLastDay], which fall back correctly.
 */
@Immutable
public data class CalendarMonth(
    val yearMonth: HijrahYearMonth,
    val days: ImmutableList<CalendarDay>,
    val firstDayOfWeek: WeekDay,
    val adjustmentDays: Int = 0,
    val gregorianRange: GregorianMonthRange? = null,
) {
    val numberOfWeeks: Int get() = days.size / DAYS_IN_WEEK

    val year: Int get() = yearMonth.year

    val firstDay: HijrahDate get() = yearMonth.firstDay

    val lastDay: HijrahDate get() = yearMonth.lastDay

    /**
     * The Gregorian extent of this month, resolved in the space its cells were generated in.
     *
     * When [gregorianRange] is null (a hand-constructed instance) this resolves in the plain Umm
     * al-Qura space, which is correct only when no overrides are set and Pakistan mode is off.
     * That fallback exists for previews and tests; anything rendering real data should use a
     * [CalendarMonth] from [toCalendarMonth].
     */
    val resolvedGregorianRange: GregorianMonthRange
        get() = gregorianRange
            ?: resolveGregorianMonthRange(
                year = year,
                month = yearMonth.month.number,
                adjustmentDays = adjustmentDays,
            )

    /**
     * The Gregorian day this month's first day falls on, in the active calendar space.
     * See [resolvedGregorianRange] for which space that is.
     */
    val gregorianFirstDay: LocalDate get() = resolvedGregorianRange.first

    /**
     * The Gregorian day this month's last day falls on, in the active calendar space.
     * See [resolvedGregorianRange] for which space that is.
     */
    val gregorianLastDay: LocalDate get() = resolvedGregorianRange.last

    @Deprecated(
        message = "Hard-coded English formatting, and it always describes the Umm al-Qura extent " +
            "even when the grid was built in Pakistan or observed space. Use " +
            "resolvedGregorianRange with your own localizer instead.",
    )
    val gregorianMonthRange: String
        get() {
            val first = gregorianFirstDay
            val last = gregorianLastDay
            return if (first.month == last.month && first.year == last.year) {
                "${first.month.name} ${first.year}"
            } else if (first.year == last.year) {
                "${first.month.name} - ${last.month.name} ${first.year}"
            } else {
                "${first.month.name} ${first.year} - ${last.month.name} ${last.year}"
            }
        }

    public companion object {
        public const val DAYS_IN_WEEK: Int = 7
        public const val WEEKS_IN_MONTH: Int = 6

        /** 42 — every grid is exactly six weeks, so a month is never more than 42 cells. */
        public const val TOTAL_DAYS: Int = DAYS_IN_WEEK * WEEKS_IN_MONTH
    }
}
