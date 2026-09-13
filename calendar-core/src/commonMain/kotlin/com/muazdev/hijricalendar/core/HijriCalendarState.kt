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
 * @param adjustmentDays Shifts the whole calendar relative to the Umm al-Qura calculation
 *   to compensate for local moon sighting: the observed Hijri date of a Gregorian day is
 *   the Umm al-Qura conversion of `(date + adjustmentDays)`. All generated cells,
 *   weekday alignment, [selectedDate] and today detection live in this adjusted space.
 * @param initialSelectedDate The initially selected date. May be given as an unadjusted
 *   (Umm al-Qura) [HijrahDate]; it is normalized into adjusted space at construction, so
 *   passing `today.toHijrahDate()` with `adjustmentDays != 0` still highlights the same
 *   real-world day that `isToday` highlights.
 */
@Stable
class HijriCalendarState(
    initialMonth: HijrahYearMonth,
    initialSelectedDate: HijrahDate? = null,
    val firstDayOfWeek: WeekDay = WeekDay.DEFAULT_FIRST_DAY,
    val minDate: HijrahDate? = null,
    val maxDate: HijrahDate? = null,
    val adjustmentDays: Int = 0,
    val weekendDays: Set<WeekDay> = WeekDay.WEEKEND_DAYS,
) {
    private var _currentMonth by mutableStateOf(initialMonth)
    private var _selectedDate by mutableStateOf(initialSelectedDate.adjustToAdjustedSpace())

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
        _currentMonth.toCalendarMonth(
            firstDayOfWeek = firstDayOfWeek,
            selectedDate = _selectedDate,
            minDate = minDate,
            maxDate = maxDate,
            adjustmentDays = adjustmentDays,
            weekendDays = weekendDays,
        )
    }

    fun goToNextMonth() {
        _currentMonth = _currentMonth.plusMonthOrNull(1) ?: _currentMonth
    }

    fun goToPreviousMonth() {
        _currentMonth = _currentMonth.minusMonthOrNull(1) ?: _currentMonth
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
        val today = todayHijriDate(adjustmentDays)
        if (today != null) {
            _currentMonth = today.yearMonth
            _selectedDate = today
        }
    }

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
            weekendDays = weekendDays,
        )
    }
}

/**
 * A `rememberSaveable`-backed variant of [rememberHijriCalendarState]. Unlike the plain
 * `remember` version, the current month and selected date survive configuration changes and
 * process death, so the calendar does not silently reset. The immutable range/weekend
 * configuration (first day of week, min/max dates, adjustment days, weekend days) is captured
 * at first composition and reused when the state is restored.
 */
@Composable
fun rememberSaveableHijriCalendarState(
    initialMonth: HijrahYearMonth,
    initialSelectedDate: HijrahDate? = null,
    firstDayOfWeek: WeekDay = WeekDay.DEFAULT_FIRST_DAY,
    minDate: HijrahDate? = null,
    maxDate: HijrahDate? = null,
    adjustmentDays: Int = 0,
    weekendDays: Set<WeekDay> = WeekDay.WEEKEND_DAYS,
): HijriCalendarState {
    val config = remember(firstDayOfWeek, minDate, maxDate, adjustmentDays, weekendDays) {
        HijriCalendarStateConfig(firstDayOfWeek, minDate, maxDate, adjustmentDays, weekendDays)
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
            weekendDays = weekendDays,
        )
    }
}

internal data class HijriCalendarStateConfig(
    val firstDayOfWeek: WeekDay,
    val minDate: HijrahDate?,
    val maxDate: HijrahDate?,
    val adjustmentDays: Int,
    val weekendDays: Set<WeekDay>,
)

/**
 * `Saver<HijriCalendarState, List<Int>>` encoding: `[year, month, (0|1), selYear, selMonth, selDay]`.
 *
 * The selected date is saved in unadjusted space and re-normalized on restore because
 * [HijriCalendarState] always shifts `initialSelectedDate` by `adjustmentDays` at construction.
 */
internal fun hijriCalendarStateSaver(
    config: HijriCalendarStateConfig,
): Saver<HijriCalendarState, List<Int>> = Saver(
    save = { state ->
        buildList {
            add(state.currentMonth.year)
            add(state.currentMonth.month.number)
            val selected = state.selectedDate.toUnadjustedSpace(config.adjustmentDays)
            if (selected == null) {
                add(0)
            } else {
                add(1)
                add(selected.year)
                add(selected.month.number)
                add(selected.day)
            }
        }
    },
    restore = { saved ->
        val year = saved[0]
        val month = saved[1]
        val hasSelection = saved[2] == 1
        val selected = if (hasSelection) HijrahDate(saved[3], saved[4], saved[5]) else null
        HijriCalendarState(
            initialMonth = HijrahYearMonth(year, month),
            initialSelectedDate = selected,
            firstDayOfWeek = config.firstDayOfWeek,
            minDate = config.minDate,
            maxDate = config.maxDate,
            adjustmentDays = config.adjustmentDays,
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
