package com.muazdev.hijricalendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.muazdev.hijricalendar.core.CalendarDay
import com.muazdev.hijricalendar.core.DateDisplayMode

@Composable
fun HijriWeekRow(
    days: List<CalendarDay>,
    onDayClick: (CalendarDay) -> Unit,
    modifier: Modifier = Modifier,
    colors: HijriCalendarColors = HijriCalendarDefaults.colors(),
    useArabicIndicNumerals: Boolean = false,
    dateDisplayMode: DateDisplayMode = DateDisplayMode.HIJRI_ONLY,
    dayContent: (@Composable (CalendarDay) -> Unit)? = null,
) {
    require(days.size == 7) { "HijriWeekRow requires exactly 7 days, got ${days.size}" }

    Row(modifier = modifier) {
        days.forEach { day ->
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                HijriCalendarDayCell(
                    day = day,
                    onClick = { onDayClick(day) },
                    colors = colors,
                    useArabicIndicNumerals = useArabicIndicNumerals,
                    dateDisplayMode = dateDisplayMode,
                    content = dayContent?.let { { it(day) } },
                )
            }
        }
    }
}
