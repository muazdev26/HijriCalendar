package com.muazdev.hijricalendar.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.abdulrahman_b.hijrahdatetime.HijrahDate
import com.abdulrahman_b.hijrahdatetime.toHijrahDate
import com.abdulrahman_b.hijrahdatetime.toLocalDate
import com.abdulrahman_b.hijrahdatetime.yearMonth
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * State holder for a Hijri calendar.
 *
 * @param adjustmentDays Shifts the whole calendar relative to the underlying calculation
 *   to compensate for local moon sighting: the observed Hijri date of a Gregorian day is
 *   the conversion of `(date + adjustmentDays)`. All generated cells, weekday alignment,
 *   [selectedDate]/[selectedPakistanDate] and today detection live in this adjusted space.
 *   In Pakistan (Ruet-e-Hilal) mode the same shift is applied on top of the Pakistan
 *   table. Read it at runtime or change it with [setAdjustmentDays] (the selected date is
 *   kept on the same real-world Gregorian day).
 * @param initialSelectedDate The initially selected date. May be given as an unadjusted
 *   (Umm al-Qura) [HijrahDate]; it is normalized into adjusted space at construction, so
 *   passing `today.toHijrahDate()` with `adjustmentDays != 0` still highlights the same
 *   real-world day that `isToday` highlights.
 * @param initialSelectedPakistanDate The initially selected date in Pakistan space. It is
 *   expected to be an observed date (already shifted by [adjustmentDays]).
 */
@Stable
class HijriCalendarState(
    initialMonth: HijrahYearMonth,
    initialSelectedDate: HijrahDate? = null,
    val firstDayOfWeek: WeekDay = WeekDay.DEFAULT_FIRST_DAY,
    val minDate: HijrahDate? = null,
    val maxDate: HijrahDate? = null,
    adjustmentDays: Int = 0,
    pakistanDates: Boolean = false,
    initialSelectedPakistanDate: PakistanHijriDate? = null,
    initialSelectedObservedDate: ObservedHijriDate? = null,
    val weekendDays: Set<WeekDay> = WeekDay.WEEKEND_DAYS,
) {
    private var _adjustmentDays by mutableStateOf(adjustmentDays)
    private var _pakistanDates by mutableStateOf(pakistanDates)
    private var _currentMonth by mutableStateOf(initialMonth)
    private var _selectedDate by mutableStateOf(initialSelectedDate.adjustToAdjustedSpace())
    private var _selectedPakistanDate by mutableStateOf(initialSelectedPakistanDate)
    private var _selectedObservedDate by mutableStateOf(initialSelectedObservedDate)
    private var _overridesRevision by mutableStateOf(HijriMonthOverrides.currentRevision)

    val adjustmentDays: Int get() = _adjustmentDays

    /**
     * Whether the calendar shows Ruet-e-Hilal (Pakistan) dates instead of the Umm al-Qura
     * calculation. Toggle at runtime with [setPakistanDates].
     */
    val pakistanDates: Boolean get() = _pakistanDates

    private fun HijrahDate?.adjustToAdjustedSpace(): HijrahDate? {
        if (this == null || adjustmentDays == 0) return this
        return try {
            toLocalDate().plus(adjustmentDays, DateTimeUnit.DAY).toHijrahDate()
        } catch (_: Exception) {
            this
        }
    }

    val currentMonth: HijrahYearMonth get() = _currentMonth
    val selectedDate: HijrahDate? get() = _selectedDate

    /** Selected date in Pakistan (Ruet-e-Hilal) space; non-null when a Pakistan-mode cell is selected. */
    val selectedPakistanDate: PakistanHijriDate? get() = _selectedPakistanDate

    /**
     * Selected date in the observed (override-shifted) Umm al-Qura space; non-null when a
     * cell generated with [HijriMonthOverrides] applied is selected.
     */
    val selectedObservedDate: ObservedHijriDate? get() = _selectedObservedDate

    /**
     * Snapshot of [HijriMonthOverrides.currentRevision] at the last recomposition, used to
     * rebuild the grid and header when month-length overrides change at runtime.
     */
    val overridesRevision: Long get() = _overridesRevision

    /**
     * Whether the previous month can be navigated to without leaving the
     * [minDate]/[maxDate] range. A month is considered navigable if it contains at
     * least one day within the bounded range. `true` when [minDate] is unset.
     */
    val canGoToPreviousMonth: Boolean
        get() = _currentMonth.minusMonthOrNull(1)?.isNavigableWithin(minDate, maxDate) ?: false

    /**
     * Whether the next month can be navigated to without leaving the
     * [minDate]/[maxDate] range. A month is considered navigable if it contains at
     * least one day within the bounded range. `true` when [maxDate] is unset.
     */
    val canGoToNextMonth: Boolean
        get() = _currentMonth.plusMonthOrNull(1)?.isNavigableWithin(minDate, maxDate) ?: false

    val calendarMonth: CalendarMonth by derivedStateOf {
        // A plain read that establishes a snapshot dependency so the grid recomputes when
        // user overrides change, even though overrides are not a Compose state themselves.
        _overridesRevision
        _currentMonth.toCalendarMonth(
            firstDayOfWeek = firstDayOfWeek,
            selectedDate = _selectedDate,
            selectedPakistanDate = _selectedPakistanDate,
            selectedObservedDate = _selectedObservedDate,
            minDate = minDate,
            maxDate = maxDate,
            adjustmentDays = adjustmentDays,
            pakistan = pakistanDates,
            weekendDays = weekendDays,
        )
    }

    fun goToNextMonth() {
        _currentMonth = _currentMonth.plusMonthOrNull(1) ?: _currentMonth
    }

    fun goToPreviousMonth() {
        _currentMonth = _currentMonth.minusMonthOrNull(1) ?: _currentMonth
    }

    /**
     * Selects the given Pakistan (Ruet-e-Hilal) date. Falls back to navigating the month
     * only when [minDate]/[maxDate] is configured (unbounded calendars never reject a date).
     */
    fun selectPakistanDate(date: PakistanHijriDate) {
        _selectedPakistanDate = date
        val yearMonth = HijrahYearMonth(date.year, date.month)
        if (yearMonth != _currentMonth) {
            _currentMonth = yearMonth
        }
    }

    /**
     * Selects the given observed (override-shifted) Umm al-Qura date. Unlike [selectDate] it
     * accepts days beyond a month's calculated length (e.g. a forced 30th), so the selection
     * survives when overrides are cleared via navigation only.
     */
    fun selectObservedDate(date: ObservedHijriDate) {
        _selectedObservedDate = date
        val yearMonth = HijrahYearMonth(date.year, date.month)
        if (yearMonth != _currentMonth) {
            _currentMonth = yearMonth
        }
    }

    /** Forced length for [year]/[month] (29 or 30), or null when the calculation is used. */
    fun monthLengthOf(year: Int, month: Int): Int? = HijriMonthOverrides.monthLength(year, month)

    /**
     * Forces [year]/[month] to [length] (29 or 30) days, overriding both the Umm al-Qura
     * calculation and, in Pakistan mode, the [PakistanHijriCalendar.FIXES] table for that
     * month. Later months re-anchor off the new length until the next re-sync fix.
     */
    fun setMonthLength(year: Int, month: Int, length: Int) {
        HijriMonthOverrides.setMonthLength(year, month, length)
        _overridesRevision = HijriMonthOverrides.currentRevision
    }

    /** Removes a user-forced length for [year]/[month], falling back to the calculation. */
    fun clearMonthLength(year: Int, month: Int) {
        HijriMonthOverrides.clearMonthLength(year, month)
        _overridesRevision = HijriMonthOverrides.currentRevision
    }

    /** Removes every user-forced length, restoring the pure calculated calendar. */
    fun clearAllMonthLengths() {
        HijriMonthOverrides.clearAll()
        _overridesRevision = HijriMonthOverrides.currentRevision
    }

    /**
     * Routes a tapped cell to the selection space it belongs to (Umm al-Qura, observed
     * override-shifted Umm al-Qura, or Pakistan), keeping the behavior identical whichever
     * mode is active.
     */
    fun selectDay(day: CalendarDay) {
        val pakistanDate = day.pakistanDate
        if (pakistanDate != null) {
            selectPakistanDate(pakistanDate)
        } else {
            val observedDate = day.observedDate
            if (observedDate != null) {
                selectObservedDate(observedDate)
            } else {
                day.hijrahDate?.let(::selectDate)
            }
        }
    }

    fun selectDate(date: HijrahDate) {
        if (!date.isDisabledByRange(minDate, maxDate)) {
            _selectedDate = date
            if (date.yearMonth != _currentMonth) {
                _currentMonth = date.yearMonth
            }
        }
    }

    fun goToMonth(yearMonth: HijrahYearMonth) {
        _currentMonth = yearMonth
    }

    fun goToToday() {
        if (pakistanDates) {
            val todayObserved = PakistanHijriCalendar.today()
                ?.localDate
                ?.plus(adjustmentDays, DateTimeUnit.DAY)
                ?.let(PakistanHijriCalendar::gregorianToHijri)
            if (todayObserved != null) {
                _currentMonth = HijrahYearMonth(todayObserved.year, todayObserved.month)
                _selectedPakistanDate = todayObserved
            }
            return
        }
        if (hasObservedOverrides) {
            val todayObserved = ObservedHijriCalendar.today(adjustmentDays)
            if (todayObserved != null) {
                _currentMonth = HijrahYearMonth(todayObserved.year, todayObserved.month)
                _selectedObservedDate = todayObserved
            }
            return
        }
        val today = todayHijriDate(adjustmentDays)
        if (today != null) {
            _currentMonth = today.yearMonth
            _selectedDate = today
        }
    }

    /**
     * Switches between the Umm al-Qura calculation and the Pakistan Ruet-e-Hilal calendar
     * at runtime. The currently selected date is carried across the switch by its
     * real-world Gregorian day.
     */
    fun setPakistanDates(enabled: Boolean) {
        if (enabled == pakistanDates) return
        if (enabled) {
            val gregorianDay = selectedObservedDate
                ?.localDate
                ?.plus(adjustmentDays, DateTimeUnit.DAY)
                ?: _selectedDate
                    ?.toLocalDate()
                    ?.plus(adjustmentDays, DateTimeUnit.DAY)
            _selectedPakistanDate = gregorianDay?.let(PakistanHijriCalendar::gregorianToHijri)
        } else {
            val gregorianDay = _selectedPakistanDate
                ?.localDate
                ?.minus(adjustmentDays, DateTimeUnit.DAY)
            if (hasObservedOverrides) {
                _selectedObservedDate = gregorianDay
                    ?.toEpochDays()
                    ?.let(ObservedHijriCalendar::observedDateAt)
            } else {
                _selectedDate = gregorianDay?.let { runCatching { it.toHijrahDate() }.getOrNull() }
            }
        }
        _pakistanDates = enabled
    }

    /**
     * Changes the moon-sighting adjustment at runtime.
     *
     * The selected date (if any) is re-mapped so the same real-world Gregorian day stays
     * selected — its Hijri representation in the new adjusted space — and the today
     * highlight shifts accordingly. The current month is kept.
     */
    fun setAdjustmentDays(newAdjustmentDays: Int) {
        val shift = newAdjustmentDays - adjustmentDays
        if (shift == 0) {
            _adjustmentDays = newAdjustmentDays
            return
        }
        if (pakistanDates) {
            val remapped = _selectedPakistanDate
                ?.localDate
                ?.plus(shift, DateTimeUnit.DAY)
                ?.let(PakistanHijriCalendar::gregorianToHijri)
            _selectedPakistanDate = remapped
            _adjustmentDays = newAdjustmentDays
            return
        }
        if (hasObservedOverrides) {
            val remapped = _selectedObservedDate
                ?.localDate
                ?.plus(shift, DateTimeUnit.DAY)
                ?.toEpochDays()
                ?.let(ObservedHijriCalendar::observedDateAt)
            _selectedObservedDate = remapped
            _adjustmentDays = newAdjustmentDays
            return
        }
        val remapped = _selectedDate?.let { current ->
            try {
                current.toLocalDate().plus(shift, DateTimeUnit.DAY).toHijrahDate()
            } catch (_: Exception) {
                current
            }
        }
        _selectedDate = remapped
        _adjustmentDays = newAdjustmentDays
    }

    private val hasObservedOverrides: Boolean
        get() = _overridesRevision >= 0 && HijriMonthOverrides.all().isNotEmpty()

    private fun HijrahDate.isDisabledByRange(min: HijrahDate?, max: HijrahDate?): Boolean {
        if (min != null && this < min) return true
        if (max != null && this > max) return true
        return false
    }
}

@Composable
fun rememberHijriCalendarState(
    initialMonth: HijrahYearMonth,
    initialSelectedDate: HijrahDate? = null,
    firstDayOfWeek: WeekDay = WeekDay.DEFAULT_FIRST_DAY,
    minDate: HijrahDate? = null,
    maxDate: HijrahDate? = null,
    adjustmentDays: Int = 0,
    pakistanDates: Boolean = false,
    initialSelectedPakistanDate: PakistanHijriDate? = null,
    weekendDays: Set<WeekDay> = WeekDay.WEEKEND_DAYS,
): HijriCalendarState {
    return remember {
        HijriCalendarState(
            initialMonth = initialMonth,
            initialSelectedDate = initialSelectedDate,
            firstDayOfWeek = firstDayOfWeek,
            minDate = minDate,
            maxDate = maxDate,
            adjustmentDays = adjustmentDays,
            pakistanDates = pakistanDates,
            initialSelectedPakistanDate = initialSelectedPakistanDate,
            weekendDays = weekendDays,
        )
    }
}

/**
 * A `rememberSaveable`-backed variant of [rememberHijriCalendarState]. Unlike the plain
 * `remember` version, the current month and the selected date (Umm al-Qura, observed
 * override-shifted, or Pakistan) survive configuration changes and process death, so the
 * calendar does not silently reset. The immutable range/weekend configuration (first day of
 * week, min/max dates, adjustment days, weekend days) is captured at first composition and
 * reused when the state is restored.
 */
@Composable
fun rememberSaveableHijriCalendarState(
    initialMonth: HijrahYearMonth,
    initialSelectedDate: HijrahDate? = null,
    firstDayOfWeek: WeekDay = WeekDay.DEFAULT_FIRST_DAY,
    minDate: HijrahDate? = null,
    maxDate: HijrahDate? = null,
    adjustmentDays: Int = 0,
    pakistanDates: Boolean = false,
    initialSelectedPakistanDate: PakistanHijriDate? = null,
    weekendDays: Set<WeekDay> = WeekDay.WEEKEND_DAYS,
): HijriCalendarState {
    val config = remember(firstDayOfWeek, minDate, maxDate, adjustmentDays, pakistanDates, weekendDays) {
        HijriCalendarStateConfig(firstDayOfWeek, minDate, maxDate, adjustmentDays, pakistanDates, weekendDays)
    }
    val saver = remember(config) { hijriCalendarStateSaver(config) }
    return rememberSaveable(saver = saver) {
        HijriCalendarState(
            initialMonth = initialMonth,
            initialSelectedDate = initialSelectedDate,
            firstDayOfWeek = firstDayOfWeek,
            minDate = minDate,
            maxDate = maxDate,
            adjustmentDays = adjustmentDays,
            pakistanDates = pakistanDates,
            initialSelectedPakistanDate = initialSelectedPakistanDate,
            weekendDays = weekendDays,
        )
    }
}

internal data class HijriCalendarStateConfig(
    val firstDayOfWeek: WeekDay,
    val minDate: HijrahDate?,
    val maxDate: HijrahDate?,
    val adjustmentDays: Int,
    val pakistanDates: Boolean = false,
    val weekendDays: Set<WeekDay>,
)

/**
 * `Saver<HijriCalendarState, List<Int>>` encoding:
 * `[year, month, (0|1 pakistan), (0 none, 1 hijrah, 2 pakistan, 3 observed), selYear?, selMonth?, selDay?, observedLength?]`.
 *
 * The selected date is saved in its own space (Umm al-Qura unadjusted for the calculation
 * calendar, Pakistan year/month/day for the Ruet-e-Hilal calendar, or the observed
 * override-shifted year/month/day including the effective month length) and re-normalized on
 * restore because [HijriCalendarState] shifts `initialSelectedDate` by [HijriCalendarStateConfig.adjustmentDays]
 * at construction.
 */
internal fun hijriCalendarStateSaver(
    config: HijriCalendarStateConfig,
): Saver<HijriCalendarState, List<Int>> = Saver(
    save = { state ->
        buildList {
            add(state.currentMonth.year)
            add(state.currentMonth.month.number)
            add(if (config.pakistanDates) 1 else 0)
            when {
                config.pakistanDates -> {
                    val selected = state.selectedPakistanDate
                    if (selected == null) {
                        add(SELECTION_NONE)
                    } else {
                        add(SELECTION_PAKISTAN)
                        add(selected.year)
                        add(selected.month)
                        add(selected.day)
                    }
                }
                state.selectedObservedDate != null -> {
                    val selected = state.selectedObservedDate!!
                    add(SELECTION_OBSERVED)
                    add(selected.year)
                    add(selected.month)
                    add(selected.day)
                    add(selected.monthLength)
                }
                else -> {
                    val selected = state.selectedDate.toUnadjustedSpace(config.adjustmentDays)
                    if (selected == null) {
                        add(SELECTION_NONE)
                    } else {
                        add(SELECTION_HIJRAH)
                        add(selected.year)
                        add(selected.month.number)
                        add(selected.day)
                    }
                }
            }
        }
    },
    restore = { saved ->
        val year = saved[0]
        val month = saved[1]
        val pakistan = saved[2] == 1
        val selectionType = saved[3]
        val initialSelectedDate =
            if (selectionType == SELECTION_HIJRAH && !pakistan) HijrahDate(saved[4], saved[5], saved[6]) else null
        val initialSelectedPakistanDate =
            if (selectionType == SELECTION_PAKISTAN && pakistan) PakistanHijriDate(saved[4], saved[5], saved[6]) else null
        val initialSelectedObservedDate =
            if (selectionType == SELECTION_OBSERVED && !pakistan) ObservedHijriDate(saved[4], saved[5], saved[6], saved[7]) else null
        HijriCalendarState(
            initialMonth = HijrahYearMonth(year, month),
            initialSelectedDate = initialSelectedDate,
            firstDayOfWeek = config.firstDayOfWeek,
            minDate = config.minDate,
            maxDate = config.maxDate,
            adjustmentDays = config.adjustmentDays,
            pakistanDates = config.pakistanDates,
            initialSelectedPakistanDate = initialSelectedPakistanDate,
            initialSelectedObservedDate = initialSelectedObservedDate,
            weekendDays = config.weekendDays,
        )
    },
)

private fun HijrahDate?.toUnadjustedSpace(adjustmentDays: Int): HijrahDate? {
    if (this == null || adjustmentDays == 0) return this
    return try {
        toLocalDate().minus(adjustmentDays, DateTimeUnit.DAY).toHijrahDate()
    } catch (_: Exception) {
        this
    }
}

private const val SELECTION_NONE = 0
private const val SELECTION_HIJRAH = 1
private const val SELECTION_PAKISTAN = 2
private const val SELECTION_OBSERVED = 3

private fun HijrahYearMonth.plusMonthOrNull(months: Int): HijrahYearMonth? {
    return try {
        plusMonth(months)
    } catch (_: Exception) {
        null
    }
}

private fun HijrahYearMonth.minusMonthOrNull(months: Int): HijrahYearMonth? {
    return try {
        minusMonth(months)
    } catch (_: Exception) {
        null
    }
}

private fun HijrahYearMonth.isNavigableWithin(min: HijrahDate?, max: HijrahDate?): Boolean {
    if (min != null && lastDay < min) return false
    if (max != null && firstDay > max) return false
    return true
}
