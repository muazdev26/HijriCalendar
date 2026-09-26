package com.muazdev.hijricalendar.shared

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.window.ComposeUIViewController
import com.abdulrahman_b.hijrahdatetime.toHijrahDate
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import com.muazdev.hijricalendar.core.DateDisplayMode
import com.muazdev.hijricalendar.core.rememberHijriCalendarState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The iOS sample entry point.
 *
 * Mirrors the Android `MainActivity` + `CalendarViewModel` feature-for-feature: the same
 * adjustment selector, Pakistan toggle and month-length settings are exposed, and the display
 * mode, adjustment, Pakistan flag and month-length overrides are all persisted (through
 * [SamplePreferences], iOS's stand-in for the Android `SavedStateHandle`). Unlike the earlier
 * version it no longer hardcodes a demo month and adjustment — the calendar opens on today with
 * the user's restored settings, exactly like the Android app.
 */
fun MainViewController() = ComposeUIViewController {
    // Global overrides must be seeded before the state is constructed so its observed grid
    // starts from the restored calendar.
    remember { SamplePreferences.restoreMonthLengthOverrides() }

    MaterialTheme {
        var dateDisplayMode by remember { mutableStateOf(SamplePreferences.dateDisplayMode()) }
        val state = rememberHijriCalendarState(
            initialMonth = todayHijriYearMonth(),
            initialSelectedDate = todayHijriDate(),
            adjustmentDays = SamplePreferences.adjustmentDays(),
            pakistanDates = SamplePreferences.pakistanDates(),
        )

        val jumpTick by rememberDeepLinkJumpTick()
        if (jumpTick > 0) {
            LaunchedEffect(jumpTick) { state.goToToday() }
        }

        // Persist the state the Android ViewModel also persists, so relaunching restores it.
        LaunchedEffect(state) {
            snapshotFlow { state.adjustmentDays }
                .distinctUntilChanged()
                .collect { SamplePreferences.setAdjustmentDays(it) }
        }
        LaunchedEffect(state) {
            snapshotFlow { state.pakistanDates }
                .distinctUntilChanged()
                .collect { SamplePreferences.setPakistanDates(it) }
        }

        CalendarScreen(
            state = state,
            dateDisplayMode = dateDisplayMode,
            onDateDisplayModeChange = { mode ->
                dateDisplayMode = mode
                SamplePreferences.setDateDisplayMode(mode)
            },
            onJumpToToday = { state.goToToday() },
            labels = UrduCalendarLabels,
            showAdjustmentSelector = true,
            onAdjustmentDaysChange = { SamplePreferences.setAdjustmentDays(it) },
            showPakistanToggle = true,
            onPakistanDatesChange = { SamplePreferences.setPakistanDates(it) },
            showMonthLengthSettings = true,
            onMonthLengthOverridesChanged = { SamplePreferences.persistMonthLengthOverrides() },
        )
    }
}

/** The Hijri month today falls in, used when no month has been restored. */
internal fun todayHijriYearMonth() =
    todayHijriDate()?.let { HijrahYearMonth(it.year, it.month.number) } ?: HijrahYearMonth(1447, 1)

internal fun todayHijriDate() = runCatching {
    val now = kotlin.time.Clock.System.now()
    now.toLocalDateTime(TimeZone.currentSystemDefault()).date.toHijrahDate()
}.getOrNull()
