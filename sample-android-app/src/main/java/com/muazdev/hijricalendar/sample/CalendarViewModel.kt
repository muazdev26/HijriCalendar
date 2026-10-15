package com.muazdev.hijricalendar.sample

import android.content.Context
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
import com.muazdev.hijricalendar.core.HijriCalendarState
import com.muazdev.hijricalendar.core.HijriMonthOverrides
import com.muazdev.hijricalendar.core.PakistanHijriCalendar
import com.muazdev.hijricalendar.core.PakistanHijriDate
import com.muazdev.hijricalendar.ui.DateDisplayMode
import com.muazdev.hijricalendar.widget.glance.HijriWidgetRefreshLog
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class CalendarViewModel(
    private val appContext: Context,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val prefs get() = appContext.getSharedPreferences(CalendarViewModel.PREFS_NAME, Context.MODE_PRIVATE)

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
        prefs.edit().putString(KEY_DATE_DISPLAY_MODE, mode.name).apply()
    }

    fun onAdjustmentDaysChange(adjustmentDays: Int) {
        savedStateHandle[KEY_ADJUSTMENT_DAYS] = adjustmentDays
        prefs.edit().putInt(KEY_ADJUSTMENT_DAYS, adjustmentDays).apply()
    }

    fun onPakistanDatesChange(pakistanDates: Boolean) {
        savedStateHandle[KEY_PAKISTAN_DATES] = pakistanDates
        prefs.edit().putBoolean(KEY_PAKISTAN_DATES, pakistanDates).apply()
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

    /** Persists the current global month-length overrides into the saved state and data store. */
    fun persistMonthLengthOverrides() {
        val all = state.monthLengths.all()
        HijriWidgetRefreshLog.d("inapp:persist", "entries=${all.size}")
        if (all.isEmpty()) {
            savedStateHandle.remove<String>(KEY_MONTH_LENGTH_OVERRIDES)
            prefs.edit().remove(KEY_MONTH_LENGTH_OVERRIDES).apply()
        } else {
            val encoded = all.entries.joinToString(",") { (key, length) ->
                "${key.first}-${key.second}:$length"
            }
            savedStateHandle[KEY_MONTH_LENGTH_OVERRIDES] = encoded
            prefs.edit().putString(KEY_MONTH_LENGTH_OVERRIDES, encoded).apply()
        }
    }

    private fun restoreMonthLengthOverrides() {
        val prefsEncoded = prefs.getString(KEY_MONTH_LENGTH_OVERRIDES, null)
        val encoded = if (!prefsEncoded.isNullOrBlank()) prefsEncoded else savedStateHandle.get<String>(KEY_MONTH_LENGTH_OVERRIDES)
        if (encoded.isNullOrBlank()) {
            HijriWidgetRefreshLog.d("inapp:restore", "entries=0 ok=true")
            return
        }
        val restored = runCatching {
            encoded.split(",").mapNotNull { rawToken ->
                val token = rawToken.trim()
                if (token.isEmpty()) return@mapNotNull null
                val parts = token.split(":")
                if (parts.size != 2) return@mapNotNull null
                val length = parts[1].trim().toIntOrNull() ?: return@mapNotNull null
                val period = parts[0].trim().split("-")
                if (period.size != 2) return@mapNotNull null
                val year = period[0].trim().toIntOrNull() ?: return@mapNotNull null
                val month = period[1].trim().toIntOrNull() ?: return@mapNotNull null
                (year to month) to length
            }.toMap()
        }.getOrNull()
        val ok = restored != null
        val entries = restored?.size ?: 0
        HijriWidgetRefreshLog.d("inapp:restore", "entries=$entries ok=$ok")
        if (restored != null) {
            HijriMonthOverrides.replaceAll(restored)
        }
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
        val saved = prefs.getString(KEY_DATE_DISPLAY_MODE, null)
            ?: savedStateHandle.get<String>(KEY_DATE_DISPLAY_MODE)
        return saved?.let { runCatching { DateDisplayMode.valueOf(it) }.getOrNull() }
            ?: DEFAULT_DATE_DISPLAY_MODE
    }

    private fun restoreAdjustmentDays(): Int {
        if (prefs.contains(KEY_ADJUSTMENT_DAYS)) return prefs.getInt(KEY_ADJUSTMENT_DAYS, DEFAULT_ADJUSTMENT_DAYS)
        return savedStateHandle.get<Int>(KEY_ADJUSTMENT_DAYS) ?: DEFAULT_ADJUSTMENT_DAYS
    }

    private fun restorePakistanDates(): Boolean {
        if (prefs.contains(KEY_PAKISTAN_DATES)) return prefs.getBoolean(KEY_PAKISTAN_DATES, DEFAULT_PAKISTAN_DATES)
        return savedStateHandle.get<Boolean>(KEY_PAKISTAN_DATES) ?: DEFAULT_PAKISTAN_DATES
    }

    private fun restorePakistanSelectedDate(): PakistanHijriDate? {
        val year = savedStateHandle.get<Long>(KEY_PAK_SELECTED_YEAR)?.toInt()
        val month = savedStateHandle.get<Long>(KEY_PAK_SELECTED_MONTH)?.toInt()
        val day = savedStateHandle.get<Long>(KEY_PAK_SELECTED_DAY)?.toInt()
        return if (year != null && month != null && day != null) {
            runCatching {
                // `PakistanHijriDate.localDate` is stored, not derived, so the Gregorian day must be
                // resolved here against the table already restored above. Clamp a stale saved day to
                // the month's current length first: `hijriToGregorian` rejects a day the month does
                // not have, and that must fall back to a clamped selection, not drop it.
                val maxDay = PakistanHijriCalendar.lengthOfMonth(year, month)
                val resolvedDay = minOf(day, maxDay)
                PakistanHijriDate(
                    year = year,
                    month = month,
                    day = resolvedDay,
                    localDate = PakistanHijriCalendar.hijriToGregorian(year, month, resolvedDay),
                )
            }.getOrNull()
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
        private const val PREFS_NAME = "hijri_sample_settings"
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
