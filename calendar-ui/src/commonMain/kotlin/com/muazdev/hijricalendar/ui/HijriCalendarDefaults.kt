package com.muazdev.hijricalendar.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
public object HijriCalendarDefaults {
    public val TodayBorderWidth: Dp = 2.dp
    public val SingleLineCellSize: Dp = 48.dp
    @Deprecated("Cells use a single fixed size regardless of DateDisplayMode.")
    public val BothModeCellSize: Dp = SingleLineCellSize

    @Composable
    public fun colors(
        selectedDayContainerColor: Color = MaterialTheme.colorScheme.primary,
        selectedDayContentColor: Color = MaterialTheme.colorScheme.onPrimary,
        todayBorderColor: Color = MaterialTheme.colorScheme.primary,
        todayBorderWidth: Dp = TodayBorderWidth,
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
        todayBorderWidth = todayBorderWidth,
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

    public fun labels(labels: HijriCalendarLabels = HijriCalendarLabels()): HijriCalendarLabels = labels
}
