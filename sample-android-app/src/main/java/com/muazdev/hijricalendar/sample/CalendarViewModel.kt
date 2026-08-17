package com.muazdev.hijricalendar.sample

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abdulrahman_b.hijrahdatetime.HijrahDate
import com.abdulrahman_b.hijrahdatetime.toHijrahDate
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import com.muazdev.hijricalendar.core.DateDisplayMode
import com.muazdev.hijricalendar.core.HijriCalendarState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class CalendarViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val state: HijriCalendarState = HijriCalendarState(
        initialMonth = restoreMonth() ?: todayHijriMonth(),
        initialSelectedDate = restoreSelectedDate() ?: todayHijriDate(),
    )

    var dateDisplayMode: DateDisplayMode by mutableStateOf(restoreDateDisplayMode())
        private set

    fun onDateDisplayModeChange(mode: DateDisplayMode) {
        dateDisplayMode = mode
        savedStateHandle[KEY_DATE_DISPLAY_MODE] = mode.name
    }

    fun goToNextMonth() {
        state.goToNextMonth()
    }

    fun goToPreviousMonth() {
        state.goToPreviousMonth()
    }

    fun goToToday() {
        state.goToToday()
    }

    init {
        viewModelScope.launch {
            snapshotFlow { state.currentMonth }
                .distinctUntilChanged()
                .collect { month ->
                    savedStateHandle[KEY_MONTH_YEAR] = month.year.toLong()
                    savedStateHandle[KEY_MONTH_VALUE] = month.month.number.toLong()
                }
        }
        viewModelScope.launch {
            snapshotFlow { state.selectedDate }
                .distinctUntilChanged()
                .collect { date ->
                    if (date != null) {
                        savedStateHandle[KEY_SELECTED_YEAR] = date.year.toLong()
                        savedStateHandle[KEY_SELECTED_MONTH] = date.month.number.toLong()
                        savedStateHandle[KEY_SELECTED_DAY] = date.day.toLong()
                    } else {
                        savedStateHandle.remove<Long>(KEY_SELECTED_YEAR)
                        savedStateHandle.remove<Long>(KEY_SELECTED_MONTH)
                        savedStateHandle.remove<Long>(KEY_SELECTED_DAY)
                    }
                }
        }
    }

    private fun restoreMonth(): HijrahYearMonth? {
        val year = savedStateHandle.get<Long>(KEY_MONTH_YEAR)?.toInt()
        val month = savedStateHandle.get<Long>(KEY_MONTH_VALUE)?.toInt()
        return if (year != null && month != null) HijrahYearMonth(year, month)
        else null
    }

    private fun restoreSelectedDate(): HijrahDate? {
        val year = savedStateHandle.get<Long>(KEY_SELECTED_YEAR)?.toInt()
        val month = savedStateHandle.get<Long>(KEY_SELECTED_MONTH)?.toInt()
        val day = savedStateHandle.get<Long>(KEY_SELECTED_DAY)?.toInt()
        return if (year != null && month != null && day != null) HijrahDate(year, month, day)
        else null
    }

    private fun restoreDateDisplayMode(): DateDisplayMode {
        val saved = savedStateHandle.get<String>(KEY_DATE_DISPLAY_MODE)
        return saved?.let { runCatching { DateDisplayMode.valueOf(it) }.getOrNull() }
            ?: DEFAULT_DATE_DISPLAY_MODE
    }

    private fun todayHijriDate(): HijrahDate? = runCatching {
        val now = kotlin.time.Clock.System.now()
        val localDate = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
        localDate.toHijrahDate()
    }.getOrNull()

    private fun todayHijriMonth(): HijrahYearMonth =
        todayHijriDate()?.yearMonth ?: DEFAULT_MONTH

    private val HijrahDate.yearMonth: HijrahYearMonth
        get() = HijrahYearMonth(year, month.number)

    companion object {
        private const val KEY_MONTH_YEAR = "current_month_year"
        private const val KEY_MONTH_VALUE = "current_month_value"
        private const val KEY_SELECTED_YEAR = "selected_year"
        private const val KEY_SELECTED_MONTH = "selected_month"
        private const val KEY_SELECTED_DAY = "selected_day"
        private const val KEY_DATE_DISPLAY_MODE = "date_display_mode"

        private val DEFAULT_MONTH = HijrahYearMonth(1447, 1)
        private val DEFAULT_DATE_DISPLAY_MODE = DateDisplayMode.HIJRI_ONLY
    }
}
