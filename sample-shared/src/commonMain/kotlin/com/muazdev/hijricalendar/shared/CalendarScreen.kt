package com.muazdev.hijricalendar.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muazdev.hijricalendar.core.DateDisplayMode
import com.muazdev.hijricalendar.core.HijriCalendarState
import com.muazdev.hijricalendar.core.rememberHijriCalendarState
import com.muazdev.hijricalendar.ui.HijriCalendar
import com.muazdev.hijricalendar.ui.defaultOnDayClick
import com.abdulrahman_b.hijrahdatetime.toLocalDate
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth

@Composable
fun CalendarScreen(
    modifier: Modifier = Modifier,
    state: HijriCalendarState = rememberHijriCalendarState(
        initialMonth = HijrahYearMonth(1447, 9),
    ),
    dateDisplayMode: DateDisplayMode = DateDisplayMode.HIJRI_ONLY,
    onDateDisplayModeChange: (DateDisplayMode) -> Unit = {},
    onJumpToToday: (() -> Unit)? = null,
) {
    val selectedDate = state.selectedDate

    val selectedDateText = remember(selectedDate, dateDisplayMode) {
        selectedDate?.let {
            val hijriText = "${it.day} ${it.month.name} ${it.year}"
            val gregorianDate = it.toLocalDate()
            val gregorianText = "${gregorianDate.day} ${gregorianDate.month.name} ${gregorianDate.year}"
            when (dateDisplayMode) {
                DateDisplayMode.HIJRI_ONLY -> hijriText
                DateDisplayMode.GREGORIAN_ONLY -> gregorianText
                DateDisplayMode.BOTH -> "$hijriText\n$gregorianText"
            }
        }
    }

    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HijriCalendar(
            state = state,
            onDayClick = state.defaultOnDayClick(),
            dateDisplayMode = dateDisplayMode,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        DateDisplayModeSelector(
            selectedMode = dateDisplayMode,
            onModeSelected = onDateDisplayModeChange,
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedDateText != null) {
            Text(
                text = selectedDateText,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Text(
                text = "No date selected",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (onJumpToToday != null) {
            Spacer(modifier = Modifier.height(16.dp))
            androidx.compose.material3.Button(onClick = onJumpToToday) {
                Text("Jump to Today")
            }
        }
    }
}

@Composable
private fun DateDisplayModeSelector(
    selectedMode: DateDisplayMode,
    onModeSelected: (DateDisplayMode) -> Unit,
) {
    val modes = DateDisplayMode.entries
    val labels = listOf("Hijri", "Gregorian", "Both")

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, mode ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = modes.size,
                ),
                onClick = { onModeSelected(mode) },
                selected = selectedMode == mode,
                label = { Text(labels[index]) },
            )
        }
    }
}
