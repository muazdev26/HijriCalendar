package com.muazdev.hijricalendar.core

import com.abdulrahman_b.hijrahdatetime.HijrahDate
import com.abdulrahman_b.hijrahdatetime.toHijrahDate
import com.abdulrahman_b.hijrahdatetime.toLocalDate
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Generates the 6-week grid for this Hijri month, optionally compensating for local
 * moon-sighting differences from the Umm al-Qura calculation.
 *
 * The observed Hijri date of a Gregorian day [G] is defined as
 * `(G + adjustmentDays).toHijrahDate()`, i.e. the whole grid is shifted by
 * [adjustmentDays] on the Gregorian timeline. Everything is derived from that single
 * shift: each cell's [CalendarDay.hijrahDate]/day number, the weekday column alignment,
 * `isToday`, selection and month boundaries (a shifted day may belong to the previous
 * or next Hijri month).
 *
 * With [pakistan] = true the grid is instead generated from the [PakistanHijriCalendar]
 * Ruet-e-Hilal table: each cell's [CalendarDay.pakistanDate] is the true Pakistani date
 * of that Gregorian day, and [adjustmentDays] is ignored (the table is already corrected).
 * Selection and today highlight compare in that space.
 *
 * Cells whose shifted date falls outside the supported Umm al-Qura range (~1300-1600 AH)
 * do not throw: they become disabled placeholders clamped to [HijrahDate.MIN] or
 * [HijrahDate.MAX].
 */
fun HijrahYearMonth.toCalendarMonth(
    firstDayOfWeek: WeekDay = WeekDay.DEFAULT_FIRST_DAY,
    selectedDate: HijrahDate? = null,
    selectedPakistanDate: PakistanHijriDate? = null,
    selectedObservedDate: ObservedHijriDate? = null,
    minDate: HijrahDate? = null,
    maxDate: HijrahDate? = null,
    adjustmentDays: Int = 0,
    pakistan: Boolean = false,
    weekendDays: Set<WeekDay> = WeekDay.WEEKEND_DAYS,
): CalendarMonth {
    if (pakistan) {
        return toPakistanCalendarMonth(
            firstDayOfWeek = firstDayOfWeek,
            selectedDate = selectedPakistanDate,
            adjustmentDays = adjustmentDays,
            weekendDays = weekendDays,
        )
    }

    // User month-length overrides turn the Umm al-Qura grid into the observed calendar.
    if (HijriMonthOverrides.all().isNotEmpty()) {
        return toObservedCalendarMonth(
            firstDayOfWeek = firstDayOfWeek,
            selectedDate = selectedObservedDate,
            adjustmentDays = adjustmentDays,
            weekendDays = weekendDays,
        )
    }

    val today = todayHijriDate(adjustmentDays)

    // Real-world Gregorian day the observed "1" of this month falls on.
    val monthStartAnchor = firstDay.toLocalDate().minus(adjustmentDays, DateTimeUnit.DAY)
    val firstCellDow = WeekDay.fromDayOfWeek(monthStartAnchor.dayOfWeek)
    val leadingDaysCount = daysBefore(firstCellDow, firstDayOfWeek)
    val gridStart = monthStartAnchor.minus(leadingDaysCount, DateTimeUnit.DAY)

    val days = (0 until CalendarMonth.TOTAL_DAYS).map { offset ->
        val anchor = gridStart.plus(offset, DateTimeUnit.DAY)
        val shifted = anchor.plus(adjustmentDays, DateTimeUnit.DAY)
        val converted = shifted.toHijrahDateOrNull()

        if (converted != null) {
            CalendarDay(
                hijrahDate = converted,
                isCurrentMonth = converted.year == year && converted.month == month,
                isToday = converted == today,
                isSelected = converted == selectedDate,
                isDisabled = converted.isDisabledByRange(minDate, maxDate),
                isWeekend = WeekDay.fromDayOfWeek(anchor.dayOfWeek) in weekendDays,
                adjustmentDays = adjustmentDays,
            )
        } else {
            // Out of the supported Umm al-Qura range: clamp instead of crashing.
            val clamped = if (shifted < HijrahDate.MIN.toLocalDate()) HijrahDate.MIN else HijrahDate.MAX
            CalendarDay(
                hijrahDate = clamped,
                isCurrentMonth = false,
                isToday = false,
                isSelected = false,
                isDisabled = true,
                isWeekend = WeekDay.fromDayOfWeek(anchor.dayOfWeek) in weekendDays,
                adjustmentDays = adjustmentDays,
            )
        }
    }

    return CalendarMonth(
        yearMonth = this,
        days = days.toImmutableList(),
        firstDayOfWeek = firstDayOfWeek,
        adjustmentDays = adjustmentDays,
    )
}

/**
 * Pakistan (Ruet-e-Hilal) variant of [toCalendarMonth]: each grid cell carries its observed
 * [PakistanHijriDate]. Mirroring the Umm al-Qura semantics, the observed Pakistan date of a
 * real-world Gregorian day [G] is `gregorianToHijri(G + adjustmentDays)`, so a moon-sighting
 * adjustment (positive = dates one day ahead, negative = one day behind) shifts the whole
 * grid, the today highlight, selection comparands and the weekday column alignment.
 */
private fun HijrahYearMonth.toPakistanCalendarMonth(
    firstDayOfWeek: WeekDay,
    selectedDate: PakistanHijriDate?,
    adjustmentDays: Int,
    weekendDays: Set<WeekDay>,
): CalendarMonth {
    val todayObserved = PakistanHijriCalendar.today()
        ?.localDate
        ?.plus(adjustmentDays, DateTimeUnit.DAY)
        ?.let(PakistanHijriCalendar::gregorianToHijri)

    // Real-world Gregorian day the observed Pakistani "1" of this month falls on.
    val monthStartAnchor = PakistanHijriCalendar.hijriToGregorian(year, month.number, 1)
        .minus(adjustmentDays, DateTimeUnit.DAY)
    val firstCellDow = WeekDay.fromDayOfWeek(monthStartAnchor.dayOfWeek)
    val leadingDaysCount = daysBefore(firstCellDow, firstDayOfWeek)
    val gridStart = monthStartAnchor.minus(leadingDaysCount, DateTimeUnit.DAY)

    val days = (0 until CalendarMonth.TOTAL_DAYS).map { offset ->
        val anchor = gridStart.plus(offset, DateTimeUnit.DAY)
        val shifted = anchor.plus(adjustmentDays, DateTimeUnit.DAY)
        val converted = PakistanHijriCalendar.gregorianToHijri(shifted)

        if (converted != null && converted.year in PakistanHijriCalendar.MIN_YEAR..PakistanHijriCalendar.MAX_YEAR) {
            CalendarDay(
                pakistanDate = converted,
                isCurrentMonth = converted.year == year && converted.month == month.number,
                isToday = converted == todayObserved,
                isSelected = converted == selectedDate,
                isDisabled = false,
                isWeekend = WeekDay.fromDayOfWeek(anchor.dayOfWeek) in weekendDays,
                adjustmentDays = adjustmentDays,
            )
        } else {
            CalendarDay(
                pakistanDate = null,
                isCurrentMonth = false,
                isToday = false,
                isSelected = false,
                isDisabled = true,
                isWeekend = WeekDay.fromDayOfWeek(anchor.dayOfWeek) in weekendDays,
                adjustmentDays = adjustmentDays,
            )
        }
    }

    return CalendarMonth(
        yearMonth = this,
        days = days.toImmutableList(),
        firstDayOfWeek = firstDayOfWeek,
        adjustmentDays = adjustmentDays,
    )
}

private fun LocalDate.toHijrahDateOrNull(): HijrahDate? {
    return try {
        toHijrahDate()
    } catch (_: Exception) {
        null
    }
}

/**
 * Observed Umm al-Qura variant of [toCalendarMonth]: when the user forces month lengths
 * via [HijriMonthOverrides], each grid cell carries its observed [ObservedHijriDate]. The
 * real-world Gregorian day a cell represents stays `anchor`; the observed Hijri date of
 * that day is computed from the override-shifted calendar, so a forced 30th (or a clipped
 * 29th) realigns the "1 of the month" headings and the today/selection highlights.
 */
private fun HijrahYearMonth.toObservedCalendarMonth(
    firstDayOfWeek: WeekDay,
    selectedDate: ObservedHijriDate?,
    adjustmentDays: Int,
    weekendDays: Set<WeekDay>,
): CalendarMonth {
    val todayObserved = ObservedHijriCalendar.today(adjustmentDays)

    // Real-world Gregorian day the observed "1" of this month falls on.
    val monthStartAnchor = ObservedHijriCalendar.observedToGregorian(year, month.number, 1)
        .minus(adjustmentDays, DateTimeUnit.DAY)
    val firstCellDow = WeekDay.fromDayOfWeek(monthStartAnchor.dayOfWeek)
    val leadingDaysCount = daysBefore(firstCellDow, firstDayOfWeek)
    val gridStart = monthStartAnchor.minus(leadingDaysCount, DateTimeUnit.DAY)

    val days = (0 until CalendarMonth.TOTAL_DAYS).map { offset ->
        val anchor = gridStart.plus(offset, DateTimeUnit.DAY)
        val shifted = anchor.plus(adjustmentDays, DateTimeUnit.DAY)
        val observed = ObservedHijriCalendar.observedDateAt(shifted.toEpochDays())

        if (observed != null) {
            CalendarDay(
                hijrahDate = shifted.toHijrahDateOrNull(),
                observedDate = observed,
                isCurrentMonth = observed.year == year && observed.month == month.number,
                isToday = observed == todayObserved,
                isSelected = observed == selectedDate,
                isDisabled = false,
                isWeekend = WeekDay.fromDayOfWeek(anchor.dayOfWeek) in weekendDays,
                adjustmentDays = adjustmentDays,
            )
        } else {
            // Out of the supported Umm al-Qura range: clamp instead of crashing.
            val clamped = if (shifted < HijrahDate.MIN.toLocalDate()) HijrahDate.MIN else HijrahDate.MAX
            CalendarDay(
                hijrahDate = clamped,
                isCurrentMonth = false,
                isToday = false,
                isSelected = false,
                isDisabled = true,
                isWeekend = WeekDay.fromDayOfWeek(anchor.dayOfWeek) in weekendDays,
                adjustmentDays = adjustmentDays,
            )
        }
    }

    return CalendarMonth(
        yearMonth = this,
        days = days.toImmutableList(),
        firstDayOfWeek = firstDayOfWeek,
        adjustmentDays = adjustmentDays,
    )
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
