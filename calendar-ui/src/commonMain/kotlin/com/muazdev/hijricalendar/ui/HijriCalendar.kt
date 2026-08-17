package com.muazdev.hijricalendar.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.muazdev.hijricalendar.core.CalendarDay
import com.muazdev.hijricalendar.core.DateDisplayMode
import com.muazdev.hijricalendar.core.HijriCalendarState

@Composable
fun HijriCalendar(
    state: HijriCalendarState,
    modifier: Modifier = Modifier,
    colors: HijriCalendarColors = HijriCalendarDefaults.colors(),
    useArabicIndicNumerals: Boolean = false,
    dateDisplayMode: DateDisplayMode = DateDisplayMode.HIJRI_ONLY,
    onDayClick: (CalendarDay) -> Unit,
    dayContent: (@Composable (CalendarDay) -> Unit)? = null,
) {
    val calendarMonth = state.calendarMonth

    val headerContentDescription = remember(calendarMonth) {
        "${calendarMonth.monthName} ${calendarMonth.year}"
    }

    val gregorianMonthText = remember(calendarMonth) {
        calendarMonth.gregorianMonthRange
    }

    Column(modifier = modifier) {
        HijriCalendarHeader(
            monthName = calendarMonth.monthName,
            year = calendarMonth.year,
            onPreviousMonth = state::goToPreviousMonth,
            onNextMonth = state::goToNextMonth,
            colors = colors,
            dateDisplayMode = dateDisplayMode,
            gregorianMonthText = gregorianMonthText,
            contentDescription = headerContentDescription,
        )

        HijriCalendarGrid(
            state = state,
            calendarMonth = calendarMonth,
            onDayClick = onDayClick,
            colors = colors,
            useArabicIndicNumerals = useArabicIndicNumerals,
            dateDisplayMode = dateDisplayMode,
            dayContent = dayContent,
        )
    }
}

fun HijriCalendarState.defaultOnDayClick(): (CalendarDay) -> Unit = { day ->
    selectDate(day.hijrahDate)
}
