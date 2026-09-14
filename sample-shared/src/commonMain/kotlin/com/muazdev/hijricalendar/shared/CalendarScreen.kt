package com.muazdev.hijricalendar.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.muazdev.hijricalendar.ui.HijriCalendarLabels
import com.muazdev.hijricalendar.ui.defaultOnDayClick
import com.abdulrahman_b.hijrahdatetime.HijrahMonth
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
    labels: HijriCalendarLabels? = null,
    showAdjustmentSelector: Boolean = false,
    onAdjustmentDaysChange: ((Int) -> Unit)? = null,
    showPakistanToggle: Boolean = false,
    onPakistanDatesChange: ((Boolean) -> Unit)? = null,
) {
    val selectedDate = state.selectedDate
    val pakistanDate = state.selectedPakistanDate

    val selectedDateText = remember(selectedDate, pakistanDate, dateDisplayMode) {
        when {
            pakistanDate != null -> {
                val hijriText = "${pakistanDate.day} ${HijrahMonth.entries[pakistanDate.month - 1].name} ${pakistanDate.year}"
                val gregorianDate = pakistanDate.localDate
                val gregorianText = "${gregorianDate.day} ${gregorianDate.month.name} ${gregorianDate.year}"
                when (dateDisplayMode) {
                    DateDisplayMode.HIJRI_ONLY -> hijriText
                    DateDisplayMode.GREGORIAN_ONLY -> gregorianText
                    DateDisplayMode.BOTH -> "$hijriText\n$gregorianText"
                }
            }
            selectedDate != null -> {
                val hijriText = "${selectedDate.day} ${selectedDate.month.name} ${selectedDate.year}"
                val gregorianDate = selectedDate.toLocalDate()
                val gregorianText = "${gregorianDate.day} ${gregorianDate.month.name} ${gregorianDate.year}"
                when (dateDisplayMode) {
                    DateDisplayMode.HIJRI_ONLY -> hijriText
                    DateDisplayMode.GREGORIAN_ONLY -> gregorianText
                    DateDisplayMode.BOTH -> "$hijriText\n$gregorianText"
                }
            }
            else -> null
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
            labels = labels ?: HijriCalendarLabels(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        DateDisplayModeSelector(
            selectedMode = dateDisplayMode,
            onModeSelected = onDateDisplayModeChange,
        )

        if (showAdjustmentSelector) {
            Spacer(modifier = Modifier.height(16.dp))
            AdjustmentSelector(
                current = state.adjustmentDays,
                onSelect = { value ->
                    state.setAdjustmentDays(value)
                    onAdjustmentDaysChange?.invoke(value)
                },
            )
        }

        if (showPakistanToggle) {
            Spacer(modifier = Modifier.height(16.dp))
            PakistanModeSelector(
                pakistan = state.pakistanDates,
                onSelect = { enabled ->
                    state.setPakistanDates(enabled)
                    onPakistanDatesChange?.invoke(enabled)
                },
            )
        }

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

@Composable
private fun PakistanModeSelector(
    pakistan: Boolean,
    onSelect: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Hijri calendar source",
            style = MaterialTheme.typography.titleSmall,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf("Calculation" to false, "Pakistan" to true).forEach { (label, value) ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(
                        index = if (value) 1 else 0,
                        count = 2,
                    ),
                    onClick = { onSelect(value) },
                    selected = pakistan == value,
                    label = { Text(label) },
                )
            }
        }
        Text(
            text = if (pakistan) {
                "Pakistan (Ruet-e-Hilal moon sighting)"
            } else {
                "Umm al-Qura (Saudi calculation)"
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun AdjustmentSelector(
    current: Int,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Moon-sighting adjustment",
            style = MaterialTheme.typography.titleSmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (-2..2).forEach { value ->
                if (value == current) {
                    Button(onClick = { onSelect(value) }) {
                        Text(if (value > 0) "+$value" else value.toString())
                    }
                } else {
                    OutlinedButton(onClick = { onSelect(value) }) {
                        Text(if (value > 0) "+$value" else value.toString())
                    }
                }
            }
        }
        Text(
            text = when (current) {
                0 -> "Umm al-Qura (Saudi calculation)"
                else -> if (current > 0) {
                    "Show Hijri $current day(s) ahead"
                } else {
                    "Show Hijri ${-current} day(s) behind"
                }
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
