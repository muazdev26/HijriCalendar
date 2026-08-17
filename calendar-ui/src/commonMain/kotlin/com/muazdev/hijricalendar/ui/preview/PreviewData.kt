package com.muazdev.hijricalendar.ui.preview

import com.muazdev.hijricalendar.core.CalendarDay
import com.abdulrahman_b.hijrahdatetime.HijrahDate

internal fun sampleDay(
    dayOfMonth: Int = 15,
    isCurrentMonth: Boolean = true,
    isToday: Boolean = false,
    isSelected: Boolean = false,
    isDisabled: Boolean = false,
    isWeekend: Boolean = false,
): CalendarDay = CalendarDay(
    hijrahDate = HijrahDate(1447, 9, dayOfMonth),
    isCurrentMonth = isCurrentMonth,
    isToday = isToday,
    isSelected = isSelected,
    isDisabled = isDisabled,
    isWeekend = isWeekend,
)
