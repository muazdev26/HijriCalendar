package com.muazdev.hijricalendar.shared

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import com.muazdev.hijricalendar.core.DateDisplayMode
import com.muazdev.hijricalendar.core.rememberHijriCalendarState
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth

fun MainViewController() = ComposeUIViewController {
    MaterialTheme {
        var dateDisplayMode by remember { mutableStateOf(DateDisplayMode.HIJRI_ONLY) }
        val state = rememberHijriCalendarState(
            initialMonth = HijrahYearMonth(1447, 9),
            adjustmentDays = -1,
        )
        val jumpTick by rememberDeepLinkJumpTick()
        if (jumpTick > 0) {
            LaunchedEffect(jumpTick) { state.goToToday() }
        }
        CalendarScreen(
            state = state,
            dateDisplayMode = dateDisplayMode,
            onDateDisplayModeChange = { dateDisplayMode = it },
            labels = UrduCalendarLabels,
            showAdjustmentSelector = true,
            showPakistanToggle = true,
        )
    }
}
