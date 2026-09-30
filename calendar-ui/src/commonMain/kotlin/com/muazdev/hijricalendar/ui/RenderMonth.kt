package com.muazdev.hijricalendar.ui

import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import com.muazdev.hijricalendar.core.CalendarMonth
import com.muazdev.hijricalendar.core.GregorianMonthRange
import com.muazdev.hijricalendar.core.HijriCalendarState
import com.muazdev.hijricalendar.core.resolveGregorianMonthRange
import com.muazdev.hijricalendar.core.toCalendarMonth

/**
 * The render path's single source of truth for "what does this month look like".
 *
 * Both entry points below are the **only** places in `calendar-ui` that turn a year/month plus
 * this state's configuration into calendar data. They exist because the alternative — each
 * composable calling `toCalendarMonth` / `resolveGregorianMonthRange` with its own hand-copied
 * argument list — is what let [HijriCalendarState.monthLengths] go unread for an entire release:
 * every one of those argument lists omitted `overrides`, so the grid painted the process-global
 * month lengths while the state reported the scoped ones.
 *
 * Do not inline these calls back into a composable. A new input to the underlying builder must be
 * added *here*, once, and the state must be read from *this* object — not re-derived from a
 * `remember` key list beside the call.
 *
 * @see UI-01-scoped-overrides-ignored for the defect these close.
 * @see UI-03-one-month-builder for the larger refactor that supersedes this seam.
 */

/**
 * The month grid the user sees for [yearMonth], in this state's calendar space and configuration.
 *
 * This must agree with [HijriCalendarState.calendarMonth] for the state's current month, and it
 * must use this state's [HijriCalendarState.monthLengths] rather than the process default.
 */
internal fun HijriCalendarState.renderMonthFor(yearMonth: HijrahYearMonth): CalendarMonth =
    yearMonth.toCalendarMonth(
        firstDayOfWeek = firstDayOfWeek,
        selectedDate = selectedDate,
        selectedPakistanDate = selectedPakistanDate,
        selectedObservedDate = selectedObservedDate,
        minDate = minDate,
        maxDate = maxDate,
        adjustmentDays = adjustmentDays,
        pakistan = pakistanDates,
        weekendDays = weekendDays,
        overrides = monthLengths,
    )

/**
 * The real-world Gregorian extent of [yearMonth] as the header describes it.
 *
 * Resolved without building the 42-cell grid, so the header can be recomposed on its own. Must
 * return the same extent [renderMonthFor] implies, or the header names a different month than the
 * grid below it.
 */
internal fun HijriCalendarState.renderGregorianRangeFor(
    yearMonth: HijrahYearMonth,
): GregorianMonthRange = resolveGregorianMonthRange(
    year = yearMonth.year,
    month = yearMonth.month.number,
    pakistan = pakistanDates,
    adjustmentDays = adjustmentDays,
    overrides = monthLengths,
)
