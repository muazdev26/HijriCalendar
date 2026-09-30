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
public fun HijrahYearMonth.toCalendarMonth(
    firstDayOfWeek: WeekDay = WeekDay.DEFAULT_FIRST_DAY,
    selectedDate: HijrahDate? = null,
    selectedPakistanDate: PakistanHijriDate? = null,
    selectedObservedDate: ObservedHijriDate? = null,
    minDate: HijrahDate? = null,
    maxDate: HijrahDate? = null,
    adjustmentDays: Int = 0,
    pakistan: Boolean = false,
    weekendDays: Set<WeekDay> = WeekDay.WEEKEND_DAYS,
    overrides: HijriMonthLengths = HijriMonthOverrides.current,
): CalendarMonth {
    val window = DateWindow.of(minDate, maxDate, adjustmentDays)

    if (pakistan) {
        return toPakistanCalendarMonth(
            firstDayOfWeek = firstDayOfWeek,
            selectedDate = selectedPakistanDate,
            adjustmentDays = adjustmentDays,
            weekendDays = weekendDays,
            overrides = overrides,
            window = window,
        )
    }

    // User month-length overrides turn the Umm al-Qura grid into the observed calendar.
    if (overrides.all().isNotEmpty()) {
        return toObservedCalendarMonth(
            firstDayOfWeek = firstDayOfWeek,
            selectedDate = selectedObservedDate,
            adjustmentDays = adjustmentDays,
            weekendDays = weekendDays,
            overrides = overrides,
            window = window,
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
                isDisabled = !window.contains(anchor),
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
        gregorianRange = resolveGregorianMonthRange(year, month.number, adjustmentDays = adjustmentDays),
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
    overrides: HijriMonthLengths,
    window: DateWindow,
): CalendarMonth {
    val todayObserved = PakistanHijriCalendar.today(overrides)
        ?.localDate
        ?.plus(adjustmentDays, DateTimeUnit.DAY)
        ?.let { PakistanHijriCalendar.gregorianToHijri(it, overrides) }

    // Real-world Gregorian day the observed Pakistani "1" of this month falls on.
    val monthStartAnchor = PakistanHijriCalendar.hijriToGregorian(year, month.number, 1, overrides)
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
                isDisabled = !window.contains(anchor),
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
        gregorianRange = resolveGregorianMonthRange(
            year,
            month.number,
            pakistan = true,
            adjustmentDays = adjustmentDays,
        ),
    )
}

/**
 * Converts to a Umm al-Qura [HijrahDate], or null when this Gregorian day falls outside the
 * calculation's table (which happens legitimately at the edges of a 42-cell grid).
 *
 * Deliberately does **not** log: this runs once per grid cell, so a month at either edge of the
 * supported range would emit six lines every recomposition and bury the real signal. A defect
 * here propagates instead of returning null, which is the point of narrowing the catch.
 */
private fun LocalDate.toHijrahDateOrNull(): HijrahDate? = orNullIfOutOfRange { toHijrahDate() }

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
    overrides: HijriMonthLengths,
    window: DateWindow,
): CalendarMonth {
    val todayObserved = ObservedHijriCalendar.today(adjustmentDays, overrides)

    // Real-world Gregorian day the observed "1" of this month falls on.
    val monthStartAnchor = ObservedHijriCalendar.observedToGregorian(year, month.number, 1, overrides)
        .minus(adjustmentDays, DateTimeUnit.DAY)
    val firstCellDow = WeekDay.fromDayOfWeek(monthStartAnchor.dayOfWeek)
    val leadingDaysCount = daysBefore(firstCellDow, firstDayOfWeek)
    val gridStart = monthStartAnchor.minus(leadingDaysCount, DateTimeUnit.DAY)

    val days = (0 until CalendarMonth.TOTAL_DAYS).map { offset ->
        val anchor = gridStart.plus(offset, DateTimeUnit.DAY)
        val shifted = anchor.plus(adjustmentDays, DateTimeUnit.DAY)
        val observed = ObservedHijriCalendar.observedDateAt(shifted.toEpochDays(), overrides)

        if (observed != null) {
            CalendarDay(
                hijrahDate = shifted.toHijrahDateOrNull(),
                observedDate = observed,
                isCurrentMonth = observed.year == year && observed.month == month.number,
                isToday = observed == todayObserved,
                isSelected = observed == selectedDate,
                isDisabled = !window.contains(anchor),
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
        gregorianRange = resolveGregorianMonthRange(year, month.number, adjustmentDays = adjustmentDays),
    )
}

private fun daysBefore(actualFirstDay: WeekDay, desiredFirstDay: WeekDay): Int {
    val diff = actualFirstDay.index - desiredFirstDay.index
    return if (diff >= 0) diff else diff + CalendarMonth.DAYS_IN_WEEK
}

/**
 * A [minDate]/[maxDate] selection window, resolved once into the real-world Gregorian days that
 * all three grid spaces agree on.
 *
 * The bounds stay [HijrahDate] for API compatibility, but they are no longer *compared* as
 * Hijrah dates. A cell in Pakistan space, or an observed cell whose day exceeds its month's
 * Umm al-Qura length, has no Umm al-Qura coordinate to compare against at all — so comparing
 * them meant either rejecting those cells outright or dropping the bounds on those paths (which
 * is what happened: the Pakistan and observed builders hardcoded `isDisabled = false`, making
 * `minDate`/`maxDate` silently no-ops outside plain Umm al-Qura mode).
 *
 * Every [CalendarDay] already knows the real-world day it occupies as [CalendarDay.localDate],
 * and all three spaces agree on that single ordering, so resolving the bounds into
 * [LocalDate] makes the window mean the same thing everywhere — including `adjustmentDays`,
 * which is why the bounds are shifted by it here and compared against the cell's own
 * `anchor` (which is already in real-world space).
 */
internal class DateWindow private constructor(
    private val min: LocalDate?,
    private val max: LocalDate?,
) {
    /** Whether [day], the real-world day a cell occupies, is inside the window. */
    fun contains(day: LocalDate): Boolean {
        if (min != null && day < min) return false
        if (max != null && day > max) return false
        return true
    }

    /** Resolves [minDate]/[maxDate] into the same real-world space as [CalendarDay.localDate]. */
    companion object {
        fun of(minDate: HijrahDate?, maxDate: HijrahDate?, adjustmentDays: Int): DateWindow =
            DateWindow(
                min = minDate?.toRealWorld(adjustmentDays),
                max = maxDate?.toRealWorld(adjustmentDays),
            )

        private fun HijrahDate.toRealWorld(adjustmentDays: Int): LocalDate =
            toLocalDate().minus(adjustmentDays, DateTimeUnit.DAY)
    }
}
