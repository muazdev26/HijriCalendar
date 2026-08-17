package com.muazdev.hijricalendar.shared

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import com.muazdev.hijricalendar.core.DateDisplayMode

fun MainViewController() = ComposeUIViewController {
    MaterialTheme {
        var dateDisplayMode by remember { mutableStateOf(DateDisplayMode.HIJRI_ONLY) }
        CalendarScreen(
            dateDisplayMode = dateDisplayMode,
            onDateDisplayModeChange = { dateDisplayMode = it },
        )
    }
}
