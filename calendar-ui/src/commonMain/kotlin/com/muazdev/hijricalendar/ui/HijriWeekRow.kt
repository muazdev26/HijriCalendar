package com.muazdev.hijricalendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.muazdev.hijricalendar.core.CalendarDay
import com.muazdev.hijricalendar.core.CalendarMonth

@Composable
internal fun HijriWeekRow(
    days: List<CalendarDay>,
    onDayClick: (CalendarDay) -> Unit,
    modifier: Modifier = Modifier,
    colors: HijriCalendarColors = HijriCalendarDefaults.colors(),
    useArabicIndicNumerals: Boolean = false,
    dateDisplayMode: DateDisplayMode = DateDisplayMode.HIJRI_ONLY,
    dayCellSize: Dp? = null,
    labels: HijriCalendarLabels = HijriCalendarDefaults.labels(),
    dayContent: (@Composable (CalendarDay) -> Unit)? = null,
    showAdjacentDays: Boolean = true,
) {
    // Not a consumer-reachable crash any more: this composable is internal, so the only caller
    // chunks the grid's 42 cells. The check stays because a silent wrong row would be harder to
    // diagnose than a throw, but it can no longer be triggered from outside the module.
    require(days.size == CalendarMonth.DAYS_IN_WEEK) {
        "HijriWeekRow renders exactly one week; got ${days.size} days"
    }

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
                    dayCellSize = dayCellSize,
                    labels = labels,
                    content = dayContent,
                    visible = showAdjacentDays || day.isCurrentMonth,
                )
            }
        }
    }
}
