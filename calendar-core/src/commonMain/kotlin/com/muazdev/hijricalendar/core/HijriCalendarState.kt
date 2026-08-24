package com.muazdev.hijricalendar.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.abdulrahman_b.hijrahdatetime.HijrahDate
import com.abdulrahman_b.hijrahdatetime.toHijrahDate
import com.abdulrahman_b.hijrahdatetime.toLocalDate
import com.abdulrahman_b.hijrahdatetime.yearMonth
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

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

    val calendarMonth: CalendarMonth
        get() = _currentMonth.toCalendarMonth(
            firstDayOfWeek = firstDayOfWeek,
            selectedDate = _selectedDate,
            minDate = minDate,
            maxDate = maxDate,
            adjustmentDays = adjustmentDays,
        )

    fun goToNextMonth() {
        _currentMonth = _currentMonth.plusMonth(1)
    }

    fun goToPreviousMonth() {
        _currentMonth = _currentMonth.minusMonth(1)
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
        val today = todayHijriDate()
        if (today != null) {
            _currentMonth = today.yearMonth
            _selectedDate = today
        }
    }

    private fun todayHijriDate(): HijrahDate? {
        return try {
            val now = kotlin.time.Clock.System.now()
            val localDate = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
            localDate.plus(adjustmentDays, DateTimeUnit.DAY).toHijrahDate()
        } catch (_: Exception) {
            null
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
): HijriCalendarState {
    return remember {
        HijriCalendarState(
            initialMonth = initialMonth,
            initialSelectedDate = initialSelectedDate,
            firstDayOfWeek = firstDayOfWeek,
            minDate = minDate,
            maxDate = maxDate,
            adjustmentDays = adjustmentDays,
        )
    }
}
