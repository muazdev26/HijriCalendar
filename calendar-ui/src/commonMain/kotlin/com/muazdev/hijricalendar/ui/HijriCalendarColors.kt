package com.muazdev.hijricalendar.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class HijriCalendarColors(
    val selectedDayContainerColor: Color,
    val selectedDayContentColor: Color,
    val todayBorderColor: Color,
    val todayBorderWidth: Float,
    val disabledDayContentColor: Color,
    val weekendDayContentColor: Color,
    val dayContentColor: Color,
    val dayBackgroundColor: Color,
    val headerContentColor: Color,
    val navigationIconColor: Color,
    val dayOfWeekLabelColor: Color,
    val outsideMonthDayContentColor: Color,
    val gregorianDayContentColor: Color,
    val gregorianHeaderColor: Color,
)
