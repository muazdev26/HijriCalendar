package com.muazdev.hijricalendar.core

import androidx.compose.runtime.Immutable
import com.abdulrahman_b.hijrahdatetime.HijrahDate
import com.abdulrahman_b.hijrahdatetime.toLocalDate
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import kotlinx.collections.immutable.ImmutableList
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

@Immutable
data class CalendarMonth(
    val yearMonth: HijrahYearMonth,
    val days: ImmutableList<CalendarDay>,
    val firstDayOfWeek: WeekDay,
    val adjustmentDays: Int = 0,
) {
    val numberOfWeeks: Int get() = days.size / DAYS_IN_WEEK

    val monthName: String get() = yearMonth.month.name

    val year: Int get() = yearMonth.year

    val firstDay: HijrahDate get() = yearMonth.firstDay

    val lastDay: HijrahDate get() = yearMonth.lastDay

    val gregorianFirstDay: LocalDate get() = firstDay.toLocalDate().minus(adjustmentDays, DateTimeUnit.DAY)

    val gregorianLastDay: LocalDate get() = lastDay.toLocalDate().minus(adjustmentDays, DateTimeUnit.DAY)

    @Deprecated(
        message = "Hard-coded English formatting. Use HijriCalendarLabels.gregorianMonthName " +
            "in calendar-ui to build a localized range.",
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

    companion object {
        const val DAYS_IN_WEEK = 7
        const val WEEKS_IN_MONTH = 6

        const val TOTAL_DAYS = DAYS_IN_WEEK * WEEKS_IN_MONTH
    }
}
