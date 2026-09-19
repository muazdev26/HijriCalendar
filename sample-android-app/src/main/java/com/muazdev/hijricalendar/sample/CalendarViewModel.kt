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
import com.muazdev.hijricalendar.core.HijriMonthOverrides
import com.muazdev.hijricalendar.core.PakistanHijriDate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class CalendarViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    init {
        // Persisted month-length overrides are global and must be seeded before the state
        // is constructed so its observed grid starts from the restored calendar.
        restoreMonthLengthOverrides()
    }

    val state: HijriCalendarState = HijriCalendarState(
        initialMonth = restoreMonth() ?: todayHijriMonth(),
        initialSelectedDate = restoreSelectedDate() ?: todayHijriDate(),
        adjustmentDays = restoreAdjustmentDays(),
        pakistanDates = restorePakistanDates(),
        initialSelectedPakistanDate = restorePakistanSelectedDate(),
    )

    var dateDisplayMode: DateDisplayMode by mutableStateOf(restoreDateDisplayMode())
        private set

    fun onDateDisplayModeChange(mode: DateDisplayMode) {
        dateDisplayMode = mode
        savedStateHandle[KEY_DATE_DISPLAY_MODE] = mode.name
    }

    fun onAdjustmentDaysChange(adjustmentDays: Int) {
        savedStateHandle[KEY_ADJUSTMENT_DAYS] = adjustmentDays
    }

    fun onPakistanDatesChange(pakistanDates: Boolean) {
        savedStateHandle[KEY_PAKISTAN_DATES] = pakistanDates
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

    /** Persists the current global month-length overrides into the saved state handle. */
    fun persistMonthLengthOverrides() {
        val all = HijriMonthOverrides.all()
        if (all.isEmpty()) {
            savedStateHandle.remove<String>(KEY_MONTH_LENGTH_OVERRIDES)
        } else {
            savedStateHandle[KEY_MONTH_LENGTH_OVERRIDES] = all.entries.joinToString { (key, length) ->
                "${key.first}-${key.second}:$length"
            }
        }
    }

    private fun restoreMonthLengthOverrides() {
        val encoded = savedStateHandle.get<String>(KEY_MONTH_LENGTH_OVERRIDES) ?: return
        if (encoded.isBlank()) return
        val restored = runCatching {
            encoded.split(",").mapNotNull { token ->
                val (period, length) = token.split(":").let { it[0] to it[1].toIntOrNull() }
                if (length == null) return@mapNotNull null
                val (yearText, monthText) = period.split("-").let { it[0] to it[1] }
                (yearText.toInt() to monthText.toInt()) to length
            }.toMap()
        }.getOrNull() ?: return
        HijriMonthOverrides.replaceAll(restored)
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
            snapshotFlow { state.pakistanDates }
                .distinctUntilChanged()
                .collect { pakistan ->
                    savedStateHandle[KEY_PAKISTAN_DATES] = pakistan
                }
        }
        viewModelScope.launch {
            snapshotFlow { state.selectedDate }
                .distinctUntilChanged()
                .collect { date ->
                    if (state.pakistanDates) return@collect
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
        viewModelScope.launch {
            snapshotFlow { state.selectedPakistanDate }
                .distinctUntilChanged()
                .collect { date ->
                    if (!state.pakistanDates) return@collect
                    if (date != null) {
                        savedStateHandle[KEY_PAK_SELECTED_YEAR] = date.year.toLong()
                        savedStateHandle[KEY_PAK_SELECTED_MONTH] = date.month.toLong()
                        savedStateHandle[KEY_PAK_SELECTED_DAY] = date.day.toLong()
                    } else {
                        savedStateHandle.remove<Long>(KEY_PAK_SELECTED_YEAR)
                        savedStateHandle.remove<Long>(KEY_PAK_SELECTED_MONTH)
                        savedStateHandle.remove<Long>(KEY_PAK_SELECTED_DAY)
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

    private fun restoreAdjustmentDays(): Int {
        val saved = savedStateHandle.get<Int>(KEY_ADJUSTMENT_DAYS)
        return saved ?: DEFAULT_ADJUSTMENT_DAYS
    }

    private fun restorePakistanDates(): Boolean {
        return savedStateHandle.get<Boolean>(KEY_PAKISTAN_DATES) ?: DEFAULT_PAKISTAN_DATES
    }

    private fun restorePakistanSelectedDate(): PakistanHijriDate? {
        val year = savedStateHandle.get<Long>(KEY_PAK_SELECTED_YEAR)?.toInt()
        val month = savedStateHandle.get<Long>(KEY_PAK_SELECTED_MONTH)?.toInt()
        val day = savedStateHandle.get<Long>(KEY_PAK_SELECTED_DAY)?.toInt()
        return if (year != null && month != null && day != null) {
            runCatching { PakistanHijriDate(year, month, day) }.getOrNull()
        } else {
            null
        }
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
        private const val KEY_ADJUSTMENT_DAYS = "adjustment_days"
        private const val KEY_PAKISTAN_DATES = "pakistan_dates"
        private const val KEY_PAK_SELECTED_YEAR = "pakistan_selected_year"
        private const val KEY_PAK_SELECTED_MONTH = "pakistan_selected_month"
        private const val KEY_PAK_SELECTED_DAY = "pakistan_selected_day"
        private const val KEY_MONTH_LENGTH_OVERRIDES = "month_length_overrides"

        private val DEFAULT_MONTH = HijrahYearMonth(1447, 1)
        private val DEFAULT_DATE_DISPLAY_MODE = DateDisplayMode.HIJRI_ONLY
        private const val DEFAULT_ADJUSTMENT_DAYS = 0
        private const val DEFAULT_PAKISTAN_DATES = false
    }
}
