package com.muazdev.hijricalendar.core

import com.abdulrahman_b.hijrahdatetime.HijrahDate
import com.abdulrahman_b.hijrahdatetime.minusDays
import com.abdulrahman_b.hijrahdatetime.plusDays
import com.abdulrahman_b.hijrahdatetime.toHijrahDate
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun HijrahYearMonth.toCalendarMonth(
    firstDayOfWeek: WeekDay = WeekDay.DEFAULT_FIRST_DAY,
    selectedDate: HijrahDate? = null,
    minDate: HijrahDate? = null,
    maxDate: HijrahDate? = null,
): CalendarMonth {
    val today = todayHijriDate()
    val firstDayOfMonth = this.firstDay
    val firstDayDow = WeekDay.fromDayOfWeek(firstDayOfMonth.dayOfWeek)

    val leadingDaysCount = daysBefore(firstDayDow, firstDayOfWeek)
    val leadingDays = (1..leadingDaysCount).map { offset ->
        val date = firstDayOfMonth minusDays offset
        CalendarDay(
            hijrahDate = date,
            isCurrentMonth = false,
            isToday = date == today,
            isSelected = date == selectedDate,
            isDisabled = date.isDisabledByRange(minDate, maxDate),
            isWeekend = date.dayOfWeek.isWeekend(),
        )
    }.reversed()

    val daysInMonth = this.numberOfDays
    val currentMonthDays = (1..daysInMonth).map { day ->
        val date = this.onDay(day)
        CalendarDay(
            hijrahDate = date,
            isCurrentMonth = true,
            isToday = date == today,
            isSelected = date == selectedDate,
            isDisabled = date.isDisabledByRange(minDate, maxDate),
            isWeekend = date.dayOfWeek.isWeekend(),
        )
    }

    val trailingDaysCount = CalendarMonth.TOTAL_DAYS - leadingDaysCount - daysInMonth
    val lastDayOfMonth = this.lastDay
    val trailingDays = (1..trailingDaysCount).map { offset ->
        val date = lastDayOfMonth plusDays offset
        CalendarDay(
            hijrahDate = date,
            isCurrentMonth = false,
            isToday = date == today,
            isSelected = date == selectedDate,
            isDisabled = date.isDisabledByRange(minDate, maxDate),
            isWeekend = date.dayOfWeek.isWeekend(),
        )
    }

    return CalendarMonth(
        yearMonth = this,
        days = (leadingDays + currentMonthDays + trailingDays).toImmutableList(),
        firstDayOfWeek = firstDayOfWeek,
    )
}

private fun todayHijriDate(): HijrahDate {
    val now = kotlin.time.Clock.System.now()
    val localDate = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
    return localDate.toHijrahDate()
}

private fun daysBefore(actualFirstDay: WeekDay, desiredFirstDay: WeekDay): Int {
    val diff = actualFirstDay.index - desiredFirstDay.index
    return if (diff >= 0) diff else diff + CalendarMonth.DAYS_IN_WEEK
}

private fun HijrahDate.isDisabledByRange(min: HijrahDate?, max: HijrahDate?): Boolean {
    if (min != null && this < min) return true
    if (max != null && this > max) return true
    return false
}

private fun kotlinx.datetime.DayOfWeek.isWeekend(): Boolean {
    return this == kotlinx.datetime.DayOfWeek.FRIDAY || this == kotlinx.datetime.DayOfWeek.SATURDAY
}
