package com.muazdev.hijricalendar.shared

import com.muazdev.hijricalendar.core.UrduCalendarNames
import com.muazdev.hijricalendar.ui.HijriCalendarLabels

val UrduCalendarLabels = HijriCalendarLabels(
    hijriMonthName = { _, month -> UrduCalendarNames.hijriMonths[month - 1] },
    gregorianMonthName = { month -> UrduCalendarNames.gregorianMonths[month - 1] },
    weekdayShortName = { weekDay -> UrduCalendarNames.weekdays.getValue(weekDay) },
    previousMonthContentDescription = "پچھلا مہینہ",
    nextMonthContentDescription = "اگلا مہینہ",
)