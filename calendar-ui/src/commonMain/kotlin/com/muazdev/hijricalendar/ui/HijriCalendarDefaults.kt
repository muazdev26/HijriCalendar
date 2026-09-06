package com.muazdev.hijricalendar.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
object HijriCalendarDefaults {
    val TodayBorderWidth = 2.dp
    val SingleLineCellSize = 48.dp
    @Deprecated("Cells use a single fixed size regardless of DateDisplayMode.")
    val BothModeCellSize = SingleLineCellSize

    @Composable
    fun colors(
        selectedDayContainerColor: Color = MaterialTheme.colorScheme.primary,
        selectedDayContentColor: Color = MaterialTheme.colorScheme.onPrimary,
        todayBorderColor: Color = MaterialTheme.colorScheme.primary,
        disabledDayContentColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        weekendDayContentColor: Color = MaterialTheme.colorScheme.error,
        dayContentColor: Color = MaterialTheme.colorScheme.onSurface,
        dayBackgroundColor: Color = Color.Transparent,
        headerContentColor: Color = MaterialTheme.colorScheme.onSurface,
        navigationIconColor: Color = MaterialTheme.colorScheme.onSurface,
        dayOfWeekLabelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        outsideMonthDayContentColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        gregorianDayContentColor: Color = dayContentColor.copy(alpha = 0.6f),
        gregorianHeaderColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    ): HijriCalendarColors = HijriCalendarColors(
        selectedDayContainerColor = selectedDayContainerColor,
        selectedDayContentColor = selectedDayContentColor,
        todayBorderColor = todayBorderColor,
        todayBorderWidth = TodayBorderWidth.value,
        disabledDayContentColor = disabledDayContentColor,
        weekendDayContentColor = weekendDayContentColor,
        dayContentColor = dayContentColor,
        dayBackgroundColor = dayBackgroundColor,
        headerContentColor = headerContentColor,
        navigationIconColor = navigationIconColor,
        dayOfWeekLabelColor = dayOfWeekLabelColor,
        outsideMonthDayContentColor = outsideMonthDayContentColor,
        gregorianDayContentColor = gregorianDayContentColor,
        gregorianHeaderColor = gregorianHeaderColor,
    )

    fun labels(labels: HijriCalendarLabels = HijriCalendarLabels()): HijriCalendarLabels = labels
}
